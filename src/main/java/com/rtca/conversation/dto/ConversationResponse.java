package com.rtca.conversation.dto;

import com.rtca.conversation.Conversation;
import com.rtca.conversation.ConversationType;

import java.time.Instant;
import java.util.List;

public record ConversationResponse(
        Long id,
        ConversationType type,
        String name,
        List<ParticipantResponse> participants,
        Instant createdAt,
        Instant lastMessageAt
) {
    public static ConversationResponse from(Conversation c, List<ParticipantResponse> participants) {
        return new ConversationResponse(
                c.getId(),
                c.getType(),
                c.getName(),
                participants,
                c.getCreatedAt(),
                c.getLastMessageAt()
        );
    }
}
