package io.epiphaneia.agent.api;

import io.epiphaneia.domain.internal.entity.Application;
import io.epiphaneia.domain.internal.entity.Conversation;
import io.epiphaneia.domain.internal.entity.DataSource;
import io.epiphaneia.domain.internal.entity.LlmProvider;
import io.epiphaneia.domain.internal.entity.Message;

import java.util.List;

/**
 * Immutable context carrying all information needed through the diagnosis pipeline.
 * <p>
 * Created once at diagnosis start and passed through each phase. Phases may
 * produce new evidence, hypotheses, and suggestions that are appended to the
 * context's message.
 */
public record DiagnosisContext(
        Conversation conversation,
        Message message,
        Application application,
        LlmProvider llmProvider,
        List<DataSource> dataSources,
        String question
) {
    public DiagnosisContext {
        if (conversation == null) throw new IllegalArgumentException("conversation required");
        if (message == null) throw new IllegalArgumentException("message required");
        if (llmProvider == null) throw new IllegalArgumentException("llmProvider required");
        if (question == null || question.isBlank()) throw new IllegalArgumentException("question required");
    }
}
