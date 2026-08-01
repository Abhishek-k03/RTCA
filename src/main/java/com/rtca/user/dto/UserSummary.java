package com.rtca.user.dto;

import com.rtca.user.User;
import java.util.UUID;

public record UserSummary(UUID id, String username, String displayName) {

    public static UserSummary from(User user) {
        return new UserSummary(user.getPublicId(), user.getUsername(), user.getDisplayName());
    }
}
