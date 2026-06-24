package com.rtca.auth;

import com.rtca.user.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.Principal;
import java.util.Collection;
import java.util.List;

/**
 * Authenticated principal built from the jwt. getName() returns the user id,
 * which is also what STOMP user destinations resolve to.
 */
public record AuthUser(Long id, String username, Role role) implements Principal {

    @Override
    public String getName() {
        return String.valueOf(id);
    }

    public Collection<? extends GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
}
