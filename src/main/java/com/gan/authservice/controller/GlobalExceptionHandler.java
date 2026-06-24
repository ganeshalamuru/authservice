package com.gan.authservice.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Central error mapper. Extends {@link ResponseEntityExceptionHandler} so every Spring MVC
 * exception (validation, unreadable body, unsupported method, …) is rendered as an RFC 7807
 * {@link ProblemDetail} ({@code application/problem+json}) instead of falling through to the
 * default {@code /error} page.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Maps {@link ResponseStatusException} (the app's primary way of signalling HTTP errors) to a
     * {@link ProblemDetail}. Routed through {@link #handleExceptionInternal} so the body is written
     * directly via the message converters — NOT via {@code HttpServletResponse.sendError(...)},
     * which would trigger a container ERROR dispatch to {@code /error}, re-run the security filter
     * chain, and mask the real status as a 401 on permit-all endpoints (e.g. {@code /auth/signup}).
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Object> handleResponseStatus(ResponseStatusException ex, WebRequest request) {
        // ResponseStatusException implements ErrorResponse: getBody() is a ProblemDetail whose
        // detail is the supplied reason, already carrying the correct status.
        return handleExceptionInternal(ex, ex.getBody(), ex.getHeaders(), ex.getStatusCode(), request);
    }

    /**
     * Enriches the bean-validation 400 with a field → message map so clients get an actionable
     * {@code errors} object instead of an opaque "Invalid request content." detail.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "Request validation failed");
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            // Keep the first message per field; later constraints on the same field are dropped.
            errors.putIfAbsent(fieldError.getField(),
                fieldError.getDefaultMessage() == null ? "invalid" : fieldError.getDefaultMessage());
        }
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

}
