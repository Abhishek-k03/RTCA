package com.rtca;

import com.rtca.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;

class UserSearchIntegrationTest extends IntegrationTest {

    @Test
    void underscoresAndPercentSignsAreMatchedLiterally() {
        String prefix = "q" + UUID.randomUUID().toString().substring(0, 6);
        register(prefix + "_x");
        register(prefix + "zx");
        TestUser me = newUser();

        assertThat(usernames(me, prefix)).containsExactlyInAnyOrder(prefix + "_x", prefix + "zx");
        // '_' is a single-character wildcard in LIKE, it must not match the 'z'
        assertThat(usernames(me, prefix + "_")).containsExactly(prefix + "_x");
        assertThat(usernames(me, "%")).isEmpty();
        assertThat(usernames(me, "\\")).isEmpty();
    }

    @Test
    void blankQueryIsRejected() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(newUser().token());
        assertThat(rest.exchange("/api/users/search?q={q}", GET, new HttpEntity<>(headers), Map.class, "  ")
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private void register(String username) {
        rest.postForEntity("/api/auth/register",
                Map.of("username", username, "email", username + "@test.com", "password", "password123"), Map.class);
    }

    @SuppressWarnings("unchecked")
    private List<String> usernames(TestUser as, String q) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(as.token());
        // as a uri variable, so the client encodes it exactly once
        Map<String, Object> page = rest.exchange("/api/users/search?q={q}", GET, new HttpEntity<>(headers), Map.class, q)
                .getBody();
        return ((List<Map<String, Object>>) page.get("content")).stream()
                .map(u -> (String) u.get("username"))
                .toList();
    }
}
