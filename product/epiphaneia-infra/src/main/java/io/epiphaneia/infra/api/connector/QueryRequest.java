package io.epiphaneia.infra.api.connector;

/** Base type for connector query requests. */
public interface QueryRequest {

    /**
     * A query request carrying a concrete query string (PromQL, ES DSL, etc.)
     * plus optional auth credentials for the target data source.
     */
    record Typed(String query, String datasourceUrl, AuthConfig authConfig) implements QueryRequest {
        /** Convenience constructor for unauthenticated data sources. */
        public Typed(String query, String datasourceUrl) {
            this(query, datasourceUrl, AuthConfig.none());
        }
    }

    /** A query request for testing connectivity only. */
    record Test() implements QueryRequest {}
}
