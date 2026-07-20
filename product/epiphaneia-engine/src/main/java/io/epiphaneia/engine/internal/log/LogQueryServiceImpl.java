package io.epiphaneia.engine.internal.log;

import io.epiphaneia.engine.api.LogQueryService;
import io.epiphaneia.engine.internal.elasticsearch.EsQueryBuilder;
import io.epiphaneia.infra.api.connector.QueryResult;
import org.springframework.stereotype.Service;

/**
 * Builds log queries using {@link EsQueryBuilder}.
 * Actual connector dispatch is handled by the orchestration layer (agent-core).
 */
@Service
public class LogQueryServiceImpl implements LogQueryService {

    private final EsQueryBuilder queryBuilder;

    public LogQueryServiceImpl(EsQueryBuilder queryBuilder) {
        this.queryBuilder = queryBuilder;
    }

    @Override
    public QueryResult query(String datasourceType, String service, String startTime, String endTime) {
        // ponytail: returns placeholder — real query dispatch in Phase 2 orchestration
        String esDsl = queryBuilder.buildErrorLogQuery(service, startTime, endTime);
        return new QueryResult() {};
    }
}
