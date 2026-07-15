package com.rtca.presence;

import java.time.Instant;

public record PresenceStatus(Long userId, boolean online, Instant lastSeen) {
}
