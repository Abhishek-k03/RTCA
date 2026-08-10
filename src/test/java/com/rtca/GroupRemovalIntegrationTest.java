package com.rtca;

import com.fasterxml.jackson.databind.JsonNode;
import com.rtca.conversation.dto.ConversationResponse;
import com.rtca.conversation.dto.ParticipantResponse;
import com.rtca.message.dto.MessagePage;
import com.rtca.message.dto.MessageResponse;
import com.rtca.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.stomp.StompSession;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

class GroupRemovalIntegrationTest extends IntegrationTest {

    @Test
    void removedMemberKeepsReadOnlyHistoryUpToRemoval() {
        TestUser alice = newUser();
        TestUser bob = newUser();
        TestUser carol = newUser();
        UUID groupId = group(alice, bob, carol);

        Long before = send(alice, groupId, "before").getBody().id();
        remove(alice, groupId, bob);
        send(alice, groupId, "after");

        ConversationResponse seen = call(GET, "/api/conversations/" + groupId, bob, null,
                ConversationResponse.class).getBody();
        assertThat(seen.removedAt()).isNotNull();
        assertThat(seen.participants()).extracting(ParticipantResponse::userId).doesNotContain(bob.id());
        assertThat(seen.unreadCount()).isEqualTo(1);

        List<Long> history = history(bob, groupId).items().stream().map(MessageResponse::id).toList();
        assertThat(history).containsExactly(before);

        assertThat(send(bob, groupId, "let me back").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void removedMemberStopsReceivingLiveMessages() throws Exception {
        TestUser alice = newUser();
        TestUser bob = newUser();
        TestUser carol = newUser();
        UUID groupId = group(alice, bob, carol);

        StompSession bobSession = connect(bob.token());
        BlockingQueue<JsonNode> bobTopic = subscribe(bobSession, "/topic/conversations." + groupId);
        BlockingQueue<JsonNode> bobEvents = subscribe(bobSession, "/user/queue/events");
        StompSession carolSession = connect(carol.token());
        BlockingQueue<JsonNode> carolTopic = subscribe(carolSession, "/topic/conversations." + groupId);
        Thread.sleep(300);

        remove(alice, groupId, bob);
        JsonNode removed = nextOfType(bobEvents, "REMOVED");
        assertThat(removed.at("/payload/conversationId").asText()).isEqualTo(groupId.toString());

        send(alice, groupId, "bob should not see this");
        assertThat(nextOfType(carolTopic, "MESSAGE").at("/payload/content").asText())
                .isEqualTo("bob should not see this");
        assertThat(bobTopic.poll(1, TimeUnit.SECONDS)).isNull();
    }

    @Test
    void onlyFormerMembersCanDeleteConversation() {
        TestUser alice = newUser();
        TestUser bob = newUser();
        UUID groupId = group(alice, bob);

        assertThat(call(DELETE, "/api/conversations/" + groupId, bob, null, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        remove(bob, groupId, bob);
        assertThat(call(DELETE, "/api/conversations/" + groupId, bob, null, Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(call(GET, "/api/conversations/" + groupId, bob, null, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(GET, "/api/conversations/" + groupId, alice, null, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void readdedMemberSeesFullHistoryAndCanSend() {
        TestUser alice = newUser();
        TestUser bob = newUser();
        TestUser carol = newUser();
        UUID groupId = group(alice, bob, carol);

        send(alice, groupId, "one");
        remove(alice, groupId, bob);
        send(alice, groupId, "two");
        call(POST, "/api/conversations/groups/" + groupId + "/members", alice,
                Map.of("userIds", List.of(bob.id())), ConversationResponse.class);

        ConversationResponse seen = call(GET, "/api/conversations/" + groupId, bob, null,
                ConversationResponse.class).getBody();
        assertThat(seen.removedAt()).isNull();
        assertThat(history(bob, groupId).items()).extracting(MessageResponse::content).containsExactly("two", "one");
        assertThat(send(bob, groupId, "back").getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void removedGroupStaysAtRemovalTimeInList() {
        TestUser alice = newUser();
        TestUser bob = newUser();
        TestUser carol = newUser();
        UUID groupId = group(alice, bob, carol);
        UUID directId = call(POST, "/api/conversations/direct", carol, Map.of("userId", bob.id()),
                ConversationResponse.class).getBody().id();

        remove(alice, groupId, bob);
        send(carol, directId, "direct after removal");
        send(alice, groupId, "group chatter bob can't see");

        List<ConversationResponse> list = call(GET, "/api/conversations", bob, null, ConversationPage.class)
                .getBody().content();
        assertThat(list).extracting(ConversationResponse::id).containsExactly(directId, groupId);
        ConversationResponse removed = list.get(1);
        assertThat(removed.lastMessageAt()).isEqualTo(removed.removedAt());
    }

    @Test
    void readdedMemberGetsAddedEvent() throws Exception {
        TestUser alice = newUser();
        TestUser bob = newUser();
        TestUser carol = newUser();
        UUID groupId = group(alice, bob, carol);
        remove(alice, groupId, bob);

        StompSession bobSession = connect(bob.token());
        BlockingQueue<JsonNode> bobEvents = subscribe(bobSession, "/user/queue/events");
        Thread.sleep(300);

        call(POST, "/api/conversations/groups/" + groupId + "/members", alice,
                Map.of("userIds", List.of(bob.id())), ConversationResponse.class);
        assertThat(nextOfType(bobEvents, "ADDED").at("/payload/conversationId").asText()).isEqualTo(groupId.toString());
    }

    @Test
    void newGroupNotifiesMembersButNotCreator() throws Exception {
        TestUser alice = newUser();
        TestUser bob = newUser();
        BlockingQueue<JsonNode> aliceEvents = subscribe(connect(alice.token()), "/user/queue/events");
        BlockingQueue<JsonNode> bobEvents = subscribe(connect(bob.token()), "/user/queue/events");
        Thread.sleep(300);

        UUID groupId = group(alice, bob);

        assertThat(nextOfType(bobEvents, "ADDED").at("/payload/conversationId").asText()).isEqualTo(groupId.toString());
        assertThat(aliceEvents.poll(1, TimeUnit.SECONDS)).isNull();
    }

    @Test
    void directChatStaysHiddenFromBothUntilFirstMessage() throws Exception {
        TestUser alice = newUser();
        TestUser bob = newUser();
        BlockingQueue<JsonNode> bobEvents = subscribe(connect(bob.token()), "/user/queue/events");
        Thread.sleep(300);

        UUID directId = call(POST, "/api/conversations/direct", alice, Map.of("userId", bob.id()),
                ConversationResponse.class).getBody().id();
        assertThat(bobEvents.poll(1, TimeUnit.SECONDS)).isNull();
        assertThat(listIds(bob)).doesNotContain(directId);
        assertThat(listIds(alice)).doesNotContain(directId);

        send(alice, directId, "hi bob");
        assertThat(nextOfType(bobEvents, "ADDED").at("/payload/conversationId").asText()).isEqualTo(directId.toString());
        assertThat(listIds(alice)).contains(directId);
        List<ConversationResponse> bobList = list(bob);
        assertThat(bobList).extracting(ConversationResponse::id).contains(directId);
        assertThat(bobList.stream().filter(c -> c.id().equals(directId)).findFirst().orElseThrow().unreadCount())
                .isEqualTo(1);

        send(alice, directId, "second");
        assertThat(bobEvents.poll(1, TimeUnit.SECONDS)).isNull();
    }

    @Test
    void openingDirectChatFromEitherSideReusesItAndFirstMessageShowsIt() {
        TestUser alice = newUser();
        TestUser bob = newUser();
        UUID directId = call(POST, "/api/conversations/direct", alice, Map.of("userId", bob.id()),
                ConversationResponse.class).getBody().id();

        UUID bobsId = call(POST, "/api/conversations/direct", bob, Map.of("userId", alice.id()),
                ConversationResponse.class).getBody().id();
        assertThat(bobsId).isEqualTo(directId);
        assertThat(listIds(bob)).doesNotContain(directId);

        send(bob, directId, "hi alice");
        assertThat(listIds(alice)).contains(directId);
        assertThat(listIds(bob)).contains(directId);
    }

    private List<ConversationResponse> list(TestUser as) {
        return call(GET, "/api/conversations", as, null, ConversationPage.class).getBody().content();
    }

    private List<UUID> listIds(TestUser as) {
        return list(as).stream().map(ConversationResponse::id).toList();
    }

    private record ConversationPage(List<ConversationResponse> content) {
    }

    private UUID group(TestUser owner, TestUser... members) {
        List<UUID> ids = Arrays.stream(members).map(TestUser::id).toList();
        return call(POST, "/api/conversations/groups", owner, Map.of("name", "g", "memberIds", ids),
                ConversationResponse.class).getBody().id();
    }

    private void remove(TestUser as, UUID groupId, TestUser target) {
        assertThat(call(DELETE, "/api/conversations/groups/" + groupId + "/members/" + target.id(), as, null,
                Void.class).getStatusCode().is2xxSuccessful()).isTrue();
    }

    private ResponseEntity<MessageResponse> send(TestUser as, UUID convId, String content) {
        return call(POST, "/api/conversations/" + convId + "/messages", as,
                Map.of("clientMessageId", UUID.randomUUID().toString(), "content", content),
                MessageResponse.class);
    }

    private MessagePage history(TestUser as, UUID convId) {
        return call(GET, "/api/conversations/" + convId + "/messages", as, null, MessagePage.class).getBody();
    }
}
