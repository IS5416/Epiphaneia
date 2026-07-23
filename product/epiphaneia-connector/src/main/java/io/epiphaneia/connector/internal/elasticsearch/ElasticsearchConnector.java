package io.epiphaneia.connector.internal.elasticsearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.epiphaneia.infra.api.connector.ConnectionConfig;
import io.epiphaneia.infra.api.connector.Connector;
import io.epiphaneia.infra.api.connector.QueryRequest;
import io.epiphaneia.infra.api.connector.QueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Connector for Elasticsearch REST API.
 * <p>
 * Executes JSON DSL queries via POST to {@code /_search}.
 * Connection testing uses {@code /_cluster/health}.
 */
@Component
public class ElasticsearchConnector implements Connector<QueryRequest, QueryResult> {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchConnector.class);
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
        if (request == null) {
            return new QueryResult.Failure("NULL_REQUEST", "Query request must not be null");
        }
        if (request instanceof QueryRequest.Typed(String query, String url)) {
            return executeQuery(query, url);
        }
        return new QueryResult.Failure("UNSUPPORTED_REQUEST",
                "Expected QueryRequest.Typed, got " + request.getClass().getSimpleName());
    }

    private QueryResult executeQuery(String dslJson, String baseUrl) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/_search"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(dslJson))
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 200) {
                JsonNode root = MAPPER.readTree(resp.body());
                int hits = root.path("hits").path("total").path("value").asInt(0);
                String summary = "Elasticsearch returned " + hits + " log entries";
                return new QueryResult.Success(resp.body(), summary);
            }
            return new QueryResult.Failure("HTTP_" + resp.statusCode(),
                    "Elasticsearch returned status " + resp.statusCode());
        } catch (Exception e) {
            log.warn("Elasticsearch query failed: {}", e.getMessage());
            return new QueryResult.Failure("QUERY_FAILED", e.getMessage());
        }
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
