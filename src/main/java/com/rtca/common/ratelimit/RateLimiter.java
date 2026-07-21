package com.rtca.common.ratelimit;

import com.rtca.common.exception.TooManyRequestsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/** Fixed window limiter in redis, shared by all instances. Fails open. */
@Slf4j
@Component
public class RateLimiter {

    private final StringRedisTemplate redis;
    private final RedisScript<Long> script;
    private final int messageLimit;
    private final Duration messageWindow;

    public RateLimiter(StringRedisTemplate redis,
                       @Value("${app.rate-limit.messages.limit:20}") int messageLimit,
                       @Value("${app.rate-limit.messages.window:10s}") Duration messageWindow) {
        this.redis = redis;
        this.script = RedisScript.of(new ClassPathResource("redis/rate_limit.lua"), Long.class);
        this.messageLimit = messageLimit;
        this.messageWindow = messageWindow;
    }

    public void checkMessageSend(Long userId) {
        check("rl:msg:" + userId, messageLimit, messageWindow);
    }

    private void check(String key, int limit, Duration window) {
        Long count;
        try {
            count = redis.execute(script, List.of(key), String.valueOf(window.toSeconds()));
        } catch (Exception e) {
            log.warn("Rate limiter unavailable, allowing request: {}", e.getMessage());
            return;
        }
        if (count != null && count > limit) {
            throw new TooManyRequestsException("Too many messages, slow down");
        }
    }
}
