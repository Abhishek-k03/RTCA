package com.rtca;

import com.fasterxml.jackson.databind.JsonNode;
import com.rtca.conversation.dto.ConversationResponse;
import com.rtca.message.dto.MessagePage;
import com.rtca.message.dto.MessageResponse;
import com.rtca.message.dto.Reaction;
import com.rtca.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.stomp.StompSession;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;

class ReplyAndReactionIntegrationTest extends IntegrationTest {

    private static final ParameterizedTypeReference<List<Reaction>> REACTIONS = new ParameterizedTypeReference<>() {
    };

    @Test
    void replyCarriesAPreviewOfTheQuotedMessage() {
        TestUser alice = newUser();
        TestUser bob = newUser();
        UUID convId = direct(alice, bob);
        Long question = send(alice, convId, "lunch at one?", null).getBody().id();

        var reply = send(bob, convId, "works for me", question);
        assertThat(reply.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        MessageResponse.ReplyPreview preview = reply.getBody().replyTo();
        assertThat(preview.id()).isEqualTo(question);
        assertThat(preview.senderId()).isEqualTo(alice.id());
        assertThat(preview.content()).isEqualTo("lunch at one?");

        // the preview follows edits and deletes of the original
        call(PATCH, url(convId, question), alice, Map.of("content", "lunch at two?"), MessageResponse.class);
        assertThat(history(alice, convId).getFirst().replyTo().content()).isEqualTo("lunch at two?");
        call(DELETE, url(convId, question) + "?scope=everyone", alice, null, Void.class);
        MessageResponse.ReplyPreview gone = history(alice, convId).getFirst().replyTo();
        assertThat(gone.deleted()).isTrue();
        assertThat(gone.content()).isNull();
    }

    @Test
    void replyMustQuoteAMessageFromTheSameChat() {
        TestUser alice = newUser();
        TestUser bob = newUser();
        TestUser carol = newUser();
        UUID ours = direct(alice, bob);
        UUID other = direct(alice, carol);
        Long elsewhere = send(alice, other, "private", null).getBody().id();

        assertThat(send(alice, ours, "quoting", elsewhere).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(send(alice, ours, "quoting", 999_999_999L).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void reactionsAreOnePerMemberAndReachEveryoneLive() throws Exception {
        TestUser alice = newUser();
        TestUser bob = newUser();
        UUID convId = direct(alice, bob);
        Long msgId = send(alice, convId, "we shipped it", null).getBody().id();

        StompSession session = connect(alice.token());
        BlockingQueue<JsonNode> events = subscribe(session, "/topic/conversations." + convId);
        Thread.sleep(300);

        List<Reaction> afterLike = react(bob, convId, msgId, "👍").getBody();
        assertThat(afterLike).containsExactly(new Reaction("👍", List.of(bob.id())));
        JsonNode event = nextOfType(events, "REACTION");
        assertThat(event.at("/payload/messageId").asLong()).isEqualTo(msgId);
        assertThat(event.at("/payload/reactions/0/emoji").asText()).isEqualTo("👍");

        react(alice, convId, msgId, "👍");
        // picking another one replaces bob's first choice
        List<Reaction> changed = react(bob, convId, msgId, "❤️").getBody();
        assertThat(changed).containsExactly(
                new Reaction("👍", List.of(alice.id())),
                new Reaction("❤️", List.of(bob.id())));
        assertThat(history(bob, convId).getFirst().reactions()).isEqualTo(changed);

        var removed = rest.exchange(url(convId, msgId) + "/reaction", DELETE, auth(bob, null), REACTIONS);
        assertThat(removed.getBody()).containsExactly(new Reaction("👍", List.of(alice.id())));
        session.disconnect();
    }

    @Test
    void reactionsNeedAnActiveMemberAndALiveMessage() {
        TestUser alice = newUser();
        TestUser bob = newUser();
        TestUser carol = newUser();
        UUID convId = direct(alice, bob);
        Long msgId = send(alice, convId, "hello", null).getBody().id();

        assertThat(reactStatus(bob, convId, msgId, "🦄")).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reactStatus(carol, convId, msgId, "👍")).isEqualTo(HttpStatus.NOT_FOUND);

        react(bob, convId, msgId, "😂");
        call(DELETE, url(convId, msgId) + "?scope=everyone", alice, null, Void.class);
        assertThat(reactStatus(bob, convId, msgId, "👍")).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(history(bob, convId).getFirst().reactions()).isEmpty();

        UUID groupId = call(POST, "/api/conversations/groups", alice,
                Map.of("name", "g", "memberIds", List.of(bob.id())), ConversationResponse.class).getBody().id();
        Long groupMsg = send(alice, groupId, "hi all", null).getBody().id();
        call(DELETE, "/api/conversations/groups/" + groupId + "/members/" + bob.id(), alice, null, Void.class);
        assertThat(reactStatus(bob, groupId, groupMsg, "👍")).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<MessageResponse> send(TestUser as, UUID convId, String content, Long replyToId) {
        Map<String, Object> body = new HashMap<>();
        body.put("clientMessageId", UUID.randomUUID().toString());
        body.put("content", content);
        if (replyToId != null) {
            body.put("replyToId", replyToId);
        }
        return call(POST, "/api/conversations/" + convId + "/messages", as, body, MessageResponse.class);
    }

    private ResponseEntity<List<Reaction>> react(TestUser as, UUID convId, Long msgId, String emoji) {
        return rest.exchange(url(convId, msgId) + "/reaction", PUT, auth(as, Map.of("emoji", emoji)), REACTIONS);
    }

    private HttpStatus reactStatus(TestUser as, UUID convId, Long msgId, String emoji) {
        return HttpStatus.valueOf(rest.exchange(url(convId, msgId) + "/reaction", PUT,
                auth(as, Map.of("emoji", emoji)), String.class).getStatusCode().value());
    }

    private HttpEntity<Object> auth(TestUser as, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(as.token());
        return new HttpEntity<>(body, headers);
    }

    private String url(UUID convId, Long msgId) {
        return "/api/conversations/" + convId + "/messages/" + msgId;
    }

    private UUID direct(TestUser a, TestUser b) {
        return call(POST, "/api/conversations/direct", a, Map.of("userId", b.id()), ConversationResponse.class)
                .getBody().id();
    }

    private List<MessageResponse> history(TestUser as, UUID convId) {
        return call(GET, "/api/conversations/" + convId + "/messages", as, null, MessagePage.class).getBody().items();
    }
}
