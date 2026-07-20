package io.epiphaneia.engine.internal.prometheus;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds PromQL queries from structured intent parameters.
 * <p>
 * This is NOT an LLM-based builder — the orchestration layer (agent-core)
 * converts natural language intent into structured parameters via LLM,
 * then passes them here for concrete PromQL generation.
 */
@Component
public class PrometheusQueryBuilder {

    /** Build a rate query for a metric over a time window. */
    public String buildRangeQuery(String metric, Map<String, String> labels,
                                   String aggregation, String rangeWindow) {
        StringBuilder sb = new StringBuilder();
        if (aggregation != null && !aggregation.isBlank()) {
            sb.append(aggregation).append("(");
        }
        sb.append(metric);
        if (labels != null && !labels.isEmpty()) {
            sb.append("{");
            sb.append(labels.entrySet().stream()
                    .map(e -> e.getKey() + "=\"" + e.getValue() + "\"")
                    .collect(Collectors.joining(",")));
            sb.append("}");
        }
        if (aggregation != null && !aggregation.isBlank()) {
            sb.append(")");
        }
        if (rangeWindow != null && !rangeWindow.isBlank()) {
            int aggClose = sb.lastIndexOf(")");
            if (aggClose >= 0) {
                sb.insert(aggClose, "[" + rangeWindow + "]");
            } else {
                sb.append("[").append(rangeWindow).append("]");
            }
        }
        return sb.toString();
    }

    /** Build an instant query for a metric at a point in time. */
    public String buildInstantQuery(String metric, Map<String, String> labels) {
        if (metric == null) metric = "unknown";
        StringBuilder sb = new StringBuilder(metric);
        if (labels != null && !labels.isEmpty()) {
            sb.append("{");
            sb.append(labels.entrySet().stream()
                    .map(e -> e.getKey() + "=\"" + e.getValue() + "\"")
                    .collect(Collectors.joining(",")));
            sb.append("}");
        }
        return sb.toString();
    }

    /**
     * Build a PromQL query for rate of requests over a window.
     * Standard pattern: rate(http_requests_total{job="svc"}[5m])
     */
    public String buildRateQuery(String metric, Map<String, String> labels, String window) {
        StringBuilder base = new StringBuilder("rate(").append(metric);
        if (labels != null && !labels.isEmpty()) {
            base.append("{");
            base.append(labels.entrySet().stream()
                    .map(e -> e.getKey() + "=\"" + e.getValue() + "\"")
                    .collect(Collectors.joining(",")));
            base.append("}");
        }
        base.append("[").append(window != null ? window : "5m").append("])");
        return base.toString();
    }
}
