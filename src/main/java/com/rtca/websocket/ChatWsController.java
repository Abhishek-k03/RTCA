package com.rtca.websocket;

import com.rtca.message.MessageService;
import com.rtca.message.SendResult;
import com.rtca.message.dto.SendMessageRequest;
import com.rtca.websocket.ChatEvent.EventType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class ChatWsController {

    private final MessageService messageService;
    private final ChatEventPublisher publisher;

    /** Client sends to /app/conversations.{id}.send and gets an ACK on /user/queue/events. */
    @MessageMapping("/conversations.{conversationId}.send")
    public void send(@DestinationVariable Long conversationId,
                     @Valid @Payload SendMessageRequest request,
                     Principal principal) {
        Long userId = Long.valueOf(principal.getName());
        SendResult result = messageService.send(userId, conversationId, request);

        publisher.toUser(userId, ChatEvent.of(EventType.ACK, Map.of(
                "clientMessageId", request.clientMessageId(),
                "messageId", result.message().id(),
                "duplicate", !result.created()
        )));
    }
}
