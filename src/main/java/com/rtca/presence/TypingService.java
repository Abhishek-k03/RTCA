package com.rtca.presence;

import com.rtca.conversation.MembershipService;
import com.rtca.websocket.ChatEvent;
import com.rtca.websocket.ChatEvent.EventType;
import com.rtca.websocket.ChatEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/** Typing events are ephemeral, never stored. Throttled per user+conversation. */
@Service
@RequiredArgsConstructor
public class TypingService {

    static final Duration THROTTLE = Duration.ofSeconds(2);
    private static final String KEY = "typing:";

    private final StringRedisTemplate redis;
    private final MembershipService membershipService;
    private final ChatEventPublisher publisher;

    public void typing(Long conversationId, Long userId, String username, boolean typing) {
        membershipService.requireMember(conversationId, userId);
        String key = KEY + conversationId + ":" + userId;

        if (typing) {
            Boolean first = redis.opsForValue().setIfAbsent(key, "1", THROTTLE);
            if (!Boolean.TRUE.equals(first)) {
                return;
            }
        } else {
            redis.delete(key);
        }

        publisher.toConversation(conversationId, ChatEvent.of(EventType.TYPING,
                new TypingEvent(conversationId, userId, username, typing)));
    }

    public record TypingEvent(Long conversationId, Long userId, String username, boolean typing) {
    }
}
