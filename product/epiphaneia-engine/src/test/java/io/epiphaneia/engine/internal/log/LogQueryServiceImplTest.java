package io.epiphaneia.engine.internal.log;

import io.epiphaneia.engine.internal.elasticsearch.EsQueryBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LogQueryServiceImplTest {

    private final EsQueryBuilder builder = new EsQueryBuilder();
    private final LogQueryServiceImpl service = new LogQueryServiceImpl(builder);

    @Test
    @DisplayName("query returns non-null QueryResult")
    void queryReturnsResult() {
        assertNotNull(service.query("ELASTICSEARCH", "test-svc", "now-1h", "now"));
    }

    @Test
    @DisplayName("query with null service uses escape fallback")
    void queryNullService() {
        assertNotNull(service.query("ELASTICSEARCH", null, "now-1h", "now"));
    }
}
