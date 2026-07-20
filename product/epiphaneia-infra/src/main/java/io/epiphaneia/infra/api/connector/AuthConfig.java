package io.epiphaneia.infra.api.connector;

/** Authentication configuration for a data source connection. */
public record AuthConfig(Type type, String username, String password, String token) {

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

    @Override
    public String toString() {
        return "AuthConfig[type=%s, username=%s, password=%s, token=%s]".formatted(
                type, username,
                password != null ? "***" : null,
                token != null ? "***" : null);
    }
}
