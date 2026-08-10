package com.rtca.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void realFailuresStay500WithoutDetails() {
        var res = handler.handleUnexpected(new IllegalStateException("db password is hunter2"),
                new MockHttpServletRequest("GET", "/api/users/me"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(res.getBody().message()).isEqualTo("Something went wrong");
    }
}
