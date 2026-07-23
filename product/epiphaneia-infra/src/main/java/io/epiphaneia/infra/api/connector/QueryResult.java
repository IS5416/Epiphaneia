package io.epiphaneia.infra.api.connector;

/** Base type for connector query results. */
public interface QueryResult {

    /** A successful query result with JSON body. */
    record Success(String rawJson, String summary) implements QueryResult {}

    /** A failed query result with error information. */
    record Failure(String error, String detail) implements QueryResult {}
}
