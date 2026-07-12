package com.rtca.message;

import com.rtca.message.dto.MessageResponse;

public record MessageCreatedEvent(MessageResponse message) {
}
