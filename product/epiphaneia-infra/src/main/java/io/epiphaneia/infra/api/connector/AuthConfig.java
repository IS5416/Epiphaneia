package io.epiphaneia.infra.api.connector;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Authentication configuration for a data source connection. */
public record AuthConfig(Type type, String username, String password, String token) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public enum Type {
        NONE, BASIC, BEARER
    }

    public static AuthConfig none() {
        return new AuthConfig(Type.NONE, null, null, null);
    }

    public static AuthConfig basic(String username, String password) {
        return new AuthConfig(Type.BASIC, username, password, null);
    }

    public static AuthConfig bearer(String token) {
        return new AuthConfig(Type.BEARER, null, null, token);
    }

    /**
     * Rebuild an AuthConfig from the persisted {@code authType} and {@code authConfig}
     * (JSON) fields of a {@code DataSource}. Unknown or malformed input degrades to
     * {@link #none()} rather than failing the query path.
     */
    public static AuthConfig from(String authType, String authConfigJson) {
        Type t = Type.NONE;
        try {
            if (authType != null && !authType.isBlank()) {
                t = Type.valueOf(authType.trim().toUpperCase());
            }
        } catch (IllegalArgumentException ignored) {
            // unknown type degrades to NONE
        }
        if (t == Type.NONE || authConfigJson == null || authConfigJson.isBlank()) {
            return new AuthConfig(t, null, null, null);
        }
        try {
            var node = MAPPER.readTree(authConfigJson);
            return new AuthConfig(t,
                    node.path("username").asText(null),
                    node.path("password").asText(null),
                    node.path("token").asText(null));
        } catch (Exception e) {
            return new AuthConfig(t, null, null, null);
        }
    }

    /**
     * Apply this config as an Authorization header on an outgoing HTTP request.
     * Single implementation shared by all connectors — keeps auth handling in one place.
     */
    public void applyTo(java.net.http.HttpRequest.Builder builder) {
        if (type == null || type == Type.NONE) return;
        if (type == Type.BASIC && username != null) {
            String cred = username + ":" + (password != null ? password : "");
            builder.header("Authorization",
                    "Basic " + java.util.Base64.getEncoder()
                            .encodeToString(cred.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } else if (type == Type.BEARER && token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
    }

    @Override
    public String toString() {
        return "AuthConfig[type=%s, username=%s, password=%s, token=%s]".formatted(
                type, username,
                password != null ? "***" : null,
                token != null ? "***" : null);
    }
}
