package io.epiphaneia.infra.api.connector;

/** Shortcut constants for common data source types. Core matching uses string-based lookup. */
public final class DataSourceType {

    public static final String PROMETHEUS = "PROMETHEUS";
    public static final String ELASTICSEARCH = "ELASTICSEARCH";
    public static final String ACTUATOR = "ACTUATOR";

    private DataSourceType() {
    }
}
