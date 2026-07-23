package io.epiphaneia.server.mapper;

import io.epiphaneia.domain.internal.entity.ApiToken;
import io.epiphaneia.server.dto.ApiTokenResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ApiTokenMapper {

    @Mapping(target = "id", source = "id")
    ApiTokenResponse toResponse(ApiToken entity);
}
