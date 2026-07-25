package com.rtca.conversation;

/** Published when a user is added (or re-added) to a group. */
public record MemberAddedEvent(Long conversationId, Long userId) {
}
