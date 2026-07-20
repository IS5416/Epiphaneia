package io.epiphaneia.agent.internal.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PromptTemplateManagerTest {

    private static final PromptTemplateManager manager = new PromptTemplateManager();

    @Test
    @DisplayName("loads system template with all key sections")
    void loadSystemTemplate() {
        String tpl = manager.load("system");
        assertNotNull(tpl);
        assertTrue(tpl.contains("Epiphaneia"));
        assertTrue(tpl.contains("READ-ONLY"));
        assertTrue(tpl.contains("{{applicationName}}"));
    }

    @Test
    @DisplayName("loads planning template")
    void loadPlanningTemplate() {
        String tpl = manager.load("planning");
        assertNotNull(tpl);
        assertTrue(tpl.contains("{{question}}"));
    }

    @Test
    @DisplayName("loads analysis template")
    void loadAnalysisTemplate() {
        String tpl = manager.load("analysis");
        assertNotNull(tpl);
        assertTrue(tpl.contains("{{evidence}}"));
    }

    @Test
    @DisplayName("loads report template")
    void loadReportTemplate() {
        String tpl = manager.load("report");
        assertNotNull(tpl);
        assertTrue(tpl.contains("Diagnostic Report"));
        assertTrue(tpl.contains("{{hypotheses}}"));
    }

    @Test
    @DisplayName("interpolate replaces all placeholders")
    void interpolateReplacesAll() {
        String result = manager.interpolate("system", Map.of(
                "applicationName", "user-service",
                "dataSources", "Prometheus"));
        assertTrue(result.contains("user-service"));
        assertFalse(result.contains("{{applicationName}}"));
    }

    @Test
    @DisplayName("interpolate with no params leaves placeholders")
    void interpolateNoParams() {
        String result = manager.interpolate("system", Map.of());
        assertTrue(result.contains("{{applicationName}}"));
    }

    @Test
    @DisplayName("load nonexistent template throws")
    void loadMissing() {
        assertThrows(IllegalArgumentException.class, () -> manager.load("nonexistent"));
    }

    @Test
    @DisplayName("template caching: same instance returned")
    void templateCaching() {
        assertSame(manager.load("system"), manager.load("system"));
    }
}
