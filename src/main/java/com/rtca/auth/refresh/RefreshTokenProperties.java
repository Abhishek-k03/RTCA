package com.rtca.auth.refresh;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * reuseInterval: how long a just-rotated token still works, so two tabs refreshing
 * at the same moment don't look like a stolen token.
 */
@ConfigurationProperties(prefix = "app.auth.refresh")
public record RefreshTokenProperties(
        Duration ttl,
        Duration reuseInterval,
        boolean cookieSecure
) {
}
