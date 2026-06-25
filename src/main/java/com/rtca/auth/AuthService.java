package com.rtca.auth;

import com.rtca.auth.dto.AuthResponse;
import com.rtca.auth.dto.LoginRequest;
import com.rtca.auth.dto.RegisterRequest;
import com.rtca.auth.jwt.JwtService;
import com.rtca.common.exception.ConflictException;
import com.rtca.common.exception.UnauthorizedException;
import com.rtca.user.User;
import com.rtca.user.UserRepository;
import com.rtca.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String username = request.username().trim().toLowerCase();
        String email = request.email().trim().toLowerCase();

        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("Username already taken");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email already registered");
        }

        User user = User.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .displayName(request.displayName() != null ? request.displayName() : username)
                .build();

        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String username;
        try {
            username = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.login(), request.password())).getName();
        } catch (AuthenticationException e) {
            throw new UnauthorizedException("Invalid credentials");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        return new AuthResponse(
                jwtService.generateAccessToken(user),
                "Bearer",
                jwtService.getAccessTokenTtlSeconds(),
                UserResponse.from(user)
        );
    }
}
