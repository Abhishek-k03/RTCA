package com.rtca.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record CreateGroupRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @Size(min = 1, max = 255) Set<@NotNull UUID> memberIds
) {
}
