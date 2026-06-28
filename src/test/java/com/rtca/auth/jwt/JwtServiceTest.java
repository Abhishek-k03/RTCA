package com.rtca.auth.jwt;

import com.rtca.auth.AuthUser;
import com.rtca.user.Role;
import com.rtca.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "dGVzdC1zZWNyZXQta2V5LXRoYXQtaXMtbG9uZy1lbm91Z2gtZm9yLWhzMjU2";

    private final JwtService jwtService = new JwtService(new JwtProperties(SECRET, Duration.ofMinutes(5), "rtca"));

    @Test
    void generatedTokenAuthenticatesBackToSameUser() {
        User user = User.builder().username("alice").email("a@x.com").passwordHash("x").role(Role.ADMIN).build();
        ReflectionTestUtils.setField(user, "id", 42L);

        String token = jwtService.generateAccessToken(user);

        assertThat(jwtService.authenticate(token))
                .contains(new AuthUser(42L, "alice", Role.ADMIN));
    }

    @Test
    void tamperedTokenIsRejected() {
        User user = User.builder().username("bob").email("b@x.com").passwordHash("x").build();
        ReflectionTestUtils.setField(user, "id", 1L);
        String token = jwtService.generateAccessToken(user);

        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThat(jwtService.authenticate(tampered)).isEmpty();
    }

    @Test
    void tokenFromOtherIssuerIsRejected() {
        var other = new JwtService(new JwtProperties(SECRET, Duration.ofMinutes(5), "someone-else"));
        User user = User.builder().username("bob").email("b@x.com").passwordHash("x").build();
        ReflectionTestUtils.setField(user, "id", 1L);

        assertThat(jwtService.authenticate(other.generateAccessToken(user))).isEmpty();
    }

    @Test
    void garbageIsRejected() {
        assertThat(jwtService.authenticate("not-a-jwt")).isEmpty();
    }
}
