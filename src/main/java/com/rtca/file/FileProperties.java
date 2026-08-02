package com.rtca.file;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "app.files")
public record FileProperties(
        Path dir,
        DataSize maxImageSize,
        DataSize maxAvatarSize
) {
}
