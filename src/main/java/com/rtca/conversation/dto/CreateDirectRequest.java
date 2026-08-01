package com.rtca.conversation.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateDirectRequest(@NotNull UUID userId) {
}
