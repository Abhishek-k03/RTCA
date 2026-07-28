package com.rtca.conversation.dto;

import com.rtca.message.Message;

import java.time.Instant;

/** Preview of the newest message the viewer can see. content is null once deleted. */
public record LastMessage(
        Long id,
        Long senderId,
        String senderName,
        String content,
        boolean deleted,
        Instant createdAt
) {
    public static LastMessage from(Message m) {
        String name = m.getSender().getDisplayName() != null ? m.getSender().getDisplayName() : m.getSender().getUsername();
        return new LastMessage(m.getId(), m.getSender().getId(), name,
                m.isDeleted() ? null : m.getContent(), m.isDeleted(), m.getCreatedAt());
    }
}
