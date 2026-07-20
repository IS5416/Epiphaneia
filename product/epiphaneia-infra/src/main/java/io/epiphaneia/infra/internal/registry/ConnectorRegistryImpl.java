package io.epiphaneia.infra.internal.registry;

import io.epiphaneia.infra.api.ConnectorRegistry;
import io.epiphaneia.infra.api.connector.Connector;
import io.epiphaneia.infra.api.connector.ConnectionConfig;
import io.epiphaneia.infra.internal.exception.InvalidConfigurationException;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Auto-discovers Connector implementations via Spring bean injection.
 * <p>
 * All {@code @Component}-annotated {@link Connector} beans are collected at
 * construction time and indexed by their {@link Connector#type()} value.
 * Duplicate type registrations are rejected at startup.
 */
@Component
public class ConnectorRegistryImpl implements ConnectorRegistry {

    private final Map<String, Connector<?, ?>> connectors;

    public ConnectorRegistryImpl(List<Connector<?, ?>> connectorList) {
        if (connectorList == null) {
            connectorList = Collections.emptyList();
        }
        this.connectors = connectorList.stream()
                .collect(Collectors.toMap(
                        Connector::type,
                        Function.identity(),
                        (a, b) -> { throw new InvalidConfigurationException(
                                "Duplicate Connector type: " + a.type()); }));
    }

    @Override
    public Connector<?, ?> getConnector(String type) {
        Connector<?, ?> connector = connectors.get(type);
        if (connector == null) {
            throw new InvalidConfigurationException(
                    "No Connector registered for type: " + type);
        }
        return connector;
    }

    @Override
    public List<Connector<?, ?>> listAll() {
        return List.copyOf(connectors.values());
    }

    @Override
    public boolean testConnection(String type, ConnectionConfig config) {
        return getConnector(type).testConnection(config);
    }
}
