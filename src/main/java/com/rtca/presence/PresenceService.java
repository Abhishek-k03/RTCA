package com.rtca.presence;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Presence in Redis. Each user has a zset of live sessions scored by expiry,
 * so multiple tabs/instances work and sessions of a crashed node age out.
 */
@Slf4j
@Service
public class PresenceService {

    static final Duration SESSION_TTL = Duration.ofSeconds(90);
    private static final String SESSIONS_KEY = "presence:sessions:";
    private static final String LAST_SEEN_KEY = "presence:lastseen:";

    private final StringRedisTemplate redis;
    private final RedisScript<Long> connectScript;
    private final RedisScript<Long> disconnectScript;

    private final String instanceId = UUID.randomUUID().toString().substring(0, 8);
    /** sessionId -> userId, for this instance only */
    private final Map<String, Long> localSessions = new ConcurrentHashMap<>();

    public PresenceService(StringRedisTemplate redis) {
        this.redis = redis;
        this.connectScript = RedisScript.of(new ClassPathResource("redis/presence_connect.lua"), Long.class);
        this.disconnectScript = RedisScript.of(new ClassPathResource("redis/presence_disconnect.lua"), Long.class);
    }

    /** @return true if this is the user's first live session */
    public boolean connected(Long userId, String sessionId) {
        localSessions.put(sessionId, userId);
        long now = Instant.now().toEpochMilli();
        Long count = redis.execute(connectScript, List.of(SESSIONS_KEY + userId),
                member(sessionId),
                String.valueOf(now + SESSION_TTL.toMillis()),
                String.valueOf(now),
                String.valueOf(SESSION_TTL.toSeconds() * 2));
        return count != null && count == 1;
    }

    /** @return true if the user has no live sessions left */
    public boolean disconnected(Long userId, String sessionId) {
        localSessions.remove(sessionId);
        Long remaining = redis.execute(disconnectScript, List.of(SESSIONS_KEY + userId),
                member(sessionId), String.valueOf(Instant.now().toEpochMilli()));
        if (remaining != null && remaining == 0) {
            redis.opsForValue().set(LAST_SEEN_KEY + userId, Instant.now().toString());
            return true;
        }
        return false;
    }

    public boolean isOnline(Long userId) {
        Long count = redis.opsForZSet().count(SESSIONS_KEY + userId,
                Instant.now().toEpochMilli(), Double.POSITIVE_INFINITY);
        return count != null && count > 0;
    }

    public Map<Long, PresenceStatus> statuses(Collection<Long> userIds) {
        Map<Long, PresenceStatus> result = new LinkedHashMap<>();
        for (Long id : userIds) {
            boolean online = isOnline(id);
            String lastSeen = online ? null : redis.opsForValue().get(LAST_SEEN_KEY + id);
            result.put(id, new PresenceStatus(id, online, lastSeen == null ? null : Instant.parse(lastSeen)));
        }
        return result;
    }

    // keep local sessions alive, well within SESSION_TTL
    @Scheduled(fixedDelay = 30_000)
    void heartbeat() {
        if (localSessions.isEmpty()) {
            return;
        }
        double expiresAt = Instant.now().plus(SESSION_TTL).toEpochMilli();
        try {
            localSessions.forEach((sessionId, userId) ->
                    redis.opsForZSet().add(SESSIONS_KEY + userId, member(sessionId), expiresAt));
        } catch (Exception e) {
            log.warn("Presence heartbeat failed: {}", e.getMessage());
        }
    }

    private String member(String sessionId) {
        return instanceId + ":" + sessionId;
    }
}
