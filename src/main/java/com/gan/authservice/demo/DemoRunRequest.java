package com.gan.authservice.demo;

import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Input for a visualizer run: the end-user credentials the flow logs in with, plus an optional
 * "sign up first" step that registers a fresh USER before logging in.
 *
 * @param username    the end user to authenticate at {@code /login}.
 * @param password    that user's password (redacted in the captured output).
 * @param signupFirst when {@code true}, prepend a {@code POST /auth/signup} step for this user.
 * @param firstName   first name for the signup step (defaults applied when blank).
 * @param lastName    last name for the signup step (defaults applied when blank).
 */
@NullMarked
public record DemoRunRequest(
    @NotBlank String username,
    @NotBlank String password,
    boolean signupFirst,
    @Nullable String firstName,
    @Nullable String lastName) {
}
