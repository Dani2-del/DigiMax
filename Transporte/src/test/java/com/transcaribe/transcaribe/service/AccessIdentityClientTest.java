package com.transcaribe.transcaribe.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class AccessIdentityClientTest {

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void updateUserSendsPatchRequest() throws Exception {
        AtomicReference<String> requestMethod = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/admin/users/user-1", exchange -> {
            requestMethod.set(exchange.getRequestMethod());
            byte[] response = """
                    {"id":"user-1","name":"Persona","email":"persona@example.com",
                     "role":"ROLE_CONDUCTOR","active":true,"verified":true,
                     "registeredAt":null,"phone":null,"serviceAssignment":null}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var body = exchange.getResponseBody()) {
                body.write(response);
            }
        });
        server.start();
        try {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setAttribute(AccessIdentityClient.TOKEN_ATTRIBUTE, "test-token");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
            AccessIdentityClient client = new AccessIdentityClient(
                    "http://127.0.0.1:" + server.getAddress().getPort());

            AccessUser updated = client.updateUser(
                    "user-1", "Persona", "persona@example.com", "ROLE_CONDUCTOR", true, null);

            assertThat(requestMethod.get()).isEqualTo("PATCH");
            assertThat(updated.id()).isEqualTo("user-1");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void findAllLoadsTheDirectoryInOneAuthenticatedRequest() throws Exception {
        AtomicReference<String> requestPath = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/admin/users/directory", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = """
                    [{"id":"user-1","name":"Persona","email":"persona@example.com",
                     "role":"ROLE_USER","active":true,"verified":true,
                     "registeredAt":null,"phone":null,"serviceAssignment":null}]
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var body = exchange.getResponseBody()) {
                body.write(response);
            }
        });
        server.start();
        try {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setAttribute(AccessIdentityClient.TOKEN_ATTRIBUTE, "test-token");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
            AccessIdentityClient client = new AccessIdentityClient(
                    "http://127.0.0.1:" + server.getAddress().getPort());

            List<AccessUser> users = client.findAll();

            assertThat(requestPath.get()).isEqualTo("/api/admin/users/directory");
            assertThat(authorization.get()).isEqualTo("Bearer test-token");
            assertThat(users).singleElement().extracting(AccessUser::id).isEqualTo("user-1");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void findTransportDirectoryUsesTheScopedEndpoint() throws Exception {
        AtomicReference<String> requestPath = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/auth/transport-directory", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            byte[] response = """
                    [{"id":"user-1","email":"persona@example.com","role":"ROLE_USER",
                     "serviceAssignment":null}]
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var body = exchange.getResponseBody()) {
                body.write(response);
            }
        });
        server.start();
        try {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setAttribute(AccessIdentityClient.TOKEN_ATTRIBUTE, "test-token");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
            AccessIdentityClient client = new AccessIdentityClient(
                    "http://127.0.0.1:" + server.getAddress().getPort());

            var identities = client.findTransportDirectory();

            assertThat(requestPath.get()).isEqualTo("/api/auth/transport-directory");
            assertThat(identities).singleElement().satisfies(identity -> {
                assertThat(identity.id()).isEqualTo("user-1");
                assertThat(identity.email()).isEqualTo("persona@example.com");
            });
        } finally {
            server.stop(0);
        }
    }
}
