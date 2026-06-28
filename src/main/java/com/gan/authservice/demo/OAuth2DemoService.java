package com.gan.authservice.demo;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gan.authservice.constants.JwtProperties;
import com.gan.authservice.constants.RegisteredClientProperties;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Replays the full OAuth2 Authorization Code + PKCE flow against the running app, acting as the
 * {@code authservice-client}, and records every HTTP exchange (and the client-side computations) as
 * an ordered list of {@link InteractionStep} for the visualizer to render.
 *
 * <p>Redirects are <b>not</b> followed (so each 302 in the authorize&rarr;login&rarr;callback chain
 * is captured with its {@code Location}/{@code Set-Cookie}); a small per-run cookie jar carries the
 * session across the login. Secrets (the end-user password and the client secret) are redacted in the
 * captured output. Gated by {@code demo.oauth2.enabled} so it never runs in production.
 */
@NullMarked
@Service
@ConditionalOnProperty(prefix = "demo.oauth2", name = "enabled", havingValue = "true")
public class OAuth2DemoService {

    private static final String SCOPES = "openid profile roles";
    private static final List<String> SHOWN_RESPONSE_HEADERS =
        List.of("location", "set-cookie", "content-type", "www-authenticate", "cache-control");

    private final RegisteredClientProperties clientProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String baseUrl;
    private final String redirectUri;

    public OAuth2DemoService(RegisteredClientProperties clientProperties, JwtProperties jwtProperties) {
        this.clientProperties = clientProperties;
        // A standalone mapper purely for pretty-printing/parsing captured JSON bodies — avoids
        // coupling to the app's primary (Jackson 3) JSON setup.
        this.objectMapper = new ObjectMapper();
        this.baseUrl = stripTrailingSlash(jwtProperties.issuer());
        this.redirectUri = clientProperties.redirectUris().getFirst();
        this.httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    public List<InteractionStep> run(DemoRunRequest request) {
        return new Flow(request).execute();
    }

    /** Per-run mutable state + step methods; a fresh instance is created for every request. */
    private final class Flow {

        private final DemoRunRequest request;
        private final List<InteractionStep> steps = new ArrayList<>();
        private final Map<String, String> cookies = new LinkedHashMap<>();

        private String codeVerifier = "";
        private String codeChallenge = "";
        private String state = "";
        private String authorizationCode = "";
        private String accessToken = "";
        private String refreshToken = "";
        private String idToken = "";

        private Flow(DemoRunRequest request) {
            this.request = request;
        }

        private List<InteractionStep> execute() {
            try {
                if (request.signupFirst()) {
                    signup();
                }
                discovery();
                jwks();
                pkce();
                authorizeUnauthenticated();
                String savedAuthorizeUrl = login();
                authorizeAuthenticated(savedAuthorizeUrl);
                tokenExchange();
                decodeTokens();
                callApi();
                refresh();
                revoke();
            } catch (DemoFlowException e) {
                steps.add(InteractionStep.computation(steps.size() + 1, "Flow stopped",
                    "The walkthrough could not continue past this point.", e.getMessage()));
            }
            return steps;
        }

        // --- steps -------------------------------------------------------------------------------

        private void signup() {
            String firstName = orDefault(request.firstName(), "Demo");
            String lastName = orDefault(request.lastName(), "User");
            String realBody = json(Map.of(
                "username", request.username(),
                "password", request.password(),
                "firstName", firstName,
                "lastName", lastName));
            String shownBody = json(Map.of(
                "username", request.username(),
                "password", "***",
                "firstName", firstName,
                "lastName", lastName));
            Captured c = call("POST", baseUrl + "/auth/signup",
                headers("Content-Type", "application/json"), realBody,
                headers("Content-Type", "application/json"), shownBody);
            steps.add(InteractionStep.http(steps.size() + 1, "Register a user",
                "The client first creates an end user. A 201 means the account was created; a 409 "
                    + "means it already exists (harmless — the login below still works).",
                c.request(), c.response()));
        }

        private void discovery() {
            Captured c = call("GET", baseUrl + "/.well-known/openid-configuration",
                headers(), null, headers(), null);
            steps.add(InteractionStep.http(steps.size() + 1, "OIDC discovery",
                "Before anything else a client fetches the discovery document to learn the endpoint "
                    + "URLs (authorization, token, jwks, revoke), supported scopes and grant types.",
                c.request(), c.response()));
        }

        private void jwks() {
            Captured c = call("GET", baseUrl + "/oauth2/jwks", headers(), null, headers(), null);
            steps.add(InteractionStep.http(steps.size() + 1, "Fetch signing keys (JWKS)",
                "The public RS256 keys used to verify token signatures. A resource server caches these "
                    + "and matches a token's 'kid' against them — no shared secret needed.",
                c.request(), c.response()));
        }

        private void pkce() {
            this.codeVerifier = randomUrlSafe(32);
            this.codeChallenge = base64Url(sha256(codeVerifier));
            this.state = randomUrlSafe(16);
            String details = "code_verifier  = " + codeVerifier + "\n"
                + "code_challenge = " + codeChallenge + "\n"
                + "                 (BASE64URL(SHA-256(code_verifier)), code_challenge_method = S256)\n"
                + "state          = " + state;
            steps.add(InteractionStep.computation(steps.size() + 1, "Generate PKCE + state",
                "The client invents a random 'code_verifier', hashes it into a 'code_challenge', and a "
                    + "random 'state'. The challenge goes in the authorize request now; the verifier is "
                    + "revealed only at the token call — proving the same client that started the flow is "
                    + "finishing it.", details));
        }

        private void authorizeUnauthenticated() {
            String url = authorizeUrl();
            Captured c = call("GET", url, headers("Accept", "text/html"), null,
                headers("Accept", "text/html"), null);
            steps.add(InteractionStep.http(steps.size() + 1, "Authorization request (not logged in)",
                "The browser hits /oauth2/authorize. Because there is no session yet, the auth server "
                    + "saves this request, starts a session (Set-Cookie: JSESSIONID) and 302-redirects to "
                    + "the login page.",
                c.request(), c.response()));
            if (c.raw().statusCode() / 100 != 3) {
                throw new DemoFlowException("Expected a 302 redirect to /login but got HTTP "
                    + c.raw().statusCode() + ".");
            }
        }

        private String login() {
            String realBody = "username=" + enc(request.username()) + "&password=" + enc(request.password());
            String shownBody = "username=" + request.username() + "&password=***";
            Captured c = call("POST", baseUrl + "/login",
                headers("Content-Type", "application/x-www-form-urlencoded"), realBody,
                headers("Content-Type", "application/x-www-form-urlencoded"), shownBody);
            steps.add(InteractionStep.http(steps.size() + 1, "Submit the login form",
                "Credentials are posted to /login. On success the auth server rotates the session id "
                    + "(session-fixation protection) and 302-redirects back to the saved authorization "
                    + "request; on failure it redirects to /login?error.",
                c.request(), c.response()));
            String location = location(c);
            if (location == null) {
                throw new DemoFlowException("Login did not return a redirect — authentication failed.");
            }
            if (!location.contains("/oauth2/authorize")) {
                throw new DemoFlowException(
                    "Login failed (redirected to " + location + "). Check the username/password.");
            }
            return location;
        }

        private void authorizeAuthenticated(String authorizeUrl) {
            Captured c = call("GET", authorizeUrl, headers("Accept", "text/html"), null,
                headers("Accept", "text/html"), null);
            steps.add(InteractionStep.http(steps.size() + 1, "Authorization request (logged in)",
                "The saved authorization request is replayed, now with the authenticated session "
                    + "cookie. Consent is not required for this client, so the auth server issues an "
                    + "authorization 'code' and 302-redirects to the client's redirect_uri.",
                c.request(), c.response()));
            String location = location(c);
            if (location == null) {
                throw new DemoFlowException("Authorization request did not redirect to the callback.");
            }
            String error = queryParam(location, "error");
            if (error != null) {
                throw new DemoFlowException("Authorization failed: " + error);
            }
            String code = queryParam(location, "code");
            if (code == null) {
                throw new DemoFlowException("No 'code' parameter found in the callback redirect.");
            }
            this.authorizationCode = code;
        }

        private void tokenExchange() {
            String realBody = "grant_type=authorization_code"
                + "&code=" + enc(authorizationCode)
                + "&redirect_uri=" + enc(redirectUri)
                + "&code_verifier=" + enc(codeVerifier);
            String shownBody = "grant_type=authorization_code"
                + "&code=" + authorizationCode
                + "&redirect_uri=" + redirectUri
                + "&code_verifier=" + codeVerifier;
            Captured c = call("POST", baseUrl + "/oauth2/token",
                tokenHeaders(), realBody, shownTokenHeaders(), shownBody);
            steps.add(InteractionStep.http(steps.size() + 1, "Exchange code for tokens",
                "The client authenticates with HTTP Basic (client_id:client_secret) and trades the code "
                    + "+ the original code_verifier for tokens. The auth server re-hashes the verifier and "
                    + "checks it matches the earlier challenge, then returns access, refresh and id tokens.",
                c.request(), c.response()));
            if (c.raw().statusCode() != 200) {
                throw new DemoFlowException("Token endpoint returned HTTP " + c.raw().statusCode()
                    + ": " + c.raw().body());
            }
            Map<String, Object> tokens = parseJson(c.raw().body());
            this.accessToken = string(tokens, "access_token");
            this.refreshToken = string(tokens, "refresh_token");
            this.idToken = string(tokens, "id_token");
        }

        private void decodeTokens() {
            StringBuilder details = new StringBuilder();
            details.append("ACCESS TOKEN  (sent as 'Authorization: Bearer …' to the API)\n");
            details.append(decodeJwt(accessToken));
            if (StringUtils.hasText(idToken)) {
                details.append("\n\nID TOKEN  (identity for the client; from the 'openid' scope)\n");
                details.append(decodeJwt(idToken));
            }
            steps.add(InteractionStep.computation(steps.size() + 1, "Decode the tokens",
                "Access/id tokens are signed JWTs — anyone can read the payload (it is only base64url, "
                    + "not encrypted); the signature is what makes it trustworthy. Note sub = user UUID, "
                    + "the 'role' claim, aud and iss.", details.toString()));
        }

        private void callApi() {
            Captured c = call("GET", baseUrl + "/api/users",
                headers("Authorization", "Bearer " + accessToken, "X-API-Version", "1"), null,
                headers("Authorization", "Bearer " + abbreviate(accessToken) + " (access_token from the previous step)",
                    "X-API-Version", "1"), null);
            steps.add(InteractionStep.http(steps.size() + 1, "Call the protected API",
                "The access token is sent as a Bearer token to /api/users. The stateless resource-server "
                    + "filter chain validates the signature, issuer and audience and reads the 'role' claim "
                    + "— no session, no DB lookup — then returns the data.",
                c.request(), c.response()));
        }

        private void refresh() {
            String previous = refreshToken;
            String realBody = "grant_type=refresh_token&refresh_token=" + enc(refreshToken);
            String shownBody = "grant_type=refresh_token&refresh_token=" + refreshToken;
            Captured c = call("POST", baseUrl + "/oauth2/token",
                tokenHeaders(), realBody, shownTokenHeaders(), shownBody);
            steps.add(InteractionStep.http(steps.size() + 1, "Refresh the access token",
                "When the short-lived access token expires the client exchanges its refresh token for a "
                    + "new one. This client rotates refresh tokens, so the response contains a brand-new "
                    + "refresh_token and the old one is now invalid.",
                c.request(), c.response()));
            if (c.raw().statusCode() == 200) {
                Map<String, Object> tokens = parseJson(c.raw().body());
                this.accessToken = string(tokens, "access_token");
                String rotated = string(tokens, "refresh_token");
                if (StringUtils.hasText(rotated)) {
                    this.refreshToken = rotated;
                }
                steps.add(InteractionStep.computation(steps.size() + 1, "Refresh token rotated",
                    "Confirm the refresh token changed — proof of rotation.",
                    "old refresh_token = " + previous + "\nnew refresh_token = " + refreshToken));
            }
        }

        private void revoke() {
            String realBody = "token=" + enc(refreshToken) + "&token_type_hint=refresh_token";
            String shownBody = "token=" + refreshToken + "&token_type_hint=refresh_token";
            Captured c = call("POST", baseUrl + "/oauth2/revoke",
                tokenHeaders(), realBody, shownTokenHeaders(), shownBody);
            steps.add(InteractionStep.http(steps.size() + 1, "Revoke the refresh token",
                "Finally the client revokes the refresh token at /oauth2/revoke. The endpoint always "
                    + "returns 200; the token can no longer be used to obtain new access tokens.",
                c.request(), c.response()));
        }

        // --- HTTP plumbing -----------------------------------------------------------------------

        private Captured call(String method, String url, Map<String, String> sendHeaders,
            @Nullable String sendBody, Map<String, String> shownHeaders, @Nullable String shownBody) {
            LinkedHashMap<String, String> toSend = new LinkedHashMap<>(sendHeaders);
            LinkedHashMap<String, String> toShow = new LinkedHashMap<>(shownHeaders);
            String cookieHeader = cookieHeader();
            if (cookieHeader != null) {
                toSend.put("Cookie", cookieHeader);
                toShow.put("Cookie", cookieHeader);
            }
            HttpRequest.BodyPublisher publisher = sendBody == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(sendBody, UTF_8);
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .method(method, publisher);
            toSend.forEach(builder::header);
            HttpResponse<String> response;
            try {
                response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(UTF_8));
            } catch (IOException e) {
                throw new DemoFlowException("Call to " + url + " failed: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new DemoFlowException("Call to " + url + " was interrupted.");
            }
            response.headers().allValues("set-cookie").forEach(this::storeCookie);
            ExchangeView requestView = ExchangeView.request(method, url, toShow, shownBody);
            ExchangeView responseView = ExchangeView.response(response.statusCode(),
                reasonPhrase(response.statusCode()), shownResponseHeaders(response.headers()),
                prettyBody(response));
            return new Captured(requestView, responseView, response);
        }

        private void storeCookie(String setCookie) {
            String first = setCookie.split(";", 2)[0].trim();
            int eq = first.indexOf('=');
            if (eq <= 0) {
                return;
            }
            cookies.put(first.substring(0, eq).trim(), first.substring(eq + 1).trim());
        }

        private @Nullable String cookieHeader() {
            if (cookies.isEmpty()) {
                return null;
            }
            return cookies.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("; "));
        }

        private String authorizeUrl() {
            return baseUrl + "/oauth2/authorize?response_type=code"
                + "&client_id=" + enc(clientProperties.clientId())
                + "&redirect_uri=" + enc(redirectUri)
                + "&scope=" + enc(SCOPES)
                + "&code_challenge=" + enc(codeChallenge)
                + "&code_challenge_method=S256"
                + "&state=" + enc(state);
        }

        private Map<String, String> tokenHeaders() {
            return headers("Authorization", "Basic " + basicCredential(),
                "Content-Type", "application/x-www-form-urlencoded");
        }

        private Map<String, String> shownTokenHeaders() {
            return headers("Authorization", "Basic *** (base64 of client_id:client_secret — secret redacted)",
                "Content-Type", "application/x-www-form-urlencoded");
        }

        private String basicCredential() {
            String raw = clientProperties.clientId() + ":" + clientProperties.clientSecret();
            return Base64.getEncoder().encodeToString(raw.getBytes(UTF_8));
        }

        private @Nullable String prettyBody(HttpResponse<String> response) {
            String raw = response.body();
            if (raw.isBlank()) {
                return null;
            }
            String contentType = response.headers().firstValue("content-type").orElse("");
            if (contentType.contains("json")) {
                return prettyJson(raw);
            }
            return raw;
        }

        private Map<String, String> shownResponseHeaders(HttpHeaders headers) {
            LinkedHashMap<String, String> shown = new LinkedHashMap<>();
            for (String name : SHOWN_RESPONSE_HEADERS) {
                List<String> values = headers.allValues(name);
                if (!values.isEmpty()) {
                    shown.put(canonical(name), String.join(", ", values));
                }
            }
            return shown;
        }

        private @Nullable String location(Captured captured) {
            return captured.raw().headers().firstValue("location").orElse(null);
        }
    }

    // --- stateless helpers -----------------------------------------------------------------------

    private record Captured(ExchangeView request, ExchangeView response, HttpResponse<String> raw) {
    }

    private static Map<String, String> headers(String... keyValues) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, UTF_8);
    }

    private String json(Map<String, String> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String prettyJson(String raw) {
        try {
            Object tree = objectMapper.readValue(raw, Object.class);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(tree);
        } catch (Exception e) {
            return raw;
        }
    }

    private Map<String, Object> parseJson(String raw) {
        try {
            return objectMapper.readValue(raw, new  TypeReference<>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static String string(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : value.toString();
    }

    private static String orDefault(@Nullable String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private String decodeJwt(String jwt) {
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) {
            return "(not a JWT)";
        }
        String header = prettyJson(new String(Base64.getUrlDecoder().decode(parts[0]), UTF_8));
        String payload = prettyJson(new String(Base64.getUrlDecoder().decode(parts[1]), UTF_8));
        return "  header:  " + indent(header) + "\n  payload: " + indent(payload);
    }

    private static String indent(String json) {
        return json.replace("\n", "\n           ");
    }

    private static @Nullable String queryParam(String url, String name) {
        URI uri = URI.create(url);
        String query = uri.getRawQuery();
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            if (key.equals(name)) {
                String value = eq >= 0 ? pair.substring(eq + 1) : "";
                return URLDecoder.decode(value, UTF_8);
            }
        }
        return null;
    }

    private static String randomUrlSafe(int bytes) {
        byte[] buffer = new byte[bytes];
        new SecureRandom().nextBytes(buffer);
        return base64Url(buffer);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String abbreviate(String token) {
        if (token.length() <= 24) {
            return token;
        }
        return token.substring(0, 18) + "…" + token.substring(token.length() - 6);
    }

    private static String canonical(String lowerHeader) {
        return switch (lowerHeader) {
            case "location" -> "Location";
            case "set-cookie" -> "Set-Cookie";
            case "content-type" -> "Content-Type";
            case "www-authenticate" -> "WWW-Authenticate";
            case "cache-control" -> "Cache-Control";
            default -> lowerHeader;
        };
    }

    private static String reasonPhrase(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 204 -> "No Content";
            case 302 -> "Found";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 409 -> "Conflict";
            case 500 -> "Internal Server Error";
            default -> "";
        };
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** Signals that the walkthrough cannot continue; the message is shown as the final step. */
    private static final class DemoFlowException extends RuntimeException {
        private DemoFlowException(String message) {
            super(message);
        }
    }
}
