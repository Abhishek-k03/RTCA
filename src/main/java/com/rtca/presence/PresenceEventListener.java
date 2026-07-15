package com.rtca.presence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

/** Presence is best effort, a redis failure must not break the socket. */
@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceEventListener {

    private final PresenceService presenceService;

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        Principal user = event.getUser();
        String sessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
        if (user == null || sessionId == null) {
            return;
        }
        Long userId = Long.valueOf(user.getName());
        try {
            if (presenceService.connected(userId, sessionId)) {
                log.debug("User {} is online", userId);
            }
        } catch (Exception e) {
            log.warn("Failed to record connect for user {}: {}", userId, e.getMessage());
        }
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        Principal user = event.getUser();
        if (user == null) {
            return;
        }
        Long userId = Long.valueOf(user.getName());
        try {
            if (presenceService.disconnected(userId, event.getSessionId())) {
                log.debug("User {} is offline", userId);
            }
        } catch (Exception e) {
            log.warn("Failed to record disconnect for user {}: {}", userId, e.getMessage());
        }
    }
}
