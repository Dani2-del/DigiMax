const APPLY_MIGRATION = false;
const ACCESS_DATABASE = "digitrans_acceso";
const TRANSPORT_DATABASE = "digitrans_transporte";

const accessUsers = db.getSiblingDB(ACCESS_DATABASE).getCollection("usuarios");
const transportDatabase = db.getSiblingDB(TRANSPORT_DATABASE);
const legacyUsers = transportDatabase.getCollection("usuarios");
const profiles = transportDatabase.getCollection("perfiles_transporte");
const transactions = transportDatabase.getCollection("transacciones");
const schedules = transportDatabase.getCollection("horarios_conductor");
const messages = transportDatabase.getCollection("mensajes_chat");
const notices = transportDatabase.getCollection("avisos_administrativos");

function escapeRegex(value) {
    return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function normalizedEmail(email) {
    return email.trim().toLowerCase();
}

function transportData(user) {
    const data = {};
    [
        "sistema_asignado",
        "sitm",
        "tarjetas",
        "rutas_favoritas",
        "preferencias_notificacion_rutas",
        "bus_asignado"
    ].forEach(function (field) {
        if (Object.prototype.hasOwnProperty.call(user, field)) {
            data[field] = user[field];
        }
    });
    return data;
}

const accessUsersByEmail = new Map();
accessUsers.find({}, { _id: 1, correo: 1, nombre: 1 }).forEach(function (user) {
    if (typeof user.correo !== "string" || !user.correo.trim()) {
        return;
    }
    const email = normalizedEmail(user.correo);
    if (!accessUsersByEmail.has(email)) {
        accessUsersByEmail.set(email, []);
    }
    accessUsersByEmail.get(email).push(user);
});

const usersByIdentity = new Map();
let unmatchedLegacyUsers = 0;
let ambiguousAccessIdentities = 0;

legacyUsers.find({}, {
    _id: 1,
    correo: 1,
    sistema_asignado: 1,
    sitm: 1,
    tarjetas: 1,
    rutas_favoritas: 1,
    preferencias_notificacion_rutas: 1,
    bus_asignado: 1
}).forEach(function (legacyUser) {
    if (typeof legacyUser.correo !== "string" || !legacyUser.correo.trim()) {
        unmatchedLegacyUsers++;
        return;
    }

    const identities = accessUsersByEmail.get(normalizedEmail(legacyUser.correo)) || [];
    if (identities.length !== 1) {
        if (identities.length > 1) {
            ambiguousAccessIdentities++;
        } else {
            unmatchedLegacyUsers++;
        }
        return;
    }

    const identity = identities[0];
    const identityId = identity._id.toString();
    if (!usersByIdentity.has(identityId)) {
        usersByIdentity.set(identityId, []);
    }
    usersByIdentity.get(identityId).push(legacyUser);
});

const mapping = [];
let duplicateLegacyProfiles = 0;
let profilesAlreadyPresent = 0;
let profilesReady = 0;
let profileIdConflicts = 0;

usersByIdentity.forEach(function (users, identityId) {
    if (users.length !== 1) {
        duplicateLegacyProfiles += users.length;
        return;
    }

    const legacyUser = users[0];
    const identity = (accessUsersByEmail.get(normalizedEmail(legacyUser.correo)) || [])[0];
    const entry = {
        legacyId: legacyUser._id,
        legacyIdString: legacyUser._id.toString(),
        identityId: identityId,
        identityBsonId: identity._id,
        identity: identity,
        legacyUser: legacyUser
    };
    mapping.push(entry);

    const existingProfile = profiles.findOne({ usuarioId: identityId }, { _id: 1 });
    if (existingProfile) {
        profilesAlreadyPresent++;
        if (existingProfile._id.toString() !== identityId) {
            profileIdConflicts++;
        }
    } else {
        profilesReady++;
    }
});

function countReferences(entry) {
    return {
        transactions: transactions.countDocuments({
            usuario: DBRef("usuarios", entry.legacyId)
        }),
        schedules: schedules.countDocuments({ conductor_id: entry.legacyIdString }),
        chatMessages: messages.countDocuments({ authorId: entry.legacyIdString }),
        noticeRecipients: notices.countDocuments({ destinatarios: entry.legacyIdString }),
        dismissedNotices: notices.countDocuments({ descartada_por: entry.legacyIdString })
    };
}

function remapReferences(entry) {
    const newId = entry.identityId;
    const oldId = entry.legacyIdString;
    const transactionResult = transactions.updateMany(
        { usuario: DBRef("usuarios", entry.legacyId) },
        {
            $set: {
                usuario_id: newId,
                usuario_correo: entry.identity.correo,
                usuario_nombre: entry.identity.nombre
            },
            $unset: { usuario: "" }
        });
    const scheduleResult = schedules.updateMany(
        { conductor_id: oldId },
        { $set: { conductor_id: newId } });
    const chatResult = messages.updateMany(
        { authorId: oldId },
        { $set: { authorId: newId } });

    let recipientReferences = 0;
    let dismissedReferences = 0;
    notices.find({
        $or: [
            { destinatarios: oldId },
            { descartada_por: oldId }
        ]
    }, { _id: 1, destinatarios: 1, descartada_por: 1 }).forEach(function (notice) {
        const recipients = notice.destinatarios || [];
        const dismissedBy = notice.descartada_por || [];
        const updatedRecipients = recipients.map(function (id) {
            if (id === oldId) {
                recipientReferences++;
                return newId;
            }
            return id;
        });
        const updatedDismissedBy = dismissedBy.map(function (id) {
            if (id === oldId) {
                dismissedReferences++;
                return newId;
            }
            return id;
        });
        notices.updateOne({ _id: notice._id }, {
            $set: {
                destinatarios: updatedRecipients,
                descartada_por: updatedDismissedBy
            }
        });
    });

    return {
        transactions: transactionResult.modifiedCount,
        schedules: scheduleResult.modifiedCount,
        chatMessages: chatResult.modifiedCount,
        noticeRecipients: recipientReferences,
        dismissedNotices: dismissedReferences
    };
}

if (APPLY_MIGRATION
        && (unmatchedLegacyUsers > 0
            || ambiguousAccessIdentities > 0
            || duplicateLegacyProfiles > 0
            || profileIdConflicts > 0)) {
    throw new Error(
        "Migration aborted: resolve unmatched, ambiguous, duplicate, and profile-ID-conflict mappings first.");
}

let profilesInserted = 0;
const referenceCounts = {
    transactions: 0,
    schedules: 0,
    chatMessages: 0,
    noticeRecipients: 0,
    dismissedNotices: 0
};
const referenceUpdates = Object.assign({}, referenceCounts);

if (APPLY_MIGRATION) {
    profiles.createIndex({ usuarioId: 1 }, { unique: true, name: "uk_perfiles_transporte_usuarioId" });
}

mapping.forEach(function (entry) {
    const existingProfile = profiles.findOne({ usuarioId: entry.identityId }, { _id: 1 });
    if (!existingProfile && APPLY_MIGRATION) {
        profiles.insertOne(Object.assign({
            _id: entry.identityBsonId,
            usuarioId: entry.identityId
        }, transportData(entry.legacyUser)));
        profilesInserted++;
    }

    const counts = countReferences(entry);
    Object.keys(referenceCounts).forEach(function (key) {
        referenceCounts[key] += counts[key];
    });

    if (APPLY_MIGRATION) {
        const updates = remapReferences(entry);
        Object.keys(referenceUpdates).forEach(function (key) {
            referenceUpdates[key] += updates[key];
        });
    }
});

printjson({
    mode: APPLY_MIGRATION ? "apply" : "dry-run",
    legacyUsers: legacyUsers.countDocuments({}),
    identitiesMatched: mapping.length,
    profilesReady: profilesReady,
    profilesAlreadyPresent: profilesAlreadyPresent,
    profileIdConflicts: profileIdConflicts,
    profilesInserted: profilesInserted,
    unmatchedLegacyUsers: unmatchedLegacyUsers,
    ambiguousAccessIdentities: ambiguousAccessIdentities,
    duplicateLegacyProfiles: duplicateLegacyProfiles,
    referenceCounts: referenceCounts,
    referenceUpdates: referenceUpdates
});
