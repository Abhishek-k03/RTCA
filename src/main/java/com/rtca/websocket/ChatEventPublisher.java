package com.rtca.websocket;

import com.rtca.common.ids.PublicIds;
import com.rtca.conversation.MemberAddedEvent;
import com.rtca.conversation.MemberRemovedEvent;
import com.rtca.message.MessageCreatedEvent;
import com.rtca.message.MessageDeletedEvent;
import com.rtca.message.MessageEditedEvent;
import com.rtca.websocket.ChatEvent.EventType;
import com.rtca.websocket.relay.RedisEventRelay;
import com.rtca.websocket.relay.RelayMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.UUID;

/** All outbound events go through the relay so every instance sees them. Clients only see public ids. */
@Component
@RequiredArgsConstructor
public class ChatEventPublisher {

    private final RedisEventRelay relay;
    private final PublicIds ids;

    // after commit so clients never see a message that was rolled back
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageCreated(MessageCreatedEvent event) {
        toConversation(event.message().conversationId(), ChatEvent.of(EventType.MESSAGE, event.message()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageEdited(MessageEditedEvent event) {
        toConversation(event.message().conversationId(), ChatEvent.of(EventType.EDITED, event.message()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageDeleted(MessageDeletedEvent event) {
        UUID conversation = ids.conversation(event.conversationId());
        toConversation(conversation, ChatEvent.of(EventType.DELETED, new Deleted(conversation, event.messageId())));
    }

    // drop the removed user's live subscription on every instance, then tell their clients
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberRemoved(MemberRemovedEvent event) {
        UUID conversation = ids.conversation(event.conversationId());
        relay.publish(new RelayMessage(Destinations.USER_EVENTS, String.valueOf(event.userId()),
                ChatEvent.of(EventType.REMOVED, new Removed(conversation, event.removedAt())),
                Destinations.conversation(conversation)));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberAdded(MemberAddedEvent event) {
        toUser(event.userId(), ChatEvent.of(EventType.ADDED, new Added(ids.conversation(event.conversationId()))));
    }

    public void toConversation(Long conversationId, ChatEvent event) {
        toConversation(ids.conversation(conversationId), event);
    }

    public void toConversation(UUID conversationId, ChatEvent event) {
        relay.publish(new RelayMessage(Destinations.conversation(conversationId), null, event, null));
    }

    public void presence(Long userId, ChatEvent event) {
        relay.publish(new RelayMessage(Destinations.presence(ids.user(userId)), null, event, null));
    }

    // user destinations are routed by the internal id, which is the session principal name
    public void toUser(Long userId, ChatEvent event) {
        relay.publish(new RelayMessage(Destinations.USER_EVENTS, String.valueOf(userId), event, null));
    }

    public record Deleted(UUID conversationId, Long messageId) {
    }

    public record Removed(UUID conversationId, Instant removedAt) {
    }

    public record Added(UUID conversationId) {
    }
}
