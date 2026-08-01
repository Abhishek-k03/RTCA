package com.rtca.conversation.dto;

import com.rtca.conversation.ConversationParticipant;
import com.rtca.conversation.ParticipantRole;
import java.util.UUID;

public record ParticipantResponse(
        UUID userId,
        String username,
        String displayName,
        ParticipantRole role,
        Long lastDeliveredMessageId,
        Long lastReadMessageId
) {
    public static ParticipantResponse from(ConversationParticipant p) {
        return new ParticipantResponse(
                p.getUser().getPublicId(),
                p.getUser().getUsername(),
                p.getUser().getDisplayName(),
                p.getRole(),
                p.getLastDeliveredMessageId(),
                p.getLastReadMessageId()
        );
    }
}
