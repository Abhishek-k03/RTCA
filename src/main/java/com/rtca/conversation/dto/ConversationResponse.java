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
        @JsonInclude(JsonInclude.Include.NON_NULL) Long unreadCount,
        // set when the viewer was removed, the conversation is read-only for them
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant removedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) LastMessage lastMessage
) {
    public static ConversationResponse from(Conversation c, List<ParticipantResponse> participants) {
        return from(c, participants, null);
    }

    public static ConversationResponse from(Conversation c, List<ParticipantResponse> participants, Long unreadCount) {
        return from(c, participants, unreadCount, null, null);
    }

    public static ConversationResponse from(Conversation c, List<ParticipantResponse> participants, Long unreadCount,
                                            Instant removedAt, LastMessage lastMessage) {
        return new ConversationResponse(
                c.getId(),
                c.getType(),
                c.getName(),
                participants,
                c.getCreatedAt(),
                // removed viewers don't get to see later activity
                removedAt != null ? removedAt : c.getLastMessageAt(),
                unreadCount,
                removedAt,
                lastMessage
        );
    }
}
