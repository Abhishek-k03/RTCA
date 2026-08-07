package com.rtca.conversation;

import java.util.UUID;

/** Name or photo of a group changed. */
public record GroupUpdatedEvent(Long conversationId, String name, UUID avatarId) {
}
