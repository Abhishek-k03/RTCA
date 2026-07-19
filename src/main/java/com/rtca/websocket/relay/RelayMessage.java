package com.rtca.websocket.relay;

import com.rtca.websocket.ChatEvent;

/** user is set for user destinations (/user/{id}/queue/..), null for topics. */
public record RelayMessage(String destination, String user, ChatEvent event) {
}
