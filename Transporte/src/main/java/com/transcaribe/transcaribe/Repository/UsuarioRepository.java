package com.transcaribe.transcaribe.Repository;

import java.util.List;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import com.transcaribe.transcaribe.Model.PerfilTransporte;
import com.transcaribe.transcaribe.Model.SistemaTransporte;
import com.transcaribe.transcaribe.Model.Usuario;
import com.transcaribe.transcaribe.service.AccessIdentityClient;
import com.transcaribe.transcaribe.service.AccessUser;
import com.transcaribe.transcaribe.service.AccessIdentityClient.TransportIdentity;

@Repository
public class UsuarioRepository {

    private static final int PROFILE_BATCH_SIZE = 1_000;
    /** Tiempo que se reutiliza el listado de un servicio antes de volver a consultar Acceso. */
    private static final long CACHE_TTL_MILLIS = 60_000L;

    private record ListadoEnCache(long expiraEn, List<Usuario> usuarios) {
    }

    private final Map<SistemaTransporte, ListadoEnCache> cacheUsuariosPorSistema = new ConcurrentHashMap<>();
    private final Map<SistemaTransporte, ListadoEnCache> cacheConductoresPorSistema = new ConcurrentHashMap<>();

    private final PerfilTransporteRepository perfiles;
    private final MongoOperations mongoTemplate;
    private final AccessIdentityClient acceso;

    public UsuarioRepository(
            PerfilTransporteRepository perfiles,
            MongoOperations mongoTemplate,
            AccessIdentityClient acceso) {
        this.perfiles = perfiles;
        this.mongoTemplate = mongoTemplate;
        this.acceso = acceso;
    }

    public Optional<Usuario> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        AccessUser identity = acceso.findById(id);
        return Optional.ofNullable(identity).map(this::toUsuario);
    }

    public Optional<Usuario> findByIdAndActivoTrue(String id) {
        return findById(id).filter(Usuario::isActivo);
    }

    public Optional<Usuario> findByCorreo(String correo) {
        return findByCorreoIgnoreCase(correo);
    }

    public Optional<Usuario> findByCorreoIgnoreCase(String correo) {
        if (correo == null || correo.isBlank()) {
            return Optional.empty();
        }
        AccessUser identity = acceso.findByEmail(correo);
        return Optional.ofNullable(identity).map(this::toUsuario);
    }

    public Usuario fromAccessIdentity(AccessUser identity) {
        return toUsuario(identity);
    }

    public List<Usuario> findAll() {
        return toUsuarios(acceso.findAll());
    }

    public List<Usuario> findByNombreContainingIgnoreCaseOrCorreoContainingIgnoreCase(
            String nombre, String correo) {
        String search = nombre == null || nombre.isBlank() ? correo : nombre;
        return acceso.search(search == null ? "" : search).stream().map(this::toUsuario).toList();
    }

    public List<Usuario> findByRoleAndActivoTrue(String role) {
        return findAll().stream()
                .filter(Usuario::isActivo)
                .filter(usuario -> role.equals(usuario.getRole()))
                .toList();
    }

    public List<Usuario> findActiveConductoresBySistemaTransporte(
            SistemaTransporte sistema, List<String> conductorIds) {
        if (sistema == null || conductorIds == null || conductorIds.isEmpty()) {
            return List.of();
        }
        List<AccessUser> conductores = acceso.findTransportUsers(conductorIds).stream()
                .filter(AccessUser::active)
                .filter(identity -> Usuario.ROLE_CONDUCTOR.equals(identity.role()))
                .toList();
        if (conductores.stream().anyMatch(identity -> identity.id() == null || identity.id().isBlank())) {
            throw new IllegalStateException("Acceso devolvió una identidad incompleta.");
        }
        List<TransportIdentity> identidadesTransporte = conductores.stream()
                .map(identity -> new TransportIdentity(
                        identity.id(), identity.email(), identity.role(), identity.serviceAssignment()))
                .toList();
        Map<String, PerfilTransporte> profilesByUserId = findProfilesForUsers(identidadesTransporte);
        List<String> idsDelSistema = identidadesTransporte.stream()
                .filter(identity -> belongsToSystem(identity, profilesByUserId.get(identity.id()), sistema))
                .map(TransportIdentity::id)
                .toList();
        return toUsuarios(conductores.stream()
                .filter(identity -> idsDelSistema.contains(identity.id()))
                .toList());
    }

    public Stream<Usuario> streamAll() {
        return findAll().stream();
    }

    public Stream<Usuario> streamBySistemaTransporte(SistemaTransporte sistema) {
        return findBySistemaTransporte(sistema).stream();
    }

    public List<Usuario> findBySistemaTransporte(SistemaTransporte sistema) {
        if (sistema == null) {
            return findAll();
        }
        ListadoEnCache guardado = cacheUsuariosPorSistema.get(sistema);
        if (guardado != null && guardado.expiraEn() > System.currentTimeMillis()) {
            return guardado.usuarios();
        }
        List<Usuario> usuarios = List.copyOf(cargarBySistemaTransporte(sistema));
        cacheUsuariosPorSistema.put(sistema,
                new ListadoEnCache(System.currentTimeMillis() + CACHE_TTL_MILLIS, usuarios));
        return usuarios;
    }

    /**
     * Conductores activos de un servicio. Consulta solo a los conductores del directorio,
     * en lugar de cargar todos los usuarios de Acceso como hacía findByRoleAndActivoTrue.
     */
    public List<Usuario> findActiveConductoresDelSistema(SistemaTransporte sistema) {
        if (sistema == null) {
            return List.of();
        }
        ListadoEnCache guardado = cacheConductoresPorSistema.get(sistema);
        if (guardado != null && guardado.expiraEn() > System.currentTimeMillis()) {
            return guardado.usuarios();
        }
        List<String> conductorIds = acceso.findTransportDirectory().stream()
                .filter(identity -> Usuario.ROLE_CONDUCTOR.equals(identity.role()))
                .map(TransportIdentity::id)
                .filter(id -> id != null && !id.isBlank())
                .toList();
        List<Usuario> conductores = List.copyOf(
                findActiveConductoresBySistemaTransporte(sistema, conductorIds));
        cacheConductoresPorSistema.put(sistema,
                new ListadoEnCache(System.currentTimeMillis() + CACHE_TTL_MILLIS, conductores));
        return conductores;
    }

    private void invalidarCaches() {
        cacheUsuariosPorSistema.clear();
        cacheConductoresPorSistema.clear();
    }

    private List<Usuario> cargarBySistemaTransporte(SistemaTransporte sistema) {
        List<TransportIdentity> identities = acceso.findTransportDirectory();
        if (identities.isEmpty()) {
            return List.of();
        }
        if (identities.stream().anyMatch(identity -> identity.id() == null || identity.id().isBlank())) {
            throw new IllegalStateException("Acceso devolvió una identidad incompleta.");
        }

        List<TransportIdentity> usersWithProfileMembership = identities.stream()
                .filter(identity -> !isAssignedTransportModerator(identity))
                .toList();
        Map<String, PerfilTransporte> profilesByUserId =
                findProfilesForUsers(usersWithProfileMembership);
        List<String> userIds = identities.stream()
                .filter(identity -> belongsToSystem(identity, profilesByUserId.get(identity.id()), sistema))
                .map(TransportIdentity::id)
                .toList();
        return userIds.isEmpty() ? List.of() : toUsuarios(acceso.findTransportUsers(userIds));
    }

    public Map<String, Long> countRolesBySistemaTransporte(SistemaTransporte sistema) {
        if (sistema == null) {
            throw new IllegalArgumentException("El sistema de transporte es obligatorio.");
        }
        List<TransportIdentity> identities = acceso.findTransportDirectory();
        if (identities.isEmpty()) {
            return Map.of();
        }
        if (identities.stream().anyMatch(identity -> identity.id() == null || identity.id().isBlank())) {
            throw new IllegalStateException("Acceso devolvió una identidad incompleta.");
        }

        List<TransportIdentity> usersWithProfileMembership = identities.stream()
                .filter(identity -> !isAssignedTransportModerator(identity))
                .toList();
        Map<String, PerfilTransporte> profilesByUserId =
                findProfilesForUsers(usersWithProfileMembership);
        Map<String, Long> countsByRole = new java.util.LinkedHashMap<>();
        identities.stream()
                .filter(identity -> belongsToSystem(
                        identity, profilesByUserId.get(identity.id()), sistema))
                .forEach(identity -> {
                    String role = Usuario.ROLE_MODERATOR.equals(identity.role())
                            && !identity.isTransportModerator()
                                    ? Usuario.ROLE_USER : identity.role();
                    countsByRole.merge(role, 1L, Long::sum);
                });
        return java.util.Collections.unmodifiableMap(countsByRole);
    }

    public long countTransportUsers(SistemaTransporte sistema) {
        return countRolesBySistemaTransporte(sistema).values().stream()
                .mapToLong(Long::longValue)
                .sum();
    }

    public List<Usuario> findByRutaFavoritaIgnoreCase(String rutaRegex) {
        Pattern pattern = Pattern.compile(rutaRegex, Pattern.CASE_INSENSITIVE);
        List<PerfilTransporte> matchingProfiles = perfiles.findByRutaFavoritaIgnoreCase(rutaRegex);
        if (matchingProfiles.isEmpty()) {
            return List.of();
        }
        List<String> ids = matchingProfiles.stream()
                .map(PerfilTransporte::getUsuarioId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        return acceso.findTransportUsers(ids).stream()
                .map(this::toUsuario)
                .filter(Usuario::isActivo)
                .filter(Usuario::isVerificado)
                .filter(usuario -> usuario.getRutasFavoritas().stream()
                        .anyMatch(ruta -> pattern.matcher(ruta).find()))
                .toList();
    }

    public Optional<Usuario> findByNumeroTarjetaEnLista(String numeroTarjeta) {
        Optional<PerfilTransporte> profile = perfiles.findByNumeroTarjetaEnLista(numeroTarjeta);
        if (profile.isPresent()) {
            return Optional.of(profileReference(profile.get()));
        }
        return legacyProfileByCard(numeroTarjeta, null);
    }

    public Optional<Usuario> findByNumeroTarjetaEnListaYSistema(
            String numeroTarjeta, SistemaTransporte sistemaTransporte) {
        Optional<PerfilTransporte> profile =
                perfiles.findByNumeroTarjetaEnListaYSistema(numeroTarjeta, sistemaTransporte);
        if (profile.isPresent()) {
            return Optional.of(profileReference(profile.get()));
        }
        return legacyProfileByCard(numeroTarjeta, sistemaTransporte);
    }

    public Usuario save(Usuario usuario) {
        if (usuario == null || usuario.getId() == null || usuario.getId().isBlank()) {
            throw new IllegalArgumentException("El usuario central debe tener un ID para guardar su perfil.");
        }
        PerfilTransporte perfil = perfiles.findByUsuarioId(usuario.getId())
                .orElseGet(() -> profileFromLegacy(usuario).orElseGet(PerfilTransporte::new));
        copyProfile(usuario, perfil);
        perfil.setId(usuario.getId());
        perfil.setUsuarioId(usuario.getId());
        perfiles.save(perfil);
        invalidarCaches();
        return usuario;
    }

    public List<Usuario> saveAll(Iterable<Usuario> usuarios) {
        java.util.ArrayList<Usuario> saved = new java.util.ArrayList<>();
        for (Usuario usuario : usuarios) {
            saved.add(save(usuario));
        }
        return saved;
    }

    public void delete(Usuario usuario) {
        if (usuario == null) {
            return;
        }
        acceso.updateUser(usuario.getId(), usuario.getNombre(), usuario.getCorreo(), usuario.getRole(), false, null);
        invalidarCaches();
    }

    private Usuario toUsuario(AccessUser identity) {
        if (identity == null || identity.id() == null) {
            throw new IllegalStateException("Acceso devolvió una identidad incompleta.");
        }
        Optional<PerfilTransporte> storedProfile = perfiles.findByUsuarioId(identity.id());
        PerfilTransporte profile = storedProfile.orElseGet(() ->
                profileFromLegacy(identity).orElseGet(() -> {
                    PerfilTransporte empty = new PerfilTransporte();
                    empty.setId(identity.id());
                    empty.setUsuarioId(identity.id());
                    return empty;
                }));
        if (storedProfile.isPresent()
                && (profile.getId() == null || !identity.id().equals(profile.getId()))) {
            profile.setId(identity.id());
            profile.setUsuarioId(identity.id());
            profile = perfiles.save(profile);
        } else if (storedProfile.isPresent() && profile.getUsuarioId() == null) {
            profile.setUsuarioId(identity.id());
            profile = perfiles.save(profile);
        }
        return toUsuario(identity, profile);
    }

    private List<Usuario> toUsuarios(List<AccessUser> identities) {
        List<Usuario> users = new java.util.ArrayList<>(identities.size());
        for (int start = 0; start < identities.size(); start += PROFILE_BATCH_SIZE) {
            List<AccessUser> batch = identities.subList(
                    start, Math.min(start + PROFILE_BATCH_SIZE, identities.size()));
            List<String> userIds = batch.stream()
                    .map(AccessUser::id)
                    .filter(id -> id != null && !id.isBlank())
                    .distinct()
                    .toList();
            Map<String, PerfilTransporte> profilesByUserId = new HashMap<>();
            if (!userIds.isEmpty()) {
                perfiles.findByUsuarioIdIn(userIds).forEach(profile ->
                        profilesByUserId.put(profile.getUsuarioId(), profile));
            }

            List<AccessUser> usersWithoutProfile = batch.stream()
                    .filter(identity -> identity != null && identity.id() != null)
                    .filter(identity -> !profilesByUserId.containsKey(identity.id()))
                    .filter(identity -> identity.email() != null && !identity.email().isBlank())
                    .toList();
            if (!usersWithoutProfile.isEmpty()) {
                List<Criteria> legacyCriteria = usersWithoutProfile.stream()
                        .map(identity -> Criteria.where("correo").regex(
                                "^" + Pattern.quote(identity.email().trim()) + "$", "i"))
                        .toList();
                Query legacyQuery = Query.query(
                        new Criteria().orOperator(legacyCriteria.toArray(Criteria[]::new)));
                legacyQuery.fields()
                        .include("correo")
                        .include("sitm")
                        .include("sistema_asignado")
                        .include("tarjetas")
                        .include("rutas_favoritas")
                        .include("preferencias_notificacion_rutas")
                        .include("bus_asignado");
                Map<String, AccessUser> usersByEmail = new HashMap<>();
                usersWithoutProfile.forEach(identity ->
                        usersByEmail.put(identity.email().trim().toLowerCase(Locale.ROOT), identity));
                mongoTemplate.find(legacyQuery, Document.class, "usuarios").forEach(legacy -> {
                    Object emailValue = legacy.get("correo");
                    AccessUser identity = emailValue instanceof String email
                            ? usersByEmail.get(email.toLowerCase(Locale.ROOT))
                            : null;
                    if (identity != null) {
                        PerfilTransporte profile = mongoTemplate.getConverter()
                                .read(PerfilTransporte.class, legacy);
                        profile.setId(identity.id());
                        profile.setUsuarioId(identity.id());
                        profilesByUserId.put(identity.id(), profile);
                    }
                });
            }

            for (AccessUser identity : batch) {
                if (identity == null || identity.id() == null) {
                    throw new IllegalStateException("Acceso devolvió una identidad incompleta.");
                }
                PerfilTransporte profile = profilesByUserId.get(identity.id());
                if (profile == null) {
                    profile = new PerfilTransporte();
                    profile.setId(identity.id());
                    profile.setUsuarioId(identity.id());
                } else if (!identity.id().equals(profile.getId()) || profile.getUsuarioId() == null) {
                    profile.setId(identity.id());
                    profile.setUsuarioId(identity.id());
                    profile = perfiles.save(profile);
                }
                users.add(toUsuario(identity, profile));
            }
        }
        return users;
    }

    private Map<String, PerfilTransporte> findProfilesForUsers(List<TransportIdentity> identities) {
        if (identities.isEmpty()) {
            return Map.of();
        }
        List<String> userIds = identities.stream()
                .map(TransportIdentity::id)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        Map<String, PerfilTransporte> profilesByUserId = new HashMap<>();
        if (!userIds.isEmpty()) {
            perfiles.findMembershipByUsuarioIdIn(userIds).forEach(profile ->
                    profilesByUserId.put(profile.getUsuarioId(), profile));
        }

        List<TransportIdentity> usersWithoutProfile = identities.stream()
                .filter(identity -> !profilesByUserId.containsKey(identity.id()))
                .filter(identity -> identity.email() != null && !identity.email().isBlank())
                .toList();
        Map<String, TransportIdentity> usersByEmail = new HashMap<>();
        List<String> legacyEmails = usersWithoutProfile.stream()
                .map(identity -> {
                    String email = identity.email().trim();
                    usersByEmail.put(email.toLowerCase(Locale.ROOT), identity);
                    return email;
                })
                .distinct()
                .toList();
        if (!legacyEmails.isEmpty()) {
            Query legacyQuery = Query.query(Criteria.where("correo").in(legacyEmails));
            legacyQuery.fields().include("correo").include("sitm").include("sistema_asignado");
            mongoTemplate.find(legacyQuery, Usuario.class).forEach(legacy -> {
                TransportIdentity identity = legacy.getCorreo() == null
                        ? null : usersByEmail.get(legacy.getCorreo().toLowerCase(Locale.ROOT));
                if (identity != null) {
                    PerfilTransporte profile = new PerfilTransporte();
                    profile.setId(identity.id());
                    profile.setUsuarioId(identity.id());
                    profile.setSitm(legacy.getSitm());
                    profile.setSistemaAsignado(legacy.getSistemaAsignado());
                    profilesByUserId.put(identity.id(), profile);
                }
            });
        }
        return profilesByUserId;
    }

    private Usuario toUsuario(AccessUser identity, PerfilTransporte profile) {
        Usuario usuario = new Usuario();
        usuario.setId(identity.id());
        usuario.setCorreo(identity.email());
        usuario.setNombre(identity.name());
        usuario.setRole(Usuario.ROLE_MODERATOR.equals(identity.role())
                && !identity.isTransportModerator()
                        ? Usuario.ROLE_USER
                        : identity.role());
        usuario.setModeradorExterno(Usuario.ROLE_MODERATOR.equals(identity.role())
                && !identity.isTransportModerator());
        usuario.setActivo(identity.active());
        usuario.setVerificado(identity.verified());
        usuario.setFechaRegistro(identity.registeredAt());
        usuario.setTelefono(identity.phone());
        usuario.setSistemaAsignado(sistemaAsignado(identity, profile));
        usuario.setSitm(profile.getSitm());
        usuario.setTarjetas(profile.getTarjetas());
        usuario.setRutasFavoritas(profile.getRutasFavoritas());
        usuario.setPreferenciasNotificacionRutas(profile.getPreferenciasNotificacionRutas());
        usuario.setBusAsignado(profile.getBusAsignado());
        return usuario;
    }

    private SistemaTransporte sistemaAsignado(AccessUser identity, PerfilTransporte profile) {
        if (!Usuario.ROLE_MODERATOR.equals(identity.role())) {
            return profile.getSistemaAsignado();
        }
        if (identity.serviceAssignment() == null || identity.serviceAssignment().isBlank()) {
            return profile.getSistemaAsignado();
        }
        if (!identity.isTransportModerator()) {
            return null;
        }
        return SistemaTransporte.valueOf(identity.serviceAssignment());
    }

    private Optional<PerfilTransporte> profileFromLegacy(Usuario identity) {
        if (identity == null || identity.getCorreo() == null) {
            return Optional.empty();
        }
        String escapedEmail = Pattern.quote(identity.getCorreo().trim());
        Document legacy = mongoTemplate.findOne(
                Query.query(Criteria.where("correo").regex("^" + escapedEmail + "$", "i")),
                Document.class,
                "usuarios");
        if (legacy == null) {
            return Optional.empty();
        }
        PerfilTransporte profile = mongoTemplate.getConverter().read(PerfilTransporte.class, legacy);
        profile.setId(identity.getId());
        profile.setUsuarioId(identity.getId());
        return Optional.of(profile);
    }

    private Optional<PerfilTransporte> profileFromLegacy(AccessUser identity) {
        Usuario user = new Usuario();
        user.setId(identity.id());
        user.setCorreo(identity.email());
        return profileFromLegacy(user);
    }

    private Optional<Usuario> legacyProfileByCard(String numeroTarjeta, SistemaTransporte sistema) {
        Criteria tarjeta = Criteria.where("tarjetas").elemMatch(
                sistema == null
                        ? Criteria.where("numeroTarjeta").is(numeroTarjeta)
                        : new Criteria().andOperator(
                                Criteria.where("numeroTarjeta").is(numeroTarjeta),
                                Criteria.where("sistemaTransporte").is(sistema)));
        Document legacy = mongoTemplate.findOne(
                Query.query(tarjeta), Document.class, "usuarios");
        if (legacy == null) {
            return Optional.empty();
        }
        Object email = legacy.get("correo");
        if (!(email instanceof String correo) || correo.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(acceso.findByEmail(correo)).map(this::toUsuario);
    }

    private static Usuario profileReference(PerfilTransporte profile) {
        Usuario reference = new Usuario();
        reference.setId(profile.getUsuarioId());
        return reference;
    }

    private static void copyProfile(Usuario usuario, PerfilTransporte profile) {
        profile.setSistemaAsignado(usuario.getSistemaAsignado());
        profile.setSitm(usuario.getSitm());
        profile.setTarjetas(usuario.getTarjetas());
        profile.setRutasFavoritas(usuario.getRutasFavoritas());
        profile.setPreferenciasNotificacionRutas(usuario.getPreferenciasNotificacionRutas());
        profile.setBusAsignado(usuario.getBusAsignado());
    }

    private static boolean perteneceAlSistema(Usuario usuario, SistemaTransporte sistema) {
        if (sistema == null) {
            return true;
        }
        if (Usuario.ROLE_MODERATOR.equals(usuario.getRole())) {
            return usuario.getSistemaAsignado() == sistema;
        }
        return usuario.getSitm() == sistema
                || (Usuario.ROLE_CONDUCTOR.equals(usuario.getRole())
                        && usuario.getSitm() == null
                        && sistema == SistemaTransporte.TRANSCARIBE);
    }

    private static boolean isAssignedTransportModerator(TransportIdentity identity) {
        return Usuario.ROLE_MODERATOR.equals(identity.role()) && identity.isTransportModerator();
    }

    private static boolean belongsToSystem(
            TransportIdentity identity, PerfilTransporte profile, SistemaTransporte sistema) {
        if (Usuario.ROLE_MODERATOR.equals(identity.role()) && identity.isTransportModerator()) {
            return sistema.name().equals(identity.serviceAssignment());
        }
        SistemaTransporte assignedSystem = profile == null ? null : profile.getSitm();
        return assignedSystem == sistema
                || (Usuario.ROLE_CONDUCTOR.equals(identity.role())
                        && assignedSystem == null
                        && sistema == SistemaTransporte.TRANSCARIBE);
    }
}
