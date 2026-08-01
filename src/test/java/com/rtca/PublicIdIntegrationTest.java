package com.rtca;

import com.rtca.conversation.dto.ConversationResponse;
import com.rtca.message.dto.MessageResponse;
import com.rtca.support.IntegrationTest;
import com.rtca.user.dto.UserSummary;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

class PublicIdIntegrationTest extends IntegrationTest {

    @Test
    void clientsOnlySeeRandomIds() {
        TestUser alice = newUser();
        TestUser bob = newUser();

        ConversationResponse conv = call(POST, "/api/conversations/direct", alice, Map.of("userId", bob.id()),
                ConversationResponse.class).getBody();
        MessageResponse msg = call(POST, "/api/conversations/" + conv.id() + "/messages", alice,
                Map.of("clientMessageId", "p1", "content", "hi"), MessageResponse.class).getBody();

        assertThat(conv.id()).isNotNull();
        assertThat(conv.participants()).extracting(p -> p.userId()).containsExactlyInAnyOrder(alice.id(), bob.id());
        assertThat(msg.conversationId()).isEqualTo(conv.id());
        assertThat(msg.senderId()).isEqualTo(alice.id());
    }

    @Test
    void usersCannotBeListedByCounting() {
        TestUser alice = newUser();
        TestUser bob = newUser();

        assertThat(call(GET, "/api/users/1", alice, null, Map.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(GET, "/api/users/" + UUID.randomUUID(), alice, null, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(GET, "/api/users/" + bob.id(), alice, null, UserSummary.class).getBody().username())
                .isEqualTo(bob.username());
    }

    @Test
    void numericAndUnknownConversationIdsGoNowhere() {
        TestUser alice = newUser();

        assertThat(call(GET, "/api/conversations/1", alice, null, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(GET, "/api/conversations/" + UUID.randomUUID(), alice, null, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
