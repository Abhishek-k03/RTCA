package com.rtca.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.rtca.auth.dto.AuthResponse;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.lang.NonNull;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.PostgreSQLContainer;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Boots the whole app against real postgres + redis containers.
 * Containers are shared by all test classes so the spring context can be cached.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "app.rate-limit.messages.limit=1000",
        "app.files.dir=${java.io.tmpdir}/rtca-test-files"
})
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

    protected record TestUser(UUID id, String username, String token) {
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

    private WebSocketStompClient stompClient;

    @AfterEach
    void stopStompClient() {
        if (stompClient != null) {
            stompClient.stop();
            stompClient = null;
        }
    }

    /** Made on first use, so tests without websockets don't start one. */
    protected WebSocketStompClient stompClient() {
        if (stompClient == null) {
            stompClient = new WebSocketStompClient(new StandardWebSocketClient());
            stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        }
        return stompClient;
    }

    protected StompSession connect(String token) throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + token);
        return stompClient().connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(),
                headers, new StompSessionHandlerAdapter() {
                }).get(5, TimeUnit.SECONDS);
    }

    protected BlockingQueue<JsonNode> subscribe(StompSession session, String destination) {
        BlockingQueue<JsonNode> queue = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            @NonNull
            public Type getPayloadType(@NonNull StompHeaders headers) {
                return JsonNode.class;
            }

            @Override
            public void handleFrame(@NonNull StompHeaders headers, Object payload) {
                queue.add((JsonNode) payload);
            }
        });
        return queue;
    }

    // skips unrelated events like PRESENCE
    protected JsonNode nextOfType(BlockingQueue<JsonNode> queue, String type) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            JsonNode node = queue.poll(500, TimeUnit.MILLISECONDS);
            if (node != null && type.equals(node.path("type").asText())) {
                return node;
            }
        }
        throw new AssertionError("No " + type + " event received");
    }

    /** True when no event of this type shows up for a while. */
    protected boolean noneOfType(BlockingQueue<JsonNode> queue, String type) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 1500;
        while (System.currentTimeMillis() < deadline) {
            JsonNode node = queue.poll(300, TimeUnit.MILLISECONDS);
            if (node != null && type.equals(node.path("type").asText())) {
                return false;
            }
        }
        return true;
    }
}
