package com.rtca;

import com.fasterxml.jackson.databind.JsonNode;
import com.rtca.conversation.dto.ConversationResponse;
import com.rtca.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;

class WebSocketIntegrationTest extends IntegrationTest {

    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    }

    @AfterEach
    void tearDown() {
        stompClient.stop();
    }

    @Test
    void messageSentOverStompReachesOtherMember() throws Exception {
        TestUser alice = newUser();
        TestUser bob = newUser();
        Long convId = call(POST, "/api/conversations/direct", alice, Map.of("userId", bob.id()),
                ConversationResponse.class).getBody().id();

        StompSession bobSession = connect(bob.token());
        BlockingQueue<JsonNode> bobInbox = subscribe(bobSession, "/topic/conversations." + convId);

        StompSession aliceSession = connect(alice.token());
        BlockingQueue<JsonNode> aliceEvents = subscribe(aliceSession, "/user/queue/events");
        Thread.sleep(300);

        aliceSession.send("/app/conversations." + convId + ".send",
                Map.of("clientMessageId", "ws-1", "content", "hi bob"));

        JsonNode received = nextOfType(bobInbox, "MESSAGE");
        assertThat(received.at("/payload/content").asText()).isEqualTo("hi bob");
        assertThat(received.at("/payload/senderId").asLong()).isEqualTo(alice.id());

        JsonNode ack = nextOfType(aliceEvents, "ACK");
        assertThat(ack.at("/payload/clientMessageId").asText()).isEqualTo("ws-1");
        assertThat(ack.at("/payload/duplicate").asBoolean()).isFalse();
    }

    @Test
    void connectWithoutTokenIsRejected() throws Exception {
        CompletableFuture<String> outcome = new CompletableFuture<>();
        stompClient.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(),
                new StompHeaders(), new StompSessionHandlerAdapter() {
                    @Override
                    public void afterConnected(@NonNull StompSession session, @NonNull StompHeaders headers) {
                        outcome.complete("connected");
                    }

                    @Override
                    public void handleFrame(@NonNull StompHeaders headers, Object payload) {
                        outcome.complete("error: " + headers.getFirst("message"));
                    }

                    @Override
                    public void handleException(@NonNull StompSession session, @Nullable StompCommand command,
                                                @NonNull StompHeaders headers, @NonNull byte[] payload,
                                                @NonNull Throwable ex) {
                        outcome.complete("error: " + headers.getFirst("message"));
                    }

                    @Override
                    public void handleTransportError(@NonNull StompSession session, @NonNull Throwable ex) {
                        outcome.complete("error: transport");
                    }
                });

        assertThat(outcome.get(5, TimeUnit.SECONDS)).isEqualTo("error: Missing bearer token");
    }

    private StompSession connect(String token) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);
        return stompClient.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(),
                connectHeaders, new StompSessionHandlerAdapter() {
                }).get(5, TimeUnit.SECONDS);
    }

    private BlockingQueue<JsonNode> subscribe(StompSession session, String destination) {
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
    private JsonNode nextOfType(BlockingQueue<JsonNode> queue, String type) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            JsonNode node = queue.poll(500, TimeUnit.MILLISECONDS);
            if (node != null && type.equals(node.path("type").asText())) {
                return node;
            }
        }
        throw new AssertionError("No " + type + " event received");
    }
}
