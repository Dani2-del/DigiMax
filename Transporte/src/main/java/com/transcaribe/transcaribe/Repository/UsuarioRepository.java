package com.transcaribe.transcaribe.Repository;

import java.util.List;
import java.util.Optional;
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

@Repository
public class UsuarioRepository {

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
        return acceso.findAll().stream().map(this::toUsuario).toList();
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

    public Stream<Usuario> streamAll() {
        return findAll().stream();
    }

    public Stream<Usuario> streamBySistemaTransporte(SistemaTransporte sistema) {
        return findAll().stream().filter(usuario -> perteneceAlSistema(usuario, sistema));
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

    private Usuario toUsuario(AccessUser identity, PerfilTransporte profile) {
        Usuario usuario = new Usuario();
        usuario.setId(identity.id());
        usuario.setCorreo(identity.email());
        usuario.setNombre(identity.name());
        usuario.setRole(Usuario.ROLE_MODERATOR.equals(identity.role())
                && !identity.isTransportModerator()
                        ? Usuario.ROLE_USER
                        : identity.role());
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
}
