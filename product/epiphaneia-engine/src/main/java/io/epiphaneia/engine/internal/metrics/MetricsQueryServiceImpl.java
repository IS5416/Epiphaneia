package io.epiphaneia.engine.internal.metrics;

import io.epiphaneia.engine.api.MetricsQueryService;
import io.epiphaneia.engine.internal.prometheus.PrometheusQueryBuilder;
import io.epiphaneia.infra.api.connector.QueryResult;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Builds metric queries using {@link PrometheusQueryBuilder}.
 * Actual connector dispatch is handled by the orchestration layer (agent-core).
 */
@Service
public class MetricsQueryServiceImpl implements MetricsQueryService {

    private final PrometheusQueryBuilder queryBuilder;

    public MetricsQueryServiceImpl(PrometheusQueryBuilder queryBuilder) {
        this.queryBuilder = queryBuilder;
    }

    @Override
    public QueryResult query(String datasourceType, String metricIntent, String timeRange) {
        // ponytail: returns placeholder — real query dispatch in Phase 2 orchestration
        String promql = queryBuilder.buildInstantQuery(metricIntent, Map.of());
        return new QueryResult() {};
    }
}
