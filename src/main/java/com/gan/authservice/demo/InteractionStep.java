package com.gan.authservice.demo;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A single, ordered interaction in the OAuth2 walkthrough.
 *
 * <p>{@code kind} is either {@code "http"} (a real call, with {@code request} and {@code response}
 * captured) or {@code "computation"} (a client-side step such as PKCE generation or token decoding,
 * where {@code request}/{@code response} are {@code null} and the computed values live in
 * {@code details}).
 *
 * @param index       1-based position in the flow.
 * @param kind        {@code "http"} or {@code "computation"}.
 * @param title       short step title.
 * @param explanation plain-English description of what happens and why.
 * @param request     the request side (http steps only).
 * @param response    the response side (http steps only).
 * @param details     pre-formatted text for computation steps (e.g. PKCE values, decoded claims).
 */
@NullMarked
public record InteractionStep(
    int index,
    String kind,
    String title,
    String explanation,
    @Nullable ExchangeView request,
    @Nullable ExchangeView response,
    @Nullable String details) {

    static InteractionStep http(int index, String title, String explanation,
        ExchangeView request, ExchangeView response) {
        return new InteractionStep(index, "http", title, explanation, request, response, null);
    }

    static InteractionStep computation(int index, String title, String explanation, String details) {
        return new InteractionStep(index, "computation", title, explanation, null, null, details);
    }
}
