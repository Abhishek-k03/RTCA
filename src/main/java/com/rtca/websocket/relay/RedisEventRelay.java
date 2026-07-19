package com.rtca.websocket.relay;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Fans events out to every app instance via redis pub/sub. Each instance
 * delivers to its own local STOMP sessions. Falls back to local delivery
 * if redis is unavailable.
 */
@Slf4j
@Component
public class RedisEventRelay implements MessageListener {

    public static final String CHANNEL = "rtca:events";

    private final RedisTemplate<String, Object> redis;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public RedisEventRelay(@Qualifier("jsonRedisTemplate") RedisTemplate<String, Object> redis,
                           SimpMessagingTemplate messagingTemplate,
                           ObjectMapper objectMapper,
                           @Value("${app.relay.enabled:true}") boolean enabled) {
        this.redis = redis;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    public void publish(RelayMessage message) {
        if (!enabled) {
            deliverLocally(message);
            return;
        }
        try {
            redis.convertAndSend(CHANNEL, message);
        } catch (Exception e) {
            log.warn("Redis relay unavailable, delivering locally: {}", e.getMessage());
            deliverLocally(message);
        }
    }

    @Override
    public void onMessage(@NonNull Message message, byte[] pattern) {
        try {
            deliverLocally(objectMapper.readValue(message.getBody(), RelayMessage.class));
        } catch (IOException e) {
            log.error("Dropping malformed relay message", e);
        }
    }

    private void deliverLocally(RelayMessage message) {
        if (message.user() != null) {
            messagingTemplate.convertAndSendToUser(message.user(), message.destination(), message.event());
        } else {
            messagingTemplate.convertAndSend(message.destination(), message.event());
        }
    }
}
