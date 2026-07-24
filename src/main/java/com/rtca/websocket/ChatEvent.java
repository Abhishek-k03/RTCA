package com.rtca.websocket;

/** Envelope for everything pushed to clients over STOMP. */
public record ChatEvent(EventType type, Object payload) {

    public enum EventType {
        MESSAGE,
        ACK,
        PRESENCE,
        TYPING,
        DELIVERED,
        READ,
        REMOVED
    }

    public static ChatEvent of(EventType type, Object payload) {
        return new ChatEvent(type, payload);
    }
}
