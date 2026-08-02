package io.epiphaneia.engine.api.query;

import org.springframework.stereotype.Component;

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

    /**
     * Build a range query for a metric over a time window, optionally wrapped in an
     * aggregation. Built structurally so the range window always attaches to the
     * metric selector itself (correct for nested aggregations like
     * {@code sum(rate(metric{labels}[5m]))} — a naive lastIndexOf(")") would drop
     * the outer closing paren).
     */
    public String buildRangeQuery(String metric, Map<String, String> labels,
                                   String aggregation, String rangeWindow) {
        if (metric == null || metric.isBlank()) {
            throw new IllegalArgumentException("metric must not be null or blank");
        }
        String selector = metric + formatLabels(labels);
        if (rangeWindow != null && !rangeWindow.isBlank()) {
            selector += "[" + rangeWindow + "]";
        }
        if (aggregation != null && !aggregation.isBlank()) {
            // single function name only — nested calls like "sum(rate" would produce
            // unbalanced PromQL, so reject them up front
            if (!aggregation.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                throw new IllegalArgumentException(
                        "Invalid aggregation function name: '" + aggregation + "'");
            }
            return aggregation + "(" + selector + ")";
        }
        return selector;
    }

    /** Build an instant query for a metric at a point in time. */
    public String buildInstantQuery(String metric, Map<String, String> labels) {
        if (metric == null || metric.isBlank()) {
            throw new IllegalArgumentException("metric must not be null or blank");
        }
        return metric + formatLabels(labels);
    }

    /**
     * Build a PromQL query for rate of requests over a window.
     * Standard pattern: rate(http_requests_total{job="svc"}[5m])
     */
    public String buildRateQuery(String metric, Map<String, String> labels, String window) {
        if (metric == null || metric.isBlank()) {
            throw new IllegalArgumentException("metric must not be null or blank");
        }
        return "rate(" + metric + formatLabels(labels)
                + "[" + (window != null ? window : "5m") + "])";
    }

    /** Serialize the label matcher block {@code {k="v",...}}; empty input yields "". */
    private static String formatLabels(Map<String, String> labels) {
        if (labels == null || labels.isEmpty()) return "";
        String body = labels.entrySet().stream()
                .map(e -> {
                    if (!e.getKey().matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                        throw new IllegalArgumentException(
                                "Invalid PromQL label name: '" + e.getKey() + "'");
                    }
                    return e.getKey() + "=\"" + escapeLabelValue(e.getValue()) + "\"";
                })
                .collect(Collectors.joining(","));
        return "{" + body + "}";
    }

    private static String escapeLabelValue(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
