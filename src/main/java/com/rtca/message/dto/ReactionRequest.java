package com.rtca.message.dto;

import jakarta.validation.constraints.NotBlank;

public record ReactionRequest(@NotBlank String emoji) {
}
