package com.rtca;

import com.rtca.common.exception.ApiError;
import com.rtca.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PUT;

/** Client mistakes get their proper status in the usual error shape, not a 500. */
class ErrorResponseIntegrationTest extends IntegrationTest {

    @Test
    void unknownPathIs404() {
        ResponseEntity<ApiError> res = call(GET, "/api/nope", newUser(), null, ApiError.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getBody().message()).isEqualTo("Not Found");
        assertThat(res.getBody().path()).isEqualTo("/api/nope");
    }

    @Test
    void wrongMethodIs405WithTheAllowedOnes() {
        ResponseEntity<ApiError> res = call(DELETE, "/api/users/me", newUser(), null, ApiError.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(res.getHeaders().getAllow()).contains(HttpMethod.GET, HttpMethod.PATCH);
    }

    @Test
    void jsonToAnUploadEndpointIs415() {
        ResponseEntity<ApiError> res = call(PUT, "/api/users/me/avatar", newUser(), Map.of("file", "x"), ApiError.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(res.getBody().status()).isEqualTo(415);
    }

    @Test
    void missingQueryParameterIs400() {
        TestUser alice = newUser();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(alice.token());
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        ResponseEntity<ApiError> res = rest.exchange("/api/users/search", GET, new HttpEntity<>(headers), ApiError.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().message()).contains("q");
    }
}
