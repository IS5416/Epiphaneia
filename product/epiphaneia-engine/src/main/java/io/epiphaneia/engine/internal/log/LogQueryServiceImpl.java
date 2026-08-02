package io.epiphaneia.engine.internal.log;

import io.epiphaneia.engine.api.LogQueryService;
import io.epiphaneia.engine.api.query.EsQueryBuilder;
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
        if (!"ELASTICSEARCH".equalsIgnoreCase(datasourceType)) {
            return new QueryResult.Failure("UNSUPPORTED_DATASOURCE",
                    "LogQueryService only supports ELASTICSEARCH, got " + datasourceType);
        }
        // ponytail: builds the DSL here; actual connector dispatch happens in agent-core
        String esDsl = queryBuilder.buildErrorLogQuery(service, startTime, endTime);
        return new QueryResult.Failure("NOT_IMPLEMENTED",
                "Direct dispatch removed — route through DiagnosisOrchestrator (query built: " + esDsl + ")");
    }
}
