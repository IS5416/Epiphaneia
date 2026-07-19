package io.epiphaneia.infra.api.connector;

/**
 * Connector SPI — data source integration point.
 * <p>
 * Community contributions: implement this interface in epiphaneia-connector,
 * add @Component, and the ConnectorRegistry will auto-discover it.
 *
 * @param <T> query request type
 * @param <R> query result type
 */
public interface Connector<T extends QueryRequest, R extends QueryResult> {

    /** The data source type this connector supports (e.g. "PROMETHEUS", "ELASTICSEARCH"). */
    String type();

    /** Execute a read-only query against the external data source. */
    R query(T request);

    /** Test connectivity to the configured data source. */
    default boolean testConnection(ConnectionConfig config) {
        return false;
    }
}
