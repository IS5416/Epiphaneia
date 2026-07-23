package io.epiphaneia.server.mapper;

import io.epiphaneia.domain.internal.entity.Conversation;
import io.epiphaneia.domain.internal.entity.Evidence;
import io.epiphaneia.domain.internal.entity.FixSuggestion;
import io.epiphaneia.domain.internal.entity.Message;
import io.epiphaneia.domain.internal.entity.RootCauseHypothesis;
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
