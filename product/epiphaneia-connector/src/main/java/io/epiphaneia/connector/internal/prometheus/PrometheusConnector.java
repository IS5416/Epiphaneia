package io.epiphaneia.connector.internal.prometheus;

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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Connector for Prometheus HTTP API.
 * <p>
 * Executes PromQL queries against {@code /api/v1/query} (instant) and
 * {@code /api/v1/query_range} (range). Connection testing uses
 * {@code /api/v1/status/buildinfo}.
 */
@Component
public class PrometheusConnector implements Connector<QueryRequest, QueryResult> {

    private static final Logger log = LoggerFactory.getLogger(PrometheusConnector.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public String type() {
        return io.epiphaneia.infra.api.connector.DataSourceType.PROMETHEUS;
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

    private QueryResult executeQuery(String promql, String baseUrl) {
        try {
            String encoded = URLEncoder.encode(promql, StandardCharsets.UTF_8);
            String apiUrl = baseUrl + "/api/v1/query?query=" + encoded;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(10))
                    .GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 200) {
                JsonNode root = MAPPER.readTree(resp.body());
                String status = root.path("status").asText();
                if ("success".equals(status)) {
                    JsonNode result = root.path("data").path("result");
                    int count = result.isArray() ? result.size() : 0;
                    String summary = "Prometheus returned " + count + " time series";
                    return new QueryResult.Success(resp.body(), summary);
                }
                String errorMsg = root.path("error").asText("unknown error");
                return new QueryResult.Failure("PROMETHEUS_ERROR", errorMsg);
            }
            return new QueryResult.Failure("HTTP_" + resp.statusCode(),
                    "Prometheus returned status " + resp.statusCode());
        } catch (Exception e) {
            log.warn("Prometheus query failed: {}", e.getMessage());
            return new QueryResult.Failure("QUERY_FAILED", e.getMessage());
        }
    }

    @Override
    public boolean testConnection(ConnectionConfig config) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(config.url() + "/api/v1/status/buildinfo"))
                    .timeout(Duration.ofSeconds(3))
                    .GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = MAPPER.readTree(resp.body());
                return "success".equals(root.path("status").asText());
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
