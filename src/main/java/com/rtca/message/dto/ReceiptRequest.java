package com.rtca.message.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ReceiptRequest(@NotNull @Positive Long messageId) {
}
