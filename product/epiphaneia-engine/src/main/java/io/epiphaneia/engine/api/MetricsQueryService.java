package io.epiphaneia.engine.api;

import io.epiphaneia.infra.api.connector.QueryResult;

/** Service for building and executing metric queries against time-series data sources. */
public interface MetricsQueryService {

    /** Query metrics given a metric intent and time range. Returns structured query results. */
    QueryResult query(String datasourceType, String metricIntent, String timeRange);
}
