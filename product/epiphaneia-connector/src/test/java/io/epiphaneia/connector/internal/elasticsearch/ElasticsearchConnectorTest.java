package io.epiphaneia.connector.internal.elasticsearch;

import io.epiphaneia.infra.api.connector.ConnectionConfig;
import io.epiphaneia.infra.api.connector.DataSourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ElasticsearchConnectorTest {

    private final ElasticsearchConnector connector = new ElasticsearchConnector();

    @Test
    @DisplayName("type returns ELASTICSEARCH")
    void typeMatches() {
        assertEquals(DataSourceType.ELASTICSEARCH, connector.type());
    }

    @Test
    @DisplayName("testConnection returns false for invalid URL")
    void invalidUrlReturnsFalse() {
        ConnectionConfig config = new ConnectionConfig("not-a-url", null);
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
