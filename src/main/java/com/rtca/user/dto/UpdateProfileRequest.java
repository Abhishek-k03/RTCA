package com.rtca.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** bio: null leaves it as is, blank clears it. */
public record UpdateProfileRequest(
        @NotBlank @Size(max = 64) String displayName,
        @Size(max = 200) String bio
) {
}
