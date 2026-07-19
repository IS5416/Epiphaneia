package io.epiphaneia.infra.api;

import io.epiphaneia.infra.api.connector.Connector;
import io.epiphaneia.infra.api.connector.ConnectionConfig;
import java.util.List;

/** Registry for discovering Connector implementations by data source type. */
public interface ConnectorRegistry {

    Connector<?, ?> getConnector(String type);

    List<Connector<?, ?>> listAll();

    boolean testConnection(String type, ConnectionConfig config);
}
