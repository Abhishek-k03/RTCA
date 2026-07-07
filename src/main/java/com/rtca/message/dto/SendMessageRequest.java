package com.rtca.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "must be alphanumeric, '-' or '_'")
        String clientMessageId,

        @NotBlank
        @Size(max = 4000)
        String content
) {
}
