package com.rtca.websocket;

import java.time.Instant;

public record WsError(int status, String message, Instant timestamp) {

    public static WsError of(int status, String message) {
        return new WsError(status, message, Instant.now());
    }
}
