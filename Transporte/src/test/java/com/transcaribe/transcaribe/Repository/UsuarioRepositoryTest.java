package com.transcaribe.transcaribe.Repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;

import com.transcaribe.transcaribe.Model.PerfilTransporte;
import com.transcaribe.transcaribe.Model.SistemaTransporte;
import com.transcaribe.transcaribe.Model.Usuario;
import com.transcaribe.transcaribe.service.AccessIdentityClient;
import com.transcaribe.transcaribe.service.AccessUser;

@ExtendWith(MockitoExtension.class)
class UsuarioRepositoryTest {

    @Mock
    private PerfilTransporteRepository perfiles;

    @Mock
    private MongoOperations mongoTemplate;

    private StubAccessIdentityClient acceso;
    private UsuarioRepository repository;

    @BeforeEach
    void setUp() {
        acceso = new StubAccessIdentityClient();
        repository = new UsuarioRepository(perfiles, mongoTemplate, acceso);
    }

    @Test
    void savesTransportProfileUsingCentralIdentityId() {
        Usuario usuario = new Usuario();
        usuario.setId("central-user-id");
        usuario.setCorreo("persona@example.com");
        usuario.setSistemaAsignado(SistemaTransporte.TRANSCARIBE);
        usuario.setRutasFavoritas(java.util.List.of("R1"));
        when(perfiles.findByUsuarioId("central-user-id")).thenReturn(Optional.empty());
        when(perfiles.save(any(PerfilTransporte.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        repository.save(usuario);

        ArgumentCaptor<PerfilTransporte> profile = ArgumentCaptor.forClass(PerfilTransporte.class);
        verify(perfiles).save(profile.capture());
        assertThat(profile.getValue().getId()).isEqualTo("central-user-id");
        assertThat(profile.getValue().getUsuarioId()).isEqualTo("central-user-id");
        assertThat(profile.getValue().getSistemaAsignado()).isEqualTo(SistemaTransporte.TRANSCARIBE);
        assertThat(profile.getValue().getRutasFavoritas()).containsExactly("R1");
    }

    @Test
    void resolvesLegacyCardLookupToCentralIdentityId() {
        Document legacyUser = new Document("_id", "legacy-user-id")
                .append("correo", "persona@example.com");
        AccessUser identity = new AccessUser(
                "central-user-id", "Persona", "persona@example.com", Usuario.ROLE_USER,
                true, true, null, null, null);
        when(perfiles.findByNumeroTarjetaEnLista("card-1")).thenReturn(Optional.empty());
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("usuarios")))
                .thenReturn(legacyUser)
                .thenReturn(null);
        acceso.identity = identity;
        when(perfiles.findByUsuarioId("central-user-id")).thenReturn(Optional.empty());

        Optional<Usuario> result = repository.findByNumeroTarjetaEnLista("card-1");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().getId()).isEqualTo("central-user-id");
    }

    @Test
    void usesCentralModeratorServiceAssignmentOverTransportProfile() {
        AccessUser identity = new AccessUser(
                "central-user-id", "Persona", "persona@example.com", Usuario.ROLE_MODERATOR,
                true, true, null, null, "TRANSMETRO");
        PerfilTransporte profile = new PerfilTransporte();
        profile.setId("central-user-id");
        profile.setUsuarioId("central-user-id");
        profile.setSistemaAsignado(SistemaTransporte.TRANSCARIBE);
        acceso.identity = identity;
        when(perfiles.findByUsuarioId("central-user-id")).thenReturn(Optional.of(profile));

        Usuario result = repository.findByCorreoIgnoreCase("persona@example.com").orElseThrow();

        assertThat(result.getSistemaAsignado()).isEqualTo(SistemaTransporte.TRANSMETRO);
    }

    @Test
    void treatsWalletModeratorAsRegularTransportUser() {
        AccessUser identity = new AccessUser(
                "central-user-id", "Persona", "persona@example.com", Usuario.ROLE_MODERATOR,
                true, true, null, null, "BILLETERA");
        PerfilTransporte profile = new PerfilTransporte();
        profile.setId("central-user-id");
        profile.setUsuarioId("central-user-id");
        profile.setSistemaAsignado(SistemaTransporte.TRANSCARIBE);
        profile.setSitm(SistemaTransporte.TRANSCARIBE);
        acceso.identity = identity;
        when(perfiles.findByUsuarioId("central-user-id")).thenReturn(Optional.of(profile));

        Usuario result = repository.findByCorreoIgnoreCase("persona@example.com").orElseThrow();

        assertThat(result.getRole()).isEqualTo(Usuario.ROLE_USER);
        assertThat(result.isModeradorExterno()).isTrue();
        assertThat(result.getSitm()).isEqualTo(SistemaTransporte.TRANSCARIBE);
        assertThat(result.getSistemaAsignado()).isNull();
    }

    @Test
    void deactivatesCentralAccountWithoutDeletingProfile() {
        Usuario usuario = new Usuario("persona@example.com", "", "Persona");
        usuario.setId("central-user-id");

        repository.delete(usuario);

        assertThat(acceso.updatedId).isEqualTo("central-user-id");
        assertThat(acceso.updatedActive).isFalse();
        verify(perfiles, never()).delete(any(PerfilTransporte.class));
    }

    private static final class StubAccessIdentityClient extends AccessIdentityClient {
        private AccessUser identity;
        private String updatedId;
        private boolean updatedActive;

        private StubAccessIdentityClient() {
            super("http://localhost");
        }

        @Override
        public AccessUser findByEmail(String email) {
            return identity;
        }

        @Override
        public AccessUser updateUser(
                String id, String name, String email, String role, boolean active, String serviceAssignment) {
            updatedId = id;
            updatedActive = active;
            return null;
        }
    }
}
