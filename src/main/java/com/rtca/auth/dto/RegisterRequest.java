package com.rtca.auth.dto;

public record RegisterRequest(
        String username,
        String email,
        String password,
        String displayName
) {
}
