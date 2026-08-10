package com.rtca;

import com.fasterxml.jackson.databind.JsonNode;
import com.rtca.conversation.dto.ConversationResponse;
import com.rtca.message.dto.MessageResponse;
import com.rtca.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Arrays;
import java.util.Base64;
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

class GroupPhotoAndProfileEventsIntegrationTest extends IntegrationTest {

    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");

    @Test
    void managersSetAGroupPhotoThatOnlyMembersCanSee() throws Exception {
        TestUser alice = newUser();
        TestUser bob = newUser();
        TestUser carol = newUser();
        TestUser dave = newUser();
        UUID groupId = group(alice, bob, carol);

        StompSession session = connect(bob.token());
        BlockingQueue<JsonNode> events = subscribe(session, "/topic/conversations." + groupId);
        Thread.sleep(300);

        assertThat(setPhoto(bob, groupId, PNG).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var set = setPhoto(alice, groupId, PNG);
        assertThat(set.getStatusCode()).isEqualTo(HttpStatus.OK);
        String url = set.getBody().avatarUrl();
        assertThat(url).startsWith("/api/files/");
        assertThat(nextOfType(events, "GROUP_UPDATED").at("/payload/avatarUrl").asText()).isEqualTo(url);

        assertThat(fetch(url, bob).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetch(url, dave).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        // removed members keep seeing the group as it was, photo included
        call(DELETE, "/api/conversations/groups/" + groupId + "/members/" + carol.id(), alice, null, Void.class);
        assertThat(fetch(url, carol).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(setPhoto(alice, groupId, Arrays.copyOf(PNG, 2 * 1024 * 1024 + 1)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        var removed = call(DELETE, "/api/conversations/groups/" + groupId + "/avatar", alice, null,
                ConversationResponse.class);
        assertThat(removed.getBody().avatarUrl()).isNull();
        assertThat(fetch(url, bob).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        session.disconnect();
    }

    @Test
    void renamingAGroupIsLive() throws Exception {
        TestUser alice = newUser();
        TestUser bob = newUser();
        UUID groupId = group(alice, bob);

        StompSession session = connect(bob.token());
        BlockingQueue<JsonNode> events = subscribe(session, "/topic/conversations." + groupId);
        Thread.sleep(300);

        call(PATCH, "/api/conversations/groups/" + groupId, alice, Map.of("name", "Harbour Society"),
                ConversationResponse.class);
        JsonNode event = nextOfType(events, "GROUP_UPDATED");
        assertThat(event.at("/payload/name").asText()).isEqualTo("Harbour Society");
        assertThat(event.at("/payload/conversationId").asText()).isEqualTo(groupId.toString());
        session.disconnect();
    }

    @Test
    void profileChangesReachPeopleYouShareAChatWith() throws Exception {
        TestUser alice = newUser();
        TestUser bob = newUser();
        TestUser carol = newUser();
        TestUser dave = newUser();
        UUID withBob = direct(alice, bob);
        send(alice, withBob, "hi");
        // carol's chat with alice is still hidden, nobody has written yet
        direct(alice, carol);

        StompSession bobSession = connect(bob.token());
        StompSession carolSession = connect(carol.token());
        StompSession daveSession = connect(dave.token());
        BlockingQueue<JsonNode> bobEvents = subscribe(bobSession, "/user/queue/events");
        BlockingQueue<JsonNode> carolEvents = subscribe(carolSession, "/user/queue/events");
        BlockingQueue<JsonNode> daveEvents = subscribe(daveSession, "/user/queue/events");
        Thread.sleep(300);

        call(PATCH, "/api/users/me", alice, Map.of("displayName", "Alice M", "bio", "new here"), Map.class);

        JsonNode profile = nextOfType(bobEvents, "PROFILE");
        assertThat(profile.at("/payload/id").asText()).isEqualTo(alice.id().toString());
        assertThat(profile.at("/payload/displayName").asText()).isEqualTo("Alice M");
        assertThat(profile.at("/payload/bio").asText()).isEqualTo("new here");

        assertThat(noneOfType(carolEvents, "PROFILE")).isTrue();
        assertThat(noneOfType(daveEvents, "PROFILE")).isTrue();
        bobSession.disconnect();
        carolSession.disconnect();
        daveSession.disconnect();
    }

    private ResponseEntity<ConversationResponse> setPhoto(TestUser as, UUID groupId, byte[] bytes) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(as.token());
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return "group.png";
            }
        });
        return rest.exchange("/api/conversations/groups/" + groupId + "/avatar", PUT,
                new HttpEntity<>(body, headers), ConversationResponse.class);
    }

    private ResponseEntity<byte[]> fetch(String url, TestUser as) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(as.token());
        return rest.exchange(url, GET, new HttpEntity<>(headers), byte[].class);
    }

    private UUID group(TestUser owner, TestUser... members) {
        List<UUID> ids = Arrays.stream(members).map(TestUser::id).toList();
        return call(POST, "/api/conversations/groups", owner, Map.of("name", "g", "memberIds", ids),
                ConversationResponse.class).getBody().id();
    }

    private UUID direct(TestUser a, TestUser b) {
        return call(POST, "/api/conversations/direct", a, Map.of("userId", b.id()), ConversationResponse.class)
                .getBody().id();
    }

    private void send(TestUser as, UUID convId, String content) {
        call(POST, "/api/conversations/" + convId + "/messages", as,
                Map.of("clientMessageId", UUID.randomUUID().toString(), "content", content), MessageResponse.class);
    }
}
