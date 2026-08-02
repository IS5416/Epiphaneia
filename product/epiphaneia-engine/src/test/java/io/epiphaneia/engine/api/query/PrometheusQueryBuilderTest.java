package io.epiphaneia.engine.api.query;

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
    @DisplayName("instant query rejects null metric (consistent with range/rate)")
    void instantQueryNullMetricThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> builder.buildInstantQuery(null, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> builder.buildInstantQuery("  ", Map.of()));
    }

    @Test
    @DisplayName("blank aggregation skipped")
    void blankAggregation() {
        String result = builder.buildRangeQuery("up", Map.of(), "  ", null);
        assertEquals("up", result);
    }

    @Test
    @DisplayName("range window stays inside aggregation with balanced parens")
    void aggregationRangeWindow() {
        String result = builder.buildRangeQuery("http_requests_total",
                Map.of("job", "api"), "rate", "5m");
        assertEquals("rate(http_requests_total{job=\"api\"}[5m])", result);
        // parens must balance: rate( ... [5m] )
        assertEquals(count('(', result), count(')', result));
    }

    @Test
    @DisplayName("nested-looking aggregation name is rejected (would break PromQL)")
    void nestedAggregationNameRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> builder.buildRangeQuery("http_requests_total",
                        Map.of("job", "api"), "sum(rate", "5m"));
        assertThrows(IllegalArgumentException.class,
                () -> builder.buildRangeQuery("http_requests_total",
                        Map.of(), "sum(rate)", "5m"));
    }

    @Test
    @DisplayName("null metric throws (no silent NPE)")
    void nullMetricThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> builder.buildRangeQuery(null, Map.of(), "rate", "5m"));
        assertThrows(IllegalArgumentException.class,
                () -> builder.buildRangeQuery("  ", Map.of(), "rate", "5m"));
        assertThrows(IllegalArgumentException.class,
                () -> builder.buildRateQuery(null, Map.of(), "5m"));
    }

    private static long count(char c, String s) {
        return s.chars().filter(ch -> ch == c).count();
    }

    @Test
    @DisplayName("invalid label name throws instead of producing broken PromQL")
    void invalidLabelNameThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> builder.buildInstantQuery("up", Map.of("bad-label", "x")));
        assertThrows(IllegalArgumentException.class,
                () -> builder.buildInstantQuery("up", Map.of("1label", "x")));
    }

    @Test
    @DisplayName("label value with CR is escaped")
    void labelValueCrEscaped() {
        String result = builder.buildInstantQuery("up", Map.of("job", "a\rb"));
        assertEquals("up{job=\"a\\rb\"}", result);
    }
}
