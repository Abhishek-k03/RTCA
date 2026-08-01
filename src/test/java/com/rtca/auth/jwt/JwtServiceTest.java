package com.rtca.auth.jwt;

import com.rtca.auth.AuthUser;
import com.rtca.common.ids.PublicIds;
import com.rtca.user.Role;
import com.rtca.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtServiceTest {

    private static final String SECRET = "dGVzdC1zZWNyZXQta2V5LXRoYXQtaXMtbG9uZy1lbm91Z2gtZm9yLWhzMjU2";

    private final PublicIds ids = mock(PublicIds.class);
    private final JwtService jwtService = new JwtService(new JwtProperties(SECRET, Duration.ofMinutes(5), "rtca"), ids);

    @Test
    void generatedTokenAuthenticatesBackToSameUser() {
        User user = User.builder().username("alice").email("a@x.com").passwordHash("x").role(Role.ADMIN).build();
        ReflectionTestUtils.setField(user, "id", 42L);
        when(ids.findUserId(user.getPublicId())).thenReturn(Optional.of(42L));

        String token = jwtService.generateAccessToken(user);

        assertThat(jwtService.authenticate(token))
                .contains(new AuthUser(42L, "alice", Role.ADMIN));
    }

    @Test
    void subjectIsThePublicIdNotTheInternalId() {
        User user = User.builder().username("alice").email("a@x.com").passwordHash("x").build();
        ReflectionTestUtils.setField(user, "id", 42L);

        String subject = jwtService.parse(jwtService.generateAccessToken(user)).orElseThrow().getSubject();

        assertThat(subject).isEqualTo(user.getPublicId().toString());
    }

    @Test
    void tokenForUnknownUserIsRejected() {
        User user = User.builder().username("ghost").email("g@x.com").passwordHash("x").build();
        when(ids.findUserId(any())).thenReturn(Optional.empty());

        assertThat(jwtService.authenticate(jwtService.generateAccessToken(user))).isEmpty();
    }

    @Test
    void tamperedTokenIsRejected() {
        User user = User.builder().username("bob").email("b@x.com").passwordHash("x").build();
        when(ids.findUserId(user.getPublicId())).thenReturn(Optional.of(1L));
        String token = jwtService.generateAccessToken(user);
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThat(jwtService.authenticate(tampered)).isEmpty();
    }

    @Test
    void tokenFromOtherIssuerIsRejected() {
        var other = new JwtService(new JwtProperties(SECRET, Duration.ofMinutes(5), "someone-else"), ids);
        User user = User.builder().username("bob").email("b@x.com").passwordHash("x").build();
        when(ids.findUserId(user.getPublicId())).thenReturn(Optional.of(1L));

        assertThat(jwtService.authenticate(other.generateAccessToken(user))).isEmpty();
    }

    @Test
    void garbageIsRejected() {
        assertThat(jwtService.authenticate("not-a-jwt")).isEmpty();
    }
}
