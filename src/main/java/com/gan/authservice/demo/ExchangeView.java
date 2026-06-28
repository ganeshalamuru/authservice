package com.gan.authservice.demo;

import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * One side (request or response) of a captured HTTP exchange, shaped for display in the visualizer.
 * A request carries {@code method} + {@code url}; a response carries {@code status} +
 * {@code statusText}. Both carry {@code headers} and an optional {@code body}.
 *
 * @param method     HTTP method (requests only).
 * @param url        full request URL (requests only).
 * @param status     HTTP status code (responses only).
 * @param statusText HTTP reason phrase (responses only).
 * @param headers    headers shown to the user (sensitive values already redacted).
 * @param body       request/response body, or {@code null} when there is none.
 */
@NullMarked
public record ExchangeView(
    @Nullable String method,
    @Nullable String url,
    @Nullable Integer status,
    @Nullable String statusText,
    Map<String, String> headers,
    @Nullable String body) {

    static ExchangeView request(String method, String url, Map<String, String> headers, @Nullable String body) {
        return new ExchangeView(method, url, null, null, headers, body);
    }

    static ExchangeView response(int status, String statusText, Map<String, String> headers, @Nullable String body) {
        return new ExchangeView(null, null, status, statusText, headers, body);
    }
}
