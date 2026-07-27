package com.rtca;

import com.rtca.conversation.dto.ConversationResponse;
import com.rtca.message.dto.MessagePage;
import com.rtca.message.dto.MessageResponse;
import com.rtca.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;

class MessageEditDeleteIntegrationTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void senderCanEditOwnMessage() {
        TestUser alice = newUser();
        TestUser bob = newUser();
        Long convId = direct(alice, bob);
        Long msgId = send(alice, convId, "helo");

        var edited = call(PATCH, url(convId, msgId), alice, Map.of("content", "hello"), MessageResponse.class);
        assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(edited.getBody().editedAt()).isNotNull();

        MessageResponse seen = history(bob, convId).getFirst();
        assertThat(seen.content()).isEqualTo("hello");
        assertThat(seen.editedAt()).isNotNull();

        assertThat(call(PATCH, url(convId, msgId), bob, Map.of("content", "hacked"), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void deleteForEveryoneWipesContentForAllMembers() {
        TestUser alice = newUser();
        TestUser bob = newUser();
        Long convId = direct(alice, bob);
        Long msgId = send(alice, convId, "oops");

        assertThat(call(POST, "/api/conversations/" + convId + "/clear", bob, null, Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        Long second = send(alice, convId, "wrong chat");

        assertThat(call(DELETE, url(convId, second) + "?scope=everyone", bob, null, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(DELETE, url(convId, second) + "?scope=everyone", alice, null, Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        MessageResponse seen = history(bob, convId).getFirst();
        assertThat(seen.id()).isEqualTo(second);
        assertThat(seen.deleted()).isTrue();
        assertThat(seen.content()).isNull();
        assertThat(history(alice, convId)).extracting(MessageResponse::id).containsExactly(second, msgId);

        assertThat(call(PATCH, url(convId, second), alice, Map.of("content", "x"), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void deleteForMeHidesOnlyForMe() {
        TestUser alice = newUser();
        TestUser bob = newUser();
        Long convId = direct(alice, bob);
        Long msgId = send(alice, convId, "hi");

        assertThat(call(DELETE, url(convId, msgId), bob, null, Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(history(bob, convId)).isEmpty();
        assertThat(history(alice, convId)).extracting(MessageResponse::content).containsExactly("hi");
    }

    @Test
    void editAndDeleteForEveryoneExpireAfterWindow() {
        TestUser alice = newUser();
        TestUser bob = newUser();
        Long convId = direct(alice, bob);
        Long msgId = send(alice, convId, "old");
        jdbc.update("update messages set created_at = now() - interval '16 minutes' where id = ?", msgId);

        assertThat(call(PATCH, url(convId, msgId), alice, Map.of("content", "new"), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(DELETE, url(convId, msgId) + "?scope=everyone", alice, null, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        // delete for me has no time limit
        assertThat(call(DELETE, url(convId, msgId) + "?scope=me", alice, null, Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
    }

    private String url(Long convId, Long msgId) {
        return "/api/conversations/" + convId + "/messages/" + msgId;
    }

    private Long direct(TestUser a, TestUser b) {
        return call(POST, "/api/conversations/direct", a, Map.of("userId", b.id()), ConversationResponse.class)
                .getBody().id();
    }

    private Long send(TestUser as, Long convId, String content) {
        return call(POST, "/api/conversations/" + convId + "/messages", as,
                Map.of("clientMessageId", UUID.randomUUID().toString(), "content", content), MessageResponse.class)
                .getBody().id();
    }

    private List<MessageResponse> history(TestUser as, Long convId) {
        return call(GET, "/api/conversations/" + convId + "/messages", as, null, MessagePage.class).getBody().items();
    }
}
