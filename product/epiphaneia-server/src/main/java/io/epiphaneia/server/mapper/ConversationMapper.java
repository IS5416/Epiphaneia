package io.epiphaneia.server.mapper;

import io.epiphaneia.agent.api.model.Conversation;
import io.epiphaneia.agent.api.model.Evidence;
import io.epiphaneia.agent.api.model.FixSuggestion;
import io.epiphaneia.agent.api.model.Message;
import io.epiphaneia.agent.api.model.RootCauseHypothesis;
import io.epiphaneia.server.dto.ConversationDetailResponse;
import io.epiphaneia.server.dto.ConversationResponse;
import io.epiphaneia.server.dto.EvidenceResponse;
import io.epiphaneia.server.dto.FixSuggestionResponse;
import io.epiphaneia.server.dto.MessageResponse;
import io.epiphaneia.server.dto.RootCauseHypothesisResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ConversationMapper {

    @Mapping(source = "application.id", target = "applicationId")
    @Mapping(source = "application.name", target = "applicationName")
    @Mapping(source = "messages", target = "lastMessage")
    ConversationResponse toConversationResponse(Conversation conversation);

    @Mapping(source = "application.id", target = "applicationId")
    @Mapping(source = "application.name", target = "applicationName")
    ConversationDetailResponse toConversationDetailResponse(Conversation conversation);

    MessageResponse toMessageResponse(Message message);

    EvidenceResponse toEvidenceResponse(Evidence evidence);

    RootCauseHypothesisResponse toRootCauseHypothesisResponse(RootCauseHypothesis hypothesis);

    FixSuggestionResponse toFixSuggestionResponse(FixSuggestion suggestion);

    default String mapLastMessage(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        Message last = messages.get(messages.size() - 1);
        return last.getContent();
    }
}
