package io.epiphaneia.infra.api.connector;

/** Configuration needed to establish and test a connector connection. */
public record ConnectionConfig(String url, AuthConfig authConfig) {
}
