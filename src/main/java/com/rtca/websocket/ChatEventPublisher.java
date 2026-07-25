package com.rtca.websocket;

import com.rtca.conversation.MemberAddedEvent;
import com.rtca.conversation.MemberRemovedEvent;
import com.rtca.message.MessageCreatedEvent;
import com.rtca.websocket.ChatEvent.EventType;
import com.rtca.websocket.relay.RedisEventRelay;
import com.rtca.websocket.relay.RelayMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** All outbound events go through the relay so every instance sees them. */
@Component
@RequiredArgsConstructor
public class ChatEventPublisher {

    private final RedisEventRelay relay;

    // after commit so clients never see a message that was rolled back
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageCreated(MessageCreatedEvent event) {
        toConversation(event.message().conversationId(), ChatEvent.of(EventType.MESSAGE, event.message()));
    }

    // drop the removed user's live subscription on every instance, then tell their clients
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberRemoved(MemberRemovedEvent event) {
        relay.publish(new RelayMessage(Destinations.USER_EVENTS, String.valueOf(event.userId()),
                ChatEvent.of(EventType.REMOVED, event), Destinations.conversation(event.conversationId())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberAdded(MemberAddedEvent event) {
        toUser(event.userId(), ChatEvent.of(EventType.ADDED, event));
    }

    public void toConversation(Long conversationId, ChatEvent event) {
        relay.publish(new RelayMessage(Destinations.conversation(conversationId), null, event, null));
    }

    public void presence(Long userId, ChatEvent event) {
        relay.publish(new RelayMessage(Destinations.presence(userId), null, event, null));
    }

    public void toUser(Long userId, ChatEvent event) {
        relay.publish(new RelayMessage(Destinations.USER_EVENTS, String.valueOf(userId), event, null));
    }
}
