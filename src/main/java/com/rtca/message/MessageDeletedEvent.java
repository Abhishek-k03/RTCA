package com.rtca.message;

/** A message deleted for everyone. */
public record MessageDeletedEvent(Long conversationId, Long messageId) {
}
