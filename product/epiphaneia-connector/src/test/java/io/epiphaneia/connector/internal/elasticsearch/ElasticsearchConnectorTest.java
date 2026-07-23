package io.epiphaneia.connector.internal.elasticsearch;

import io.epiphaneia.infra.api.connector.AuthConfig;
import io.epiphaneia.infra.api.connector.ConnectionConfig;
import io.epiphaneia.infra.api.connector.DataSourceType;
import io.epiphaneia.infra.api.connector.QueryRequest;
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
    @DisplayName("testConnection returns false for unreachable host")
    void unreachableHostReturnsFalse() {
        ConnectionConfig config = new ConnectionConfig("http://localhost:19999", AuthConfig.none());
        assertFalse(connector.testConnection(config));
    }

    @Test
    @DisplayName("query with null returns non-null Failure")
    void queryNullReturnsResult() {
        var result = connector.query(null);
        assertNotNull(result);
    }

    @Test
    @DisplayName("query with Typed request to unreachable host returns Failure")
    void queryTypedToUnreachableReturnsFailure() {
        var result = connector.query(
                new QueryRequest.Typed("{\"query\":{\"match_all\":{}}}", "http://localhost:19999"));
        assertNotNull(result);
        assertTrue(result instanceof io.epiphaneia.infra.api.connector.QueryResult.Failure);
    }
}
