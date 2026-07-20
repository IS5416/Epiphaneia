package io.epiphaneia.server.mapper;

import io.epiphaneia.agent.api.model.LlmProvider;
import io.epiphaneia.server.dto.LlmProviderRequest;
import io.epiphaneia.server.dto.LlmProviderResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface LlmProviderMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "apiKeyEncrypted", ignore = true)
    @Mapping(target = "connected", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    LlmProvider toEntity(LlmProviderRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "apiKeyEncrypted", ignore = true)
    @Mapping(target = "connected", ignore = true)
    void updateEntity(LlmProviderRequest request, @MappingTarget LlmProvider entity);

    LlmProviderResponse toResponse(LlmProvider entity);
}
