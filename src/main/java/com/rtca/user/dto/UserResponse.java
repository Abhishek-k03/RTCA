package com.rtca.user.dto;

import com.rtca.user.Role;
import com.rtca.user.User;

import java.time.Instant;

public record UserResponse(
        Long id,
        String username,
        String email,
        String displayName,
        Role role,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole(),
                user.getCreatedAt()
        );
    }
}
