package com.rtca.conversation.dto;

import jakarta.validation.constraints.NotNull;

public record CreateDirectRequest(@NotNull Long userId) {
}
