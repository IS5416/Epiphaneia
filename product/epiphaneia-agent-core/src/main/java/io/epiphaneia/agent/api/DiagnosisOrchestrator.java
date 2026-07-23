package io.epiphaneia.agent.api;

import io.epiphaneia.llm.api.DiagnosisSseEventPublisher;

public interface DiagnosisOrchestrator {
    void execute(DiagnosisContext ctx, DiagnosisSseEventPublisher publisher);
}
