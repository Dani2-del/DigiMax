package com.transcaribe.transcaribe.Controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import com.transcaribe.transcaribe.Model.SistemaTransporte;
import com.transcaribe.transcaribe.Model.Usuario;
import com.transcaribe.transcaribe.Repository.UsuarioRepository;
import com.transcaribe.transcaribe.Repository.PerfilTransporteRepository;
import com.transcaribe.transcaribe.service.AccessIdentityClient;
import com.transcaribe.transcaribe.service.AccessUser;

class UsuarioSitmApiControllerTest {

    private final PerfilTransporteRepository perfiles = mock(PerfilTransporteRepository.class);
    private final MongoOperations mongoTemplate = mock(MongoOperations.class);
    private final StubAccessIdentityClient acceso = new StubAccessIdentityClient();
    private UsuarioRepository usuarios;
    private UsuarioSitmApiController controller;

    @BeforeEach
    void setUp() {
        usuarios = new UsuarioRepository(perfiles, mongoTemplate, acceso);
        controller = new UsuarioSitmApiController(usuarios);
        when(perfiles.findByUsuarioId(anyString())).thenReturn(Optional.empty());
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("usuarios"))).thenReturn(null);
        when(perfiles.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void changesSitmForAuthenticatedRegularUser() {
        acceso.identity = new AccessUser(
                "user-1", "Persona", "persona@example.com", Usuario.ROLE_USER,
                true, true, null, null, null);
        Authentication authentication = authentication("persona@example.com");

        var response = controller.changeSitm(authentication,
                new UsuarioSitmApiController.SitmChangeRequest(SistemaTransporte.TRANSMETRO));

        assertThat(response.getBody())
                .isEqualTo(new UsuarioSitmApiController.SitmResponse("TRANSMETRO"));
        verify(perfiles).save(any());
    }

    @Test
    void rejectsTransportModeratorSitmChange() {
        acceso.identity = new AccessUser(
                "moderator-1", "Moderador", "moderador@example.com", Usuario.ROLE_MODERATOR,
                true, true, null, null, SistemaTransporte.TRANSCARIBE.name());
        Authentication authentication = authentication("moderador@example.com");

        assertThatThrownBy(() -> controller.changeSitm(authentication,
                new UsuarioSitmApiController.SitmChangeRequest(SistemaTransporte.TRANSMETRO)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Solo una cuenta de usuario puede cambiar su SITM");
    }

    private static final class StubAccessIdentityClient extends AccessIdentityClient {
        private AccessUser identity;

        private StubAccessIdentityClient() {
            super("http://localhost");
        }

        @Override
        public AccessUser findByEmail(String email) {
            return identity;
        }
    }

    private static Authentication authentication(String email) {
        return new UsernamePasswordAuthenticationToken(email, "");
    }
}
