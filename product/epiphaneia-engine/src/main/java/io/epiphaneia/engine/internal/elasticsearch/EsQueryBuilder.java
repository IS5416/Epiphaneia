package io.epiphaneia.engine.internal.elasticsearch;

import org.springframework.stereotype.Component;

/**
 * Builds Elasticsearch DSL query bodies from structured intent parameters.
 * <p>
 * Produces JSON strings suitable for POST to {@code /_search}.
 * This is NOT an LLM-based builder — the orchestration layer converts
 * natural language intent into structured parameters first.
 */
@Component
public class EsQueryBuilder {

    private static final int DEFAULT_SIZE = 50;

    /**
     * Build a log search query for a time range with optional query string.
     */
    public String buildSearchQuery(String queryString, String startTime, String endTime, int size) {
        return """
                {
                  "query": {
                    "bool": {
                      "must": [
                        %s
                      ],
                      "filter": [
                        {
                          "range": {
                            "@timestamp": {
                              "gte": "%s",
                              "lte": "%s"
                            }
                          }
                        }
                      ]
                    }
                  },
                  "size": %d,
                  "sort": [{"@timestamp": "desc"}]
                }
                """.formatted(
                queryString != null && !queryString.isBlank()
                        ? "{\"query_string\": {\"query\": \"" + escapeLucene(queryString) + "\"}}"
                        : "{\"match_all\": {}}",
                escape(startTime), escape(endTime),
                size > 0 ? size : DEFAULT_SIZE);
    }

    /**
     * Build a query to find error-level logs for a service in a time range.
     */
    public String buildErrorLogQuery(String service, String start, String end) {
        return """
                {
                  "query": {
                    "bool": {
                      "must": [
                        {"term": {"service": "%s"}},
                        {"term": {"level": "ERROR"}}
                      ],
                      "filter": [
                        {
                          "range": {
                            "@timestamp": {"gte": "%s", "lte": "%s"}
                          }
                        }
                      ]
                    }
                  },
                  "size": %d,
                  "sort": [{"@timestamp": "desc"}]
                }
                """.formatted(escape(service), escape(start), escape(end), DEFAULT_SIZE);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    /** Escape Lucene query string special characters to prevent query injection. */
    private static String escapeLucene(String s) {
        if (s == null) return "";
        // Escape special Lucene characters: + - = && || > < ! ( ) { } [ ] ^ " ~ * ? : \ /
        return s.replace("\\", "\\\\")
                .replace("+", "\\+")
                .replace("-", "\\-")
                .replace("=", "\\=")
                .replace("&&", "\\&&")
                .replace("||", "\\||")
                .replace(">", "\\>")
                .replace("<", "\\<")
                .replace("!", "\\!")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("^", "\\^")
                .replace("\"", "\\\"")
                .replace("~", "\\~")
                .replace("*", "\\*")
                .replace("?", "\\?")
                .replace(":", "\\:")
                .replace("/", "\\/");
    }
}
