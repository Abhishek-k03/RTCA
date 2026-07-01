package com.rtca.conversation.dto;

import com.rtca.conversation.ConversationParticipant;
import com.rtca.conversation.ParticipantRole;

public record ParticipantResponse(
        Long userId,
        String username,
        String displayName,
        ParticipantRole role
) {
    public static ParticipantResponse from(ConversationParticipant p) {
        return new ParticipantResponse(
                p.getUser().getId(),
                p.getUser().getUsername(),
                p.getUser().getDisplayName(),
                p.getRole()
        );
    }
}
