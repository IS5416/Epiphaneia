package io.epiphaneia.engine.api;

import io.epiphaneia.infra.api.connector.QueryResult;

/**
 * Service for building and executing log queries against Elasticsearch data sources.
 */
public interface LogQueryService {

    /** Query logs for a service in a time range. Returns structured log entries. */
    QueryResult query(String datasourceType, String service, String startTime, String endTime);
}
