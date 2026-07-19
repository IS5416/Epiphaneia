package io.epiphaneia.infra.internal.exception;

/** Thrown when a data source (Prometheus, Elasticsearch) is unreachable. */
public class DataSourceUnavailableException extends EpiphaneiaException {
    public DataSourceUnavailableException(String type, String message) {
        super("DATASOURCE_UNAVAILABLE", type + ": " + message);
    }
}
