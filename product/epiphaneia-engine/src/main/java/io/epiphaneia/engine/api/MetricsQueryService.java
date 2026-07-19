package io.epiphaneia.engine.api;

/** Service for building and executing metric queries against time-series data sources. */
public interface MetricsQueryService {

    /** Query metrics given a natural-language intent description. Returns structured metric samples. */
    Object query(String datasourceType, String metricIntent, String timeRange);
}
