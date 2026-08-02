package io.epiphaneia.engine.api.actuator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Probes Spring Boot Actuator endpoints to discover application metadata.
 * <p>
 * SSRF protection: validates that the actuator URL uses HTTP/HTTPS and does
 * not target loopback, link-local, or private IP ranges.
 * <p>
 * Environment variable values matching sensitive patterns are identified via
 * {@link #isSensitiveKey(String)} for masking before storage.
 */
@Service
public class ActuatorProbeService {

    private static final Logger log = LoggerFactory.getLogger(ActuatorProbeService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String[] SENSITIVE_PATTERNS =
            {"password", "secret", "credential", "pwd", "passwd", "token", "private_key", "api_key"};
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            // SSRF guard: never follow redirects — a 302 could point at loopback/metadata
            // (169.254.169.254) after validateUrl() approved the original host
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /**
     * Probe a Spring Boot application's Actuator endpoints and return merged info.
     *
     * @param actuatorUrl base Actuator URL (e.g. http://app:8080/actuator)
     * @return JSON string with health, info, and metrics summary
     */
    public String probe(String actuatorUrl) {
        validateUrl(actuatorUrl);
        try {
            ObjectNode root = OBJECT_MAPPER.createObjectNode();
            safeGet(actuatorUrl + "/health", "health", root);
            safeGet(actuatorUrl + "/info", "info", root);
            safeGet(actuatorUrl + "/metrics", "metrics", root);
            return OBJECT_MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("Actuator probe failed for {}: {}", actuatorUrl, e.getMessage());
            return "{\"error\": \"%s\"}".formatted(escape(e.getMessage()));
        }
    }

    static void validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("actuatorUrl must not be blank");
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid actuator URL: " + url, e);
        }
        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("actuatorUrl must use http or https: " + url);
        }
        String host = uri.getHost();
        if (host == null) {
            throw new IllegalArgumentException("actuatorUrl must have a host: " + url);
        }
        try {
            InetAddress addr = InetAddress.getByName(host);
            if (isForbiddenAddress(addr)) {
                throw new IllegalArgumentException(
                        "actuatorUrl must not target local/private addresses: " + url);
            }
        } catch (java.net.UnknownHostException e) {
            throw new IllegalArgumentException("Cannot resolve actuator host: " + host, e);
        }
    }

    /**
     * Loopback/link-local/site-local check that also covers IPv4-mapped IPv6
     * addresses (e.g. {@code ::ffff:127.0.0.1}), which are not caught by the
     * plain InetAddress flag checks.
     */
    static boolean isForbiddenAddress(InetAddress addr) {
        if (addr.isLoopbackAddress() || addr.isLinkLocalAddress() || addr.isSiteLocalAddress()) {
            return true;
        }
        if (addr instanceof Inet6Address v6 && isIpv4Mapped(v6.getAddress())) {
            byte[] b = v6.getAddress();
            // last 4 bytes hold the mapped IPv4 address
            byte[] v4 = {b[12], b[13], b[14], b[15]};
            try {
                return isForbiddenAddress(InetAddress.getByAddress(v4));
            } catch (java.net.UnknownHostException e) {
                return true; // unparsable embedded IPv4 — treat as forbidden
            }
        }
        return false;
    }

    /** IPv4-mapped IPv6 (::ffff:a.b.c.d): first 10 bytes 0, bytes 10-11 0xFF. */
    private static boolean isIpv4Mapped(byte[] b) {
        if (b.length != 16) return false;
        for (int i = 0; i < 10; i++) {
            if (b[i] != 0) return false;
        }
        return b[10] == (byte) 0xFF && b[11] == (byte) 0xFF;
    }

    private void safeGet(String url, String key, ObjectNode root) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body() != null) {
                JsonNode node = OBJECT_MAPPER.readTree(response.body());
                root.set(key, node);
            } else {
                log.debug("Actuator endpoint {} returned status {}", key, response.statusCode());
            }
        } catch (Exception e) {
            log.debug("Actuator endpoint {} unavailable: {}", key, e.getMessage());
        }
    }

    /** Mask sensitive environment variable names. Returns true if the name looks sensitive. */
    public static boolean isSensitiveKey(String key) {
        if (key == null) return false;
        String lower = key.toLowerCase();
        for (String pattern : SENSITIVE_PATTERNS) {
            if (lower.contains(pattern)) return true;
        }
        return false;
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
