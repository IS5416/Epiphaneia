package io.epiphaneia.server.mapper;

import io.epiphaneia.domain.internal.entity.Application;
import io.epiphaneia.server.dto.ApplicationRequest;
import io.epiphaneia.server.dto.ApplicationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ApplicationMapper {

    Application toEntity(ApplicationRequest request);

    void updateEntity(ApplicationRequest request, @MappingTarget Application entity);

    ApplicationResponse toResponse(Application entity);
}
