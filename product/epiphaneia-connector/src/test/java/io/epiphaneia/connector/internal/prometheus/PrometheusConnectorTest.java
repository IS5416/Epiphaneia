package io.epiphaneia.connector.internal.prometheus;

import io.epiphaneia.infra.api.connector.ConnectionConfig;
import io.epiphaneia.infra.api.connector.DataSourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PrometheusConnectorTest {

    private final PrometheusConnector connector = new PrometheusConnector();

    @Test
    @DisplayName("type returns PROMETHEUS")
    void typeMatches() {
        assertEquals(DataSourceType.PROMETHEUS, connector.type());
    }

    @Test
    @DisplayName("testConnection returns false for invalid URL")
    void invalidUrlReturnsFalse() {
        ConnectionConfig config = new ConnectionConfig("not-a-valid-url", null);
        assertFalse(connector.testConnection(config));
    }

    @Test
    @DisplayName("testConnection returns false for unreachable host")
    void unreachableHostReturnsFalse() {
        ConnectionConfig config = new ConnectionConfig("http://localhost:19999", null);
        assertFalse(connector.testConnection(config));
    }

    @Test
    @DisplayName("query returns non-null result")
    void queryReturnsResult() {
        assertNotNull(connector.query(null));
    }
}
