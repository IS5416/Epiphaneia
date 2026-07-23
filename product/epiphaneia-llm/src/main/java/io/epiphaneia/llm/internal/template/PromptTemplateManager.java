package io.epiphaneia.llm.internal.template;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads and caches prompt templates from classpath, supports parameter interpolation.
 * <p>
 * Templates are stored as {@code prompts/<name>.txt} on the classpath.
 * Parameters are interpolated via {@code {{key}}} placeholder syntax.
 */
@Component
public class PromptTemplateManager {

    private static final String TEMPLATE_PATH = "prompts/%s.txt";

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String load(String templateName) {
        return cache.computeIfAbsent(templateName, this::readTemplate);
    }

    public String interpolate(String templateName, Map<String, String> params) {
        String template = load(templateName);
        String result = template;
        // Sort by key length descending to prevent overlapping placeholder corruption
        // e.g., {{name}} before {{nameExtra}} would break the latter
        var sorted = params.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()))
                .toList();
        for (var entry : sorted) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    private String readTemplate(String name) {
        String path = TEMPLATE_PATH.formatted(name);
        try {
            return new ClassPathResource(path)
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to load prompt template: " + path, e);
        }
    }
}
