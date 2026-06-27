package com.rtca.user.dto;

import com.rtca.user.User;

public record UserSummary(Long id, String username, String displayName) {

    public static UserSummary from(User user) {
        return new UserSummary(user.getId(), user.getUsername(), user.getDisplayName());
    }
}
