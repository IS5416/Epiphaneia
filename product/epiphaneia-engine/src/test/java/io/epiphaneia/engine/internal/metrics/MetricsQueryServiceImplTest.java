package io.epiphaneia.engine.internal.metrics;

import io.epiphaneia.engine.internal.prometheus.PrometheusQueryBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MetricsQueryServiceImplTest {

    private final PrometheusQueryBuilder builder = new PrometheusQueryBuilder();
    private final MetricsQueryServiceImpl service = new MetricsQueryServiceImpl(builder);

    @Test
    @DisplayName("query returns non-null QueryResult")
    void queryReturnsResult() {
        assertNotNull(service.query("PROMETHEUS", "up", "now"));
    }

    @Test
    @DisplayName("query with null metric name does not throw")
    void queryNullMetric() {
        assertDoesNotThrow(() -> service.query("PROMETHEUS", null, "now"));
    }
}
