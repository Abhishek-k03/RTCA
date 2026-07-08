package com.rtca.message;

import com.rtca.message.dto.MessageResponse;

/** created=false means the request was a retry of an already stored message. */
public record SendResult(MessageResponse message, boolean created) {
}
