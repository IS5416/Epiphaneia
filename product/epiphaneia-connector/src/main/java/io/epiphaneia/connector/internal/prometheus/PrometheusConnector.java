package io.epiphaneia.connector.internal.prometheus;

import io.epiphaneia.infra.api.connector.ConnectionConfig;
import io.epiphaneia.infra.api.connector.Connector;
import io.epiphaneia.infra.api.connector.QueryRequest;
import io.epiphaneia.infra.api.connector.QueryResult;
import org.springframework.stereotype.Component;

/** Connector implementation for Prometheus HTTP API. */
@Component
public class PrometheusConnector implements Connector<QueryRequest, QueryResult> {

    @Override
    public String type() {
        return io.epiphaneia.infra.api.connector.DataSourceType.PROMETHEUS;
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
