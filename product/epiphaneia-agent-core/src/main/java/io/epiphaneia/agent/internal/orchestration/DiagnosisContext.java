package io.epiphaneia.agent.internal.orchestration;

import io.epiphaneia.agent.api.model.Application;
import io.epiphaneia.agent.api.model.Conversation;
import io.epiphaneia.agent.api.model.DataSource;
import io.epiphaneia.agent.api.model.LlmProvider;
import io.epiphaneia.agent.api.model.Message;

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
