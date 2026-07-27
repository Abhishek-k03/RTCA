package com.rtca.message;

import com.rtca.message.dto.MessageResponse;

public record MessageEditedEvent(MessageResponse message) {
}
