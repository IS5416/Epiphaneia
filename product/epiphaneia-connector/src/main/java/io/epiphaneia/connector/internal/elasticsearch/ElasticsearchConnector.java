package io.epiphaneia.connector.internal.elasticsearch;

import io.epiphaneia.infra.api.connector.ConnectionConfig;
import io.epiphaneia.infra.api.connector.Connector;
import io.epiphaneia.infra.api.connector.QueryRequest;
import io.epiphaneia.infra.api.connector.QueryResult;
import org.springframework.stereotype.Component;

/** Connector implementation for Elasticsearch REST API. */
@Component
public class ElasticsearchConnector implements Connector<QueryRequest, QueryResult> {

    @Override
    public String type() {
        return io.epiphaneia.infra.api.connector.DataSourceType.ELASTICSEARCH;
    }

    @Override
    public QueryResult query(QueryRequest request) {
        throw new UnsupportedOperationException("Not implemented — Development phase");
    }

    @Override
    public boolean testConnection(ConnectionConfig config) {
        throw new UnsupportedOperationException("Not implemented — Development phase");
    }
}
