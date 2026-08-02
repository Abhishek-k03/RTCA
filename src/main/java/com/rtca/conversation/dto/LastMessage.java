package com.rtca.conversation.dto;

import com.rtca.message.Message;
import com.rtca.message.MessageType;

import java.time.Instant;
import java.util.UUID;

/** Preview of the newest message the viewer can see. content is null once deleted. */
public record LastMessage(
        Long id,
        UUID senderId,
        String senderName,
        String content,
        MessageType type,
        boolean deleted,
        Instant createdAt
) {
    public static LastMessage from(Message m) {
        String name = m.getSender().getDisplayName() != null ? m.getSender().getDisplayName() : m.getSender().getUsername();
        return new LastMessage(m.getId(), m.getSender().getPublicId(), name,
                m.isDeleted() ? null : m.getContent(), m.getType(), m.isDeleted(), m.getCreatedAt());
    }
}
