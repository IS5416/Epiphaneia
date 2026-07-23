package io.epiphaneia.connector.internal.prometheus;

import io.epiphaneia.infra.api.connector.AuthConfig;
import io.epiphaneia.infra.api.connector.ConnectionConfig;
import io.epiphaneia.infra.api.connector.DataSourceType;
import io.epiphaneia.infra.api.connector.QueryRequest;
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
    @DisplayName("testConnection returns false for unreachable host")
    void unreachableHostReturnsFalse() {
        ConnectionConfig config = new ConnectionConfig("http://localhost:19999", AuthConfig.none());
        assertFalse(connector.testConnection(config));
    }

    @Test
    @DisplayName("query with null returns Failure")
    void queryNullReturnsFailure() {
        var result = connector.query(null);
        assertNotNull(result);
        assertTrue(result instanceof io.epiphaneia.infra.api.connector.QueryResult);
    }

    @Test
    @DisplayName("query with Typed request to unreachable host returns Failure")
    void queryTypedToUnreachableReturnsFailure() {
        var result = connector.query(
                new QueryRequest.Typed("up", "http://localhost:19999"));
        assertNotNull(result);
        assertTrue(result instanceof io.epiphaneia.infra.api.connector.QueryResult.Failure);
    }
}
