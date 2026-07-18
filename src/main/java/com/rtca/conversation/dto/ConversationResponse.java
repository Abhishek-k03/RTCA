package com.rtca.conversation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
        Instant lastMessageAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long unreadCount
) {
    public static ConversationResponse from(Conversation c, List<ParticipantResponse> participants) {
        return from(c, participants, null);
    }

    public static ConversationResponse from(Conversation c, List<ParticipantResponse> participants, Long unreadCount) {
        return new ConversationResponse(
                c.getId(),
                c.getType(),
                c.getName(),
                participants,
                c.getCreatedAt(),
                c.getLastMessageAt(),
                unreadCount
        );
    }
}
