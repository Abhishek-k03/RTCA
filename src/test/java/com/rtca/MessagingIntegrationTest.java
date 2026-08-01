package com.rtca;

import com.rtca.conversation.dto.ConversationResponse;
import com.rtca.message.dto.MessagePage;
import com.rtca.message.dto.MessageResponse;
import com.rtca.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

class MessagingIntegrationTest extends IntegrationTest {

    @Test
    void concurrentDirectChatCreationYieldsOneConversation() throws Exception {
        TestUser alice = newUser();
        TestUser bob = newUser();

        List<Callable<UUID>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            TestUser from = i % 2 == 0 ? alice : bob;
            TestUser to = i % 2 == 0 ? bob : alice;
            tasks.add(() -> call(POST, "/api/conversations/direct", from, Map.of("userId", to.id()),
                    ConversationResponse.class).getBody().id());
        }

        Set<UUID> ids = new HashSet<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(10)) {
            for (Future<UUID> f : pool.invokeAll(tasks)) {
                ids.add(f.get());
            }
        }
        assertThat(ids).hasSize(1);
    }

    @Test
    void resendingSameClientMessageIdIsIdempotent() {
        TestUser alice = newUser();
        TestUser bob = newUser();
        UUID convId = direct(alice, bob);

        var body = Map.of("clientMessageId", "abc-123", "content", "hello");
        ResponseEntity<MessageResponse> first = send(alice, convId, body);
        ResponseEntity<MessageResponse> retry = send(alice, convId, body);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(retry.getBody().id()).isEqualTo(first.getBody().id());
        assertThat(history(bob, convId, "").items()).hasSize(1);
    }

    @Test
    void historyPagesWithoutGapsOrDuplicates() {
        TestUser alice = newUser();
        TestUser bob = newUser();
        UUID convId = direct(alice, bob);
        for (int i = 0; i < 25; i++) {
            send(alice, convId, Map.of("clientMessageId", "m" + i, "content", "msg " + i));
        }

        List<Long> seen = new ArrayList<>();
        MessagePage page = history(bob, convId, "?limit=10");
        seen.addAll(page.items().stream().map(MessageResponse::id).toList());
        while (page.hasMore()) {
            page = history(bob, convId, "?limit=10&before=" + page.nextCursor());
            seen.addAll(page.items().stream().map(MessageResponse::id).toList());
        }

        assertThat(seen).hasSize(25).doesNotHaveDuplicates();
        assertThat(seen).isSortedAccordingTo((a, b) -> Long.compare(b, a));
    }

    @Test
    void nonMemberCannotReadOrWrite() {
        TestUser alice = newUser();
        TestUser bob = newUser();
        TestUser mallory = newUser();
        UUID convId = direct(alice, bob);

        assertThat(call(GET, "/api/conversations/" + convId + "/messages", mallory, null, String.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(send(mallory, convId, Map.of("clientMessageId", "x", "content", "hi"))
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void unreadCountDropsAfterRead() {
        TestUser alice = newUser();
        TestUser bob = newUser();
        UUID convId = direct(alice, bob);
        send(alice, convId, Map.of("clientMessageId", "u1", "content", "one"));
        Long lastId = send(alice, convId, Map.of("clientMessageId", "u2", "content", "two")).getBody().id();

        assertThat(conversation(bob, convId).unreadCount()).isEqualTo(2);

        call(POST, "/api/conversations/" + convId + "/messages/read", bob, Map.of("messageId", lastId), Void.class);

        assertThat(conversation(bob, convId).unreadCount()).isZero();
        assertThat(conversation(alice, convId).unreadCount()).isZero();
    }

    private UUID direct(TestUser a, TestUser b) {
        return call(POST, "/api/conversations/direct", a, Map.of("userId", b.id()), ConversationResponse.class)
                .getBody().id();
    }

    private ResponseEntity<MessageResponse> send(TestUser as, UUID convId, Map<String, String> body) {
        return call(POST, "/api/conversations/" + convId + "/messages", as, body, MessageResponse.class);
    }

    private MessagePage history(TestUser as, UUID convId, String query) {
        return call(HttpMethod.GET, "/api/conversations/" + convId + "/messages" + query, as, null, MessagePage.class)
                .getBody();
    }

    private ConversationResponse conversation(TestUser as, UUID convId) {
        return call(GET, "/api/conversations/" + convId, as, null, ConversationResponse.class).getBody();
    }
}
