package com.rtca.auth;

import com.rtca.auth.dto.LoginRequest;
import com.rtca.auth.dto.RegisterRequest;
import com.rtca.auth.jwt.JwtService;
import com.rtca.auth.refresh.RefreshTokenService;
import com.rtca.common.exception.ConflictException;
import com.rtca.common.exception.UnauthorizedException;
import com.rtca.user.User;
import com.rtca.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuthenticationManager authenticationManager;
    @Mock JwtService jwtService;
    @Mock RefreshTokenService refreshTokens;

    @InjectMocks AuthService authService;

    @Test
    void registerNormalizesAndHashes() {
        when(passwordEncoder.encode("password1")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.register(new RegisterRequest("  Alice ", "Alice@X.com", "password1", null));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("alice");
        assertThat(saved.getEmail()).isEqualTo("alice@x.com");
        assertThat(saved.getPasswordHash()).isEqualTo("hashed");
        assertThat(saved.getDisplayName()).isEqualTo("alice");
    }

    @Test
    void registerRejectsTakenUsername() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("alice", "a@x.com", "password1", null)))
                .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginReturnsToken() {
        User user = User.builder().username("alice").email("a@x.com").passwordHash("h").build();
        when(authenticationManager.authenticate(any()))
                .thenReturn(new UsernamePasswordAuthenticationToken("alice", null, List.of()));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(user)).thenReturn("token");
        when(jwtService.getAccessTokenTtlSeconds()).thenReturn(3600L);
        when(refreshTokens.issue(any())).thenReturn("refresh");

        var session = authService.login(new LoginRequest("alice", "password1"));

        assertThat(session.response().accessToken()).isEqualTo("token");
        assertThat(session.response().tokenType()).isEqualTo("Bearer");
        assertThat(session.refreshToken()).isEqualTo("refresh");
    }

    @Test
    void loginWithBadCredentialsFails() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "nope")))
                .isInstanceOf(UnauthorizedException.class);
    }
}
