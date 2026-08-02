package com.rtca.user.dto;

import com.rtca.file.FileUrls;
import com.rtca.user.User;
import java.util.UUID;

public record UserSummary(UUID id, String username, String displayName, String bio, String avatarUrl) {

    public static UserSummary from(User user) {
        return new UserSummary(user.getPublicId(), user.getUsername(), user.getDisplayName(),
                user.getBio(), FileUrls.of(user.getAvatarId()));
    }
}
