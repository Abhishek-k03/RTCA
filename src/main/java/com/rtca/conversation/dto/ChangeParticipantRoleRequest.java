package com.rtca.conversation.dto;

import com.rtca.conversation.ParticipantRole;
import jakarta.validation.constraints.NotNull;

public record ChangeParticipantRoleRequest(@NotNull ParticipantRole role) {
}
