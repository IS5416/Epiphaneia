package io.epiphaneia.connector.internal.elasticsearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.epiphaneia.infra.api.connector.ConnectionConfig;
import io.epiphaneia.infra.api.connector.Connector;
import io.epiphaneia.infra.api.connector.QueryRequest;
import io.epiphaneia.infra.api.connector.QueryResult;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Connector for Elasticsearch REST API.
 * <p>
 * Queries the {@code /_search} endpoint with JSON DSL bodies.
 * Connection testing uses {@code /_cluster/health}.
 */
@Component
public class ElasticsearchConnector implements Connector<QueryRequest, QueryResult> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public String type() {
        return io.epiphaneia.infra.api.connector.DataSourceType.ELASTICSEARCH;
    }

    @Override
    public QueryResult query(QueryRequest request) {
        // ponytail: generic query — Phase 2 orchestration will pass typed EsQueryRequest
        return new QueryResult() {};
    }

    @Override
    public boolean testConnection(ConnectionConfig config) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(config.url() + "/_cluster/health"))
                    .timeout(Duration.ofSeconds(3))
                    .GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = MAPPER.readTree(resp.body());
                return root.has("cluster_name");
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
