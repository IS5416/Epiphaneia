package io.epiphaneia.agent.api;

import io.epiphaneia.domain.internal.entity.Conversation;

public interface ReportSynthesizer {
    String synthesize(Conversation conversation);
}
