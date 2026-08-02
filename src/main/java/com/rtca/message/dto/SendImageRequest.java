package com.rtca.message.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Form fields sent with the image. width and height let clients reserve space before it loads. */
public record SendImageRequest(
        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "must be alphanumeric, '-' or '_'")
        String clientMessageId,

        @Size(max = 4000)
        String caption,

        @Min(1) @Max(20000) Integer width,
        @Min(1) @Max(20000) Integer height
) {
}
