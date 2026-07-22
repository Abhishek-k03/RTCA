package com.rtca.support;

import com.rtca.auth.dto.AuthResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;
import java.util.UUID;

/**
 * Boots the whole app against real postgres + redis containers.
 * Containers are shared by all test classes so the spring context can be cached.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "app.rate-limit.messages.limit=1000")
public abstract class IntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @Autowired
    protected TestRestTemplate rest;

    @LocalServerPort
    protected int port;

    protected record TestUser(Long id, String username, String token) {
    }

    protected TestUser newUser() {
        String username = "u" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, String> body = Map.of(
                "username", username,
                "email", username + "@test.com",
                "password", "password123");
        rest.postForEntity("/api/auth/register", body, Map.class);
        AuthResponse auth = rest.postForObject("/api/auth/login",
                Map.of("login", username, "password", "password123"), AuthResponse.class);
        return new TestUser(auth.user().id(), username, auth.accessToken());
    }

    protected <T> ResponseEntity<T> call(HttpMethod method, String url, TestUser as, Object body, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (as != null) {
            headers.setBearerAuth(as.token());
        }
        return rest.exchange(url, method, new HttpEntity<>(body, headers), type);
    }
}
