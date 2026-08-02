package com.rtca;

import com.rtca.conversation.dto.ConversationResponse;
import com.rtca.conversation.dto.ParticipantResponse;
import com.rtca.file.FileStorage;
import com.rtca.message.MessageType;
import com.rtca.message.dto.MessagePage;
import com.rtca.message.dto.MessageResponse;
import com.rtca.support.IntegrationTest;
import com.rtca.user.dto.UserResponse;
import com.rtca.user.dto.UserSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;

class ProfileAndImageIntegrationTest extends IntegrationTest {

    // 1x1 png
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private FileStorage storage;

    @Test
    void profileNameAndBioCanBeUpdated() {
        TestUser alice = newUser();
        TestUser bob = newUser();

        var updated = call(PATCH, "/api/users/me", alice, Map.of("displayName", "Alice", "bio", "  likes tea  "),
                UserResponse.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().bio()).isEqualTo("likes tea");

        UserSummary seen = call(GET, "/api/users/" + alice.id(), bob, null, UserSummary.class).getBody();
        assertThat(seen.displayName()).isEqualTo("Alice");
        assertThat(seen.bio()).isEqualTo("likes tea");

        // bio left out stays, blank clears it
        assertThat(call(PATCH, "/api/users/me", alice, Map.of("displayName", "Al"), UserResponse.class)
                .getBody().bio()).isEqualTo("likes tea");
        assertThat(call(PATCH, "/api/users/me", alice, Map.of("displayName", "Al", "bio", " "), UserResponse.class)
                .getBody().bio()).isNull();

        assertThat(call(PATCH, "/api/users/me", alice, Map.of("displayName", "Al", "bio", "x".repeat(201)), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void avatarCanBeSetReplacedAndRemoved() {
        TestUser alice = newUser();
        TestUser bob = newUser();

        String first = setAvatar(alice, PNG).getBody().avatarUrl();
        assertThat(first).startsWith("/api/files/");

        ResponseEntity<byte[]> fetched = fetch(first, bob);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(fetched.getHeaders().getCacheControl()).contains("immutable");
        assertThat(fetched.getBody()).isEqualTo(PNG);
        assertThat(fetch(first, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String second = setAvatar(alice, PNG).getBody().avatarUrl();
        assertThat(second).isNotEqualTo(first);
        assertThat(fetch(first, bob).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(storage.load(fileId(first))).isEmpty();

        UUID convId = direct(bob, alice);
        List<ParticipantResponse> participants =
                call(GET, "/api/conversations/" + convId, bob, null, ConversationResponse.class).getBody().participants();
        assertThat(participants).filteredOn(p -> p.userId().equals(alice.id()))
                .extracting(ParticipantResponse::avatarUrl).containsExactly(second);

        var removed = call(DELETE, "/api/users/me/avatar", alice, null, UserResponse.class);
        assertThat(removed.getBody().avatarUrl()).isNull();
        assertThat(fetch(second, bob).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void onlyReasonableImagesAreAccepted() {
        TestUser alice = newUser();

        assertThat(setAvatar(alice, "<svg onload=alert(1)>".getBytes()).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        byte[] big = Arrays.copyOf(PNG, 2 * 1024 * 1024 + 1);
        assertThat(setAvatar(alice, big).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        TestUser bob = newUser();
        UUID convId = direct(alice, bob);
        assertThat(sendImage(alice, convId, UUID.randomUUID().toString(), "hi".getBytes(), "").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(sendImage(alice, convId, "not valid!", PNG, "").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void imageMessagesAreOnlyVisibleToMembers() {
        TestUser alice = newUser();
        TestUser bob = newUser();
        TestUser carol = newUser();
        UUID convId = direct(alice, bob);
        String clientId = UUID.randomUUID().toString();

        var sent = sendImage(alice, convId, clientId, PNG, " look ");
        assertThat(sent.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        MessageResponse m = sent.getBody();
        assertThat(m.type()).isEqualTo(MessageType.IMAGE);
        assertThat(m.content()).isEqualTo("look");
        assertThat(m.image().width()).isEqualTo(640);
        assertThat(m.image().height()).isEqualTo(480);

        assertThat(fetch(m.image().url(), bob).getBody()).isEqualTo(PNG);
        assertThat(fetch(m.image().url(), carol).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ConversationResponse listed = call(GET, "/api/conversations/" + convId, bob, null, ConversationResponse.class)
                .getBody();
        assertThat(listed.lastMessage().type()).isEqualTo(MessageType.IMAGE);
        assertThat(history(bob, convId).getFirst().image().url()).isEqualTo(m.image().url());

        // a retry returns the same message without storing the file again
        var retry = sendImage(alice, convId, clientId, PNG, "look");
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(retry.getBody().id()).isEqualTo(m.id());
        assertThat(jdbc.queryForObject("""
                select count(*) from files f join users u on u.id = f.owner_id
                where u.public_id = ? and f.purpose = 'MESSAGE'
                """, Long.class, alice.id())).isEqualTo(1L);

        String msgUrl = "/api/conversations/" + convId + "/messages/" + m.id();
        assertThat(call(PATCH, msgUrl, alice, Map.of("content", "edit"), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(call(DELETE, msgUrl + "?scope=everyone", alice, null, Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(fetch(m.image().url(), bob).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(storage.load(fileId(m.image().url()))).isEmpty();
        MessageResponse deleted = history(bob, convId).getFirst();
        assertThat(deleted.deleted()).isTrue();
        assertThat(deleted.image()).isNull();
    }

    @Test
    void removedAndClearingMembersLoseLaterImages() {
        TestUser alice = newUser();
        TestUser bob = newUser();
        UUID groupId = call(POST, "/api/conversations/groups", alice,
                Map.of("name", "g", "memberIds", List.of(bob.id())), ConversationResponse.class).getBody().id();

        String before = sendImage(alice, groupId, UUID.randomUUID().toString(), PNG, "").getBody().image().url();
        assertThat(call(DELETE, "/api/conversations/groups/" + groupId + "/members/" + bob.id(), alice, null,
                Void.class).getStatusCode().is2xxSuccessful()).isTrue();
        String after = sendImage(alice, groupId, UUID.randomUUID().toString(), PNG, "").getBody().image().url();

        assertThat(fetch(before, bob).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetch(after, bob).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(call(POST, "/api/conversations/" + groupId + "/clear", bob, null, Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(fetch(before, bob).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(fetch(before, alice).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<UserResponse> setAvatar(TestUser as, byte[] bytes) {
        return multipart(PUT, "/api/users/me/avatar", as, bytes, Map.of(), UserResponse.class);
    }

    private ResponseEntity<MessageResponse> sendImage(TestUser as, UUID convId, String clientId, byte[] bytes,
                                                      String caption) {
        return multipart(POST, "/api/conversations/" + convId + "/messages/images", as, bytes,
                Map.of("clientMessageId", clientId, "caption", caption, "width", "640", "height", "480"),
                MessageResponse.class);
    }

    private <T> ResponseEntity<T> multipart(HttpMethod method, String url, TestUser as, byte[] bytes,
                                            Map<String, String> fields, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(as.token());
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return "image.png";
            }
        });
        fields.forEach(body::add);
        return rest.exchange(url, method, new HttpEntity<>(body, headers), type);
    }

    private ResponseEntity<byte[]> fetch(String url, TestUser as) {
        HttpHeaders headers = new HttpHeaders();
        if (as != null) {
            headers.setBearerAuth(as.token());
        }
        return rest.exchange(url, GET, new HttpEntity<>(headers), byte[].class);
    }

    private UUID fileId(String url) {
        return UUID.fromString(url.substring(url.lastIndexOf('/') + 1));
    }

    private UUID direct(TestUser a, TestUser b) {
        return call(POST, "/api/conversations/direct", a, Map.of("userId", b.id()), ConversationResponse.class)
                .getBody().id();
    }

    private List<MessageResponse> history(TestUser as, UUID convId) {
        return call(GET, "/api/conversations/" + convId + "/messages", as, null, MessagePage.class).getBody().items();
    }
}
