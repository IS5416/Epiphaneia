package io.epiphaneia.agent.api;
public interface DiagnosisOrchestrator {
    Object execute(Object conversation, String question);
}
