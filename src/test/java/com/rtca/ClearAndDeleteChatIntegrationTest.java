package com.rtca;

import com.rtca.conversation.dto.ConversationResponse;
import com.rtca.message.dto.MessagePage;
import com.rtca.message.dto.MessageResponse;
import com.rtca.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

class ClearAndDeleteChatIntegrationTest extends IntegrationTest {

    @Test
    void clearingHidesEarlierMessagesOnlyForMe() {
        TestUser alice = newUser();
        TestUser bob = newUser();
        UUID convId = direct(alice, bob);
        send(alice, convId, "old");

        assertThat(call(POST, "/api/conversations/" + convId + "/clear", bob, null, Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(contents(bob, convId)).isEmpty();
        assertThat(entry(bob, convId).orElseThrow().unreadCount()).isZero();
        assertThat(contents(alice, convId)).containsExactly("old");

        send(alice, convId, "new");
        assertThat(contents(bob, convId)).containsExactly("new");
        assertThat(entry(bob, convId).orElseThrow().unreadCount()).isEqualTo(1);
    }

    @Test
    void deletingDirectChatHidesItUntilTheNextMessage() {
        TestUser alice = newUser();
        TestUser bob = newUser();
        UUID convId = direct(alice, bob);
        send(alice, convId, "before delete");

        assertThat(call(DELETE, "/api/conversations/" + convId, bob, null, Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(entry(bob, convId)).isEmpty();
        assertThat(entry(alice, convId)).isPresent();
        assertThat(contents(alice, convId)).containsExactly("before delete");

        send(alice, convId, "after delete");
        assertThat(entry(bob, convId)).isPresent();
        assertThat(contents(bob, convId)).containsExactly("after delete");
    }

    @Test
    void listPreviewShowsNewestMessageTheViewerCanSee() {
        TestUser alice = newUser();
        TestUser bob = newUser();
        UUID convId = direct(alice, bob);
        send(alice, convId, "first");
        Long second = send(alice, convId, "second");
        assertThat(entry(bob, convId).orElseThrow().lastMessage().content()).isEqualTo("second");

        call(DELETE, "/api/conversations/" + convId + "/messages/" + second + "?scope=me", bob, null, Void.class);
        assertThat(entry(bob, convId).orElseThrow().lastMessage().content()).isEqualTo("first");
        assertThat(entry(alice, convId).orElseThrow().lastMessage().content()).isEqualTo("second");

        call(POST, "/api/conversations/" + convId + "/clear", bob, null, Void.class);
        assertThat(entry(bob, convId).orElseThrow().lastMessage()).isNull();

        send(alice, convId, "third");
        assertThat(entry(bob, convId).orElseThrow().lastMessage().content()).isEqualTo("third");
    }

    private UUID direct(TestUser a, TestUser b) {
        return call(POST, "/api/conversations/direct", a, Map.of("userId", b.id()), ConversationResponse.class)
                .getBody().id();
    }

    private Long send(TestUser as, UUID convId, String content) {
        return call(POST, "/api/conversations/" + convId + "/messages", as,
                Map.of("clientMessageId", UUID.randomUUID().toString(), "content", content), MessageResponse.class)
                .getBody().id();
    }

    private List<String> contents(TestUser as, UUID convId) {
        return call(GET, "/api/conversations/" + convId + "/messages", as, null, MessagePage.class)
                .getBody().items().stream().map(MessageResponse::content).toList();
    }

    private Optional<ConversationResponse> entry(TestUser as, UUID convId) {
        return call(GET, "/api/conversations", as, null, ConversationPage.class).getBody().content().stream()
                .filter(c -> c.id().equals(convId))
                .findFirst();
    }

    private record ConversationPage(List<ConversationResponse> content) {
    }
}
