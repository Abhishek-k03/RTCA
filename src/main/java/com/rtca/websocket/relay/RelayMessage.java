package com.rtca.websocket.relay;

import com.rtca.websocket.ChatEvent;

/**
 * user is set for user destinations (/user/{id}/queue/..), null for topics.
 * unsubscribe, when set, is a topic the user's sessions are dropped from before delivery.
 */
public record RelayMessage(String destination, String user, ChatEvent event, String unsubscribe) {
}
