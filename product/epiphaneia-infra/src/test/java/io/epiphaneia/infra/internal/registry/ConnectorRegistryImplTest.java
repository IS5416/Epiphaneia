package io.epiphaneia.infra.internal.registry;

import io.epiphaneia.infra.api.ConnectorRegistry;
import io.epiphaneia.infra.api.connector.ConnectionConfig;
import io.epiphaneia.infra.api.connector.Connector;
import io.epiphaneia.infra.api.connector.QueryRequest;
import io.epiphaneia.infra.api.connector.QueryResult;
import io.epiphaneia.infra.internal.exception.InvalidConfigurationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConnectorRegistryImplTest {

    record StubQueryRequest(String data) implements QueryRequest {}
    record StubQueryResult(String result) implements QueryResult {}

    static Connector<StubQueryRequest, StubQueryResult> stubConnector(String type) {
        return new Connector<>() {
            @Override public String type() { return type; }
            @Override public StubQueryResult query(StubQueryRequest r) { return new StubQueryResult(r.data); }
            @Override public boolean testConnection(ConnectionConfig c) { return true; }
        };
    }

    @Test
    @DisplayName("getConnector returns connector registered for type")
    void getByType() {
        Connector<StubQueryRequest, StubQueryResult> c = stubConnector("PROMETHEUS");
        ConnectorRegistry registry = new ConnectorRegistryImpl(List.of(c));
        assertEquals(c, registry.getConnector("PROMETHEUS"));
    }

    @Test
    @DisplayName("getConnector with unknown type throws InvalidConfigurationException")
    void unknownType() {
        ConnectorRegistry registry = new ConnectorRegistryImpl(List.of(stubConnector("PROMETHEUS")));
        assertThrows(InvalidConfigurationException.class, () -> registry.getConnector("UNKNOWN"));
    }

    @Test
    @DisplayName("listAll returns all registered connectors")
    void listAll() {
        var a = stubConnector("PROMETHEUS");
        var b = stubConnector("ELASTICSEARCH");
        ConnectorRegistry registry = new ConnectorRegistryImpl(List.of(a, b));
        List<Connector<?, ?>> all = registry.listAll();
        assertEquals(2, all.size());
        assertTrue(all.contains(a));
        assertTrue(all.contains(b));
    }

    @Test
    @DisplayName("duplicate type registration throws at construction")
    void duplicateType() {
        assertThrows(InvalidConfigurationException.class,
                () -> new ConnectorRegistryImpl(List.of(
                        stubConnector("PROMETHEUS"), stubConnector("PROMETHEUS"))));
    }

    @Test
    @DisplayName("empty connector list works without error")
    void emptyList() {
        ConnectorRegistry registry = new ConnectorRegistryImpl(List.of());
        assertTrue(registry.listAll().isEmpty());
        assertThrows(InvalidConfigurationException.class, () -> registry.getConnector("ANY"));
    }

    @Test
    @DisplayName("testConnection delegates to connector")
    void testConnection() {
        ConnectorRegistry registry = new ConnectorRegistryImpl(List.of(stubConnector("PROMETHEUS")));
        assertTrue(registry.testConnection("PROMETHEUS", null));
    }

    @Test
    @DisplayName("testConnection with unknown type throws")
    void testConnectionUnknown() {
        ConnectorRegistry registry = new ConnectorRegistryImpl(List.of());
        assertThrows(InvalidConfigurationException.class,
                () -> registry.testConnection("PROMETHEUS", null));
    }
}
