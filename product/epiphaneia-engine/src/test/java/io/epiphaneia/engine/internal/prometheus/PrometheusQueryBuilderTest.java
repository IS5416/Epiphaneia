package io.epiphaneia.engine.internal.prometheus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PrometheusQueryBuilderTest {

    private final PrometheusQueryBuilder builder = new PrometheusQueryBuilder();

    @Test
    @DisplayName("builds instant query with labels")
    void instantQueryWithLabels() {
        String result = builder.buildInstantQuery("http_requests_total",
                Map.of("job", "user-service", "method", "GET"));
        assertTrue(result.startsWith("http_requests_total{"));
        assertTrue(result.contains("job=\"user-service\""));
        assertTrue(result.contains("method=\"GET\""));
    }

    @Test
    @DisplayName("builds instant query without labels")
    void instantQueryNoLabels() {
        assertEquals("http_requests_total",
                builder.buildInstantQuery("http_requests_total", Map.of()));
    }

    @Test
    @DisplayName("builds range query with aggregation and range window")
    void rangeQueryWithAggregation() {
        String result = builder.buildRangeQuery("http_requests_total",
                Map.of("job", "svc"), "rate", "5m");
        assertEquals("rate(http_requests_total{job=\"svc\"}[5m])", result);
    }

    @Test
    @DisplayName("builds range query without aggregation, with range")
    void rangeQueryNoAggregation() {
        String result = builder.buildRangeQuery("up", Map.of(), null, "1h");
        assertEquals("up[1h]", result);
    }

    @Test
    @DisplayName("builds rate query with default window")
    void rateQuery() {
        String result = builder.buildRateQuery("http_requests_total",
                Map.of("job", "api"), null);
        assertEquals("rate(http_requests_total{job=\"api\"}[5m])", result);
    }

    @Test
    @DisplayName("builds rate query with custom window")
    void rateQueryCustomWindow() {
        String result = builder.buildRateQuery("http_requests_total",
                Map.of(), "10m");
        assertEquals("rate(http_requests_total[10m])", result);
    }

    @Test
    @DisplayName("null labels handled gracefully")
    void nullLabels() {
        assertDoesNotThrow(() -> builder.buildInstantQuery("up", null));
        assertDoesNotThrow(() -> builder.buildRangeQuery("up", null, null, null));
    }

    @Test
    @DisplayName("blank aggregation skipped")
    void blankAggregation() {
        String result = builder.buildRangeQuery("up", Map.of(), "  ", null);
        assertEquals("up", result);
    }
}
