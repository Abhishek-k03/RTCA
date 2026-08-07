package com.rtca.user;

import com.rtca.user.dto.UserSummary;

public record ProfileUpdatedEvent(Long userId, UserSummary user) {
}
