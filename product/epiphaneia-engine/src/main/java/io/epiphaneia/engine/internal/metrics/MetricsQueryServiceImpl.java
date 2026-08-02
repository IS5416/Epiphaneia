package io.epiphaneia.engine.internal.metrics;

import io.epiphaneia.engine.api.MetricsQueryService;
import io.epiphaneia.engine.api.query.PrometheusQueryBuilder;
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
        if (!"PROMETHEUS".equalsIgnoreCase(datasourceType)) {
            return new QueryResult.Failure("UNSUPPORTED_DATASOURCE",
                    "MetricsQueryService only supports PROMETHEUS, got " + datasourceType);
        }
        try {
            // ponytail: builds the PromQL here; actual connector dispatch happens in agent-core
            String promql = queryBuilder.buildInstantQuery(metricIntent, Map.of());
            return new QueryResult.Failure("NOT_IMPLEMENTED",
                    "Direct dispatch removed — route through DiagnosisOrchestrator (query built: " + promql + ")");
        } catch (IllegalArgumentException e) {
            return new QueryResult.Failure("INVALID_METRIC", e.getMessage());
        }
    }
}
