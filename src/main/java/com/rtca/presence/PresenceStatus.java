package com.rtca.presence;

import java.time.Instant;
import java.util.UUID;

public record PresenceStatus(UUID userId, boolean online, Instant lastSeen) {
}
