package com.rtca.message;

import com.rtca.message.dto.Reaction;

import java.util.List;

public record ReactionsChangedEvent(Long conversationId, Long messageId, List<Reaction> reactions) {
}
