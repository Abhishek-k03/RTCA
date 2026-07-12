package com.rtca.websocket;

import java.util.OptionalLong;
import java.util.regex.Pattern;

public final class Destinations {

    public static final String CONVERSATION_PREFIX = "/topic/conversations.";
    public static final String USER_EVENTS = "/queue/events";

    private static final Pattern CONVERSATION = Pattern.compile("^/topic/conversations\\.(\\d+)$");

    private Destinations() {
    }

    public static String conversation(Long id) {
        return CONVERSATION_PREFIX + id;
    }

    public static OptionalLong conversationId(String destination) {
        if (destination == null) {
            return OptionalLong.empty();
        }
        var m = CONVERSATION.matcher(destination);
        return m.matches() ? OptionalLong.of(Long.parseLong(m.group(1))) : OptionalLong.empty();
    }
}
