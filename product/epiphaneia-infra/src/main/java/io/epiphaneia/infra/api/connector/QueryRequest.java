package io.epiphaneia.infra.api.connector;

/** Base type for connector query requests. */
public interface QueryRequest {

    /** A query request carrying a concrete query string (PromQL, ES DSL, etc.). */
    record Typed(String query, String datasourceUrl) implements QueryRequest {}

    /** A query request for testing connectivity only. */
    record Test() implements QueryRequest {}
}
