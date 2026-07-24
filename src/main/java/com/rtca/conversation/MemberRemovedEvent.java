package com.rtca.conversation;

import java.time.Instant;

/** Published when a member is removed from or leaves a group. */
public record MemberRemovedEvent(Long conversationId, Long userId, Instant removedAt) {
}
