package com.rtca.message.dto;

import java.util.List;
import java.util.UUID;

/** Everyone who reacted with one emoji. Clients work out counts and their own pick. */
public record Reaction(String emoji, List<UUID> userIds) {
}
