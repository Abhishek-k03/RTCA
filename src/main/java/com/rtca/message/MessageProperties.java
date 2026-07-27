package com.rtca.message;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.messages")
public record MessageProperties(
        Duration editWindow,
        Duration deleteWindow
) {
}
