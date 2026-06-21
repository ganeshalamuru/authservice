package com.gan.authservice.controller;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles {@link ResponseStatusException} by writing the response body directly via the message
     * converters. This avoids {@code HttpServletResponse.sendError(...)}, which would trigger a
     * container ERROR dispatch to {@code /error} and cause the security filter chain to re-run and
     * mask the real status as a 401 on permit-all endpoints.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
            .body(Map.of(
                "status", ex.getStatusCode().value(),
                "error", ex.getReason() == null ? "" : ex.getReason()));
    }

}
