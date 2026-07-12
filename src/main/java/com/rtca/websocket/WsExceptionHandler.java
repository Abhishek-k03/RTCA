package com.rtca.websocket;

import com.rtca.common.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;

import java.util.stream.Collectors;

/** Errors from @MessageMapping handlers go back to the sender only. */
@Slf4j
@ControllerAdvice
public class WsExceptionHandler {

    private static final String ERRORS = "/queue/errors";

    @MessageExceptionHandler(ApiException.class)
    @SendToUser(destinations = ERRORS, broadcast = false)
    public WsError handleApi(ApiException ex) {
        return WsError.of(ex.getStatus().value(), ex.getMessage());
    }

    @MessageExceptionHandler(MethodArgumentNotValidException.class)
    @SendToUser(destinations = ERRORS, broadcast = false)
    public WsError handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult() == null ? "Invalid payload"
                : ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return WsError.of(400, details);
    }

    @MessageExceptionHandler(MessageConversionException.class)
    @SendToUser(destinations = ERRORS, broadcast = false)
    public WsError handleConversion(MessageConversionException ex) {
        return WsError.of(400, "Malformed payload");
    }

    @MessageExceptionHandler(DataIntegrityViolationException.class)
    @SendToUser(destinations = ERRORS, broadcast = false)
    public WsError handleIntegrity(DataIntegrityViolationException ex) {
        return WsError.of(409, "Resource conflict");
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser(destinations = ERRORS, broadcast = false)
    public WsError handleUnexpected(Exception ex) {
        log.error("Unhandled websocket error", ex);
        return WsError.of(500, "Something went wrong");
    }
}
