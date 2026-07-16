package com.rtca.websocket;

import java.util.OptionalLong;
import java.util.regex.Pattern;

public final class Destinations {

    public static final String CONVERSATION_PREFIX = "/topic/conversations.";
    public static final String PRESENCE_PREFIX = "/topic/presence.";
    public static final String USER_EVENTS = "/queue/events";

    private static final Pattern CONVERSATION = Pattern.compile("^/topic/conversations\\.(\\d+)$");
    private static final Pattern PRESENCE = Pattern.compile("^/topic/presence\\.(\\d+)$");

    private Destinations() {
    }

    public static String conversation(Long id) {
        return CONVERSATION_PREFIX + id;
    }

    public static String presence(Long userId) {
        return PRESENCE_PREFIX + userId;
    }

    public static OptionalLong conversationId(String destination) {
        return extract(CONVERSATION, destination);
    }

    public static boolean isPresence(String destination) {
        return extract(PRESENCE, destination).isPresent();
    }

    private static OptionalLong extract(Pattern pattern, String destination) {
        if (destination == null) {
            return OptionalLong.empty();
        }
        var m = pattern.matcher(destination);
        return m.matches() ? OptionalLong.of(Long.parseLong(m.group(1))) : OptionalLong.empty();
    }
}
