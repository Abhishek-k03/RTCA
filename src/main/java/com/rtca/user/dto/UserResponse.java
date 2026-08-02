package com.rtca.user.dto;

import com.rtca.file.FileUrls;
import com.rtca.user.Role;
import com.rtca.user.User;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String email,
        String displayName,
        String bio,
        String avatarUrl,
        Role role,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getPublicId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getBio(),
                FileUrls.of(user.getAvatarId()),
                user.getRole(),
                user.getCreatedAt()
        );
    }
}
