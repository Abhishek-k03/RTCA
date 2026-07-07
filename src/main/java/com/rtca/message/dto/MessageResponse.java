package com.rtca.message.dto;

import com.rtca.message.Message;
import com.rtca.message.MessageType;

import java.time.Instant;

public record MessageResponse(
        Long id,
        Long conversationId,
        Long senderId,
        String senderUsername,
        String content,
        MessageType type,
        String clientMessageId,
        Instant createdAt
) {
    public static MessageResponse from(Message m) {
        return new MessageResponse(
                m.getId(),
                m.getConversationId(),
                m.getSender().getId(),
                m.getSender().getUsername(),
                m.getContent(),
                m.getType(),
                m.getClientMessageId(),
                m.getCreatedAt()
        );
    }
}
