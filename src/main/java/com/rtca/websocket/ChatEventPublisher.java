package com.rtca.websocket;

import com.rtca.message.MessageCreatedEvent;
import com.rtca.websocket.ChatEvent.EventType;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ChatEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    // after commit so clients never see a message that was rolled back
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageCreated(MessageCreatedEvent event) {
        toConversation(event.message().conversationId(), ChatEvent.of(EventType.MESSAGE, event.message()));
    }

    public void toConversation(Long conversationId, ChatEvent event) {
        messagingTemplate.convertAndSend(Destinations.conversation(conversationId), event);
    }

    public void toUser(Long userId, ChatEvent event) {
        messagingTemplate.convertAndSendToUser(String.valueOf(userId), Destinations.USER_EVENTS, event);
    }
}
