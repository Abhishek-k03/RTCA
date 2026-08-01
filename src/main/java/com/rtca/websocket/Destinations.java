package com.rtca.websocket;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public final class Destinations {

    public static final String CONVERSATION_PREFIX = "/topic/conversations.";
    public static final String PRESENCE_PREFIX = "/topic/presence.";
    public static final String USER_EVENTS = "/queue/events";

    private static final String UUID_PATTERN = "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})";
    private static final Pattern CONVERSATION = Pattern.compile("^/topic/conversations\\." + UUID_PATTERN + "$");
    private static final Pattern PRESENCE = Pattern.compile("^/topic/presence\\." + UUID_PATTERN + "$");

    private Destinations() {
    }

    public static String conversation(UUID id) {
        return CONVERSATION_PREFIX + id;
    }

    public static String presence(UUID userId) {
        return PRESENCE_PREFIX + userId;
    }

    public static Optional<UUID> conversationId(String destination) {
        return extract(CONVERSATION, destination);
    }

    public static boolean isPresence(String destination) {
        return extract(PRESENCE, destination).isPresent();
    }

    private static Optional<UUID> extract(Pattern pattern, String destination) {
        if (destination == null) {
            return Optional.empty();
        }
        var m = pattern.matcher(destination);
        return m.matches() ? Optional.of(UUID.fromString(m.group(1))) : Optional.empty();
    }
}
