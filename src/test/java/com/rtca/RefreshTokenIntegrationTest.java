package com.rtca;

import com.rtca.auth.dto.AuthResponse;
import com.rtca.support.IntegrationTest;
import com.rtca.user.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

class RefreshTokenIntegrationTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void loginSetsARefreshCookieThatOnlyTheAuthEndpointsGet() {
        TestUser alice = newUser();
        ResponseEntity<AuthResponse> login = login(alice);

        String header = login.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(header).contains("refresh_token=", "HttpOnly", "SameSite=Strict", "Path=/api/auth", "Secure");
        assertThat(login.getBody().accessToken()).isNotBlank();
    }

    @Test
    void refreshRotatesTheTokenAndGivesAWorkingAccessToken() {
        TestUser alice = newUser();
        String first = cookieOf(login(alice));

        ResponseEntity<AuthResponse> refreshed = refresh(first);
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        String second = cookieOf(refreshed);
        assertThat(second).isNotEqualTo(first);

        HttpHeaders auth = new HttpHeaders();
        auth.setBearerAuth(refreshed.getBody().accessToken());
        var me = rest.exchange("/api/users/me", GET, new HttpEntity<>(auth), UserResponse.class);
        assertThat(me.getBody().username()).isEqualTo(alice.username());

        assertThat(refresh(second).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void twoTabsRefreshingAtOnceBothSucceed() {
        String token = cookieOf(login(newUser()));

        assertThat(refresh(token).getStatusCode()).isEqualTo(HttpStatus.OK);
        // the other tab still sent the old cookie a moment later
        ResponseEntity<AuthResponse> late = refresh(token);
        assertThat(late.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refresh(cookieOf(late)).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void reusingAnOldTokenRevokesTheWholeSession() {
        TestUser alice = newUser();
        String stolen = cookieOf(login(alice));
        String current = cookieOf(refresh(stolen));
        jdbc.update("update refresh_tokens set replaced_at = now() - interval '5 minutes' where token_hash = ?",
                hash(stolen));

        assertThat(refresh(stolen).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(refresh(current).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // other sessions of the same user are untouched
        assertThat(refresh(cookieOf(login(alice))).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void logoutEndsTheSessionAndClearsTheCookie() {
        String token = cookieOf(login(newUser()));

        HttpHeaders headers = cookieHeader(token);
        ResponseEntity<Void> logout = rest.exchange("/api/auth/logout", POST, new HttpEntity<>(headers), Void.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(logout.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("refresh_token=", "Max-Age=0");

        assertThat(refresh(token).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void missingUnknownOrExpiredTokensAreRejected() {
        assertThat(rest.exchange("/api/auth/refresh", POST, HttpEntity.EMPTY, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(refresh("made-up").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String token = cookieOf(login(newUser()));
        jdbc.update("update refresh_tokens set expires_at = now() - interval '1 minute' where token_hash = ?",
                hash(token));
        assertThat(refresh(token).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ResponseEntity<AuthResponse> login(TestUser user) {
        return rest.postForEntity("/api/auth/login",
                Map.of("login", user.username(), "password", "password123"), AuthResponse.class);
    }

    private ResponseEntity<AuthResponse> refresh(String token) {
        return rest.exchange("/api/auth/refresh", POST, new HttpEntity<>(cookieHeader(token)), AuthResponse.class);
    }

    private HttpHeaders cookieHeader(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "refresh_token=" + token);
        return headers;
    }

    private String cookieOf(ResponseEntity<?> response) {
        String header = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(header).as("Set-Cookie").isNotNull();
        return header.substring("refresh_token=".length(), header.indexOf(';'));
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
