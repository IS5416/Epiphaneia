package io.epiphaneia.engine.internal.elasticsearch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EsQueryBuilderTest {

    private final EsQueryBuilder builder = new EsQueryBuilder();

    @Test
    @DisplayName("builds search query with query string")
    void searchQueryWithString() {
        String result = builder.buildSearchQuery("NullPointerException", "2024-01-01T00:00:00Z",
                "2024-01-01T01:00:00Z", 20);
        assertTrue(result.contains("\"query_string\""));
        assertTrue(result.contains("NullPointerException"));
        assertTrue(result.contains("\"size\": 20"));
        assertTrue(result.contains("\"gte\": \"2024-01-01T00:00:00Z\""));
    }

    @Test
    @DisplayName("builds search query with match_all when query is null")
    void searchQueryNoQuery() {
        String result = builder.buildSearchQuery(null, "now-1h", "now", 50);
        assertTrue(result.contains("\"match_all\": {}"));
    }

    @Test
    @DisplayName("builds search query with blank query string")
    void searchQueryBlank() {
        String result = builder.buildSearchQuery("  ", "now-1h", "now", 50);
        assertTrue(result.contains("\"match_all\": {}"));
    }

    @Test
    @DisplayName("default size is 50 when non-positive")
    void defaultSize() {
        String result = builder.buildSearchQuery("error", "s", "e", 0);
        assertTrue(result.contains("\"size\": 50"));
    }

    @Test
    @DisplayName("builds error log query for service")
    void errorLogQuery() {
        String result = builder.buildErrorLogQuery("user-service", "now-1h", "now");
        assertTrue(result.contains("\"service\": \"user-service\""));
        assertTrue(result.contains("\"level\": \"ERROR\""));
        assertTrue(result.contains("\"size\": 50"));
    }

    @Test
    @DisplayName("escapes special characters in query string")
    void escapeSpecialChars() {
        String result = builder.buildSearchQuery("log: \"error\" in \\path",
                "now-1h", "now", 10);
        assertTrue(result.contains("log: \\\"error\\\" in \\\\path"));
    }

    @Test
    @DisplayName("produces valid JSON-like structure")
    void validJsonStructure() {
        String result = builder.buildSearchQuery("test", "start", "end", 10);
        assertTrue(result.trim().startsWith("{"));
        assertTrue(result.trim().endsWith("}"));
        assertTrue(result.contains("\"query\""));
        assertTrue(result.contains("\"sort\""));
    }
}
