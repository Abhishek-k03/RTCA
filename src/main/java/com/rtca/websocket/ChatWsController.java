package com.rtca.websocket;

import com.rtca.auth.AuthUser;
import com.rtca.message.MessageService;
import com.rtca.message.ReceiptService;
import com.rtca.message.SendResult;
import com.rtca.message.dto.ReceiptRequest;
import com.rtca.message.dto.SendMessageRequest;
import com.rtca.presence.TypingService;
import com.rtca.websocket.ChatEvent.EventType;
import com.rtca.websocket.dto.TypingRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class ChatWsController {

    private final MessageService messageService;
    private final TypingService typingService;
    private final ReceiptService receiptService;
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

    @MessageMapping("/conversations.{conversationId}.typing")
    public void typing(@DestinationVariable Long conversationId,
                       @Payload TypingRequest request,
                       Principal principal) {
        AuthUser user = authUser(principal);
        typingService.typing(conversationId, user.id(), user.username(), request.typing());
    }

    @MessageMapping("/conversations.{conversationId}.delivered")
    public void delivered(@DestinationVariable Long conversationId,
                          @Valid @Payload ReceiptRequest request,
                          Principal principal) {
        receiptService.markDelivered(Long.valueOf(principal.getName()), conversationId, request.messageId());
    }

    @MessageMapping("/conversations.{conversationId}.read")
    public void read(@DestinationVariable Long conversationId,
                     @Valid @Payload ReceiptRequest request,
                     Principal principal) {
        receiptService.markRead(Long.valueOf(principal.getName()), conversationId, request.messageId());
    }

    private AuthUser authUser(Principal principal) {
        return (AuthUser) ((Authentication) principal).getPrincipal();
    }
}
