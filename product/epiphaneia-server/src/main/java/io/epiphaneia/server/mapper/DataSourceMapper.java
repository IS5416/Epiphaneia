package io.epiphaneia.server.mapper;

import io.epiphaneia.agent.api.model.DataSource;
import io.epiphaneia.server.dto.DataSourceRequest;
import io.epiphaneia.server.dto.DataSourceResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface DataSourceMapper {

    DataSource toEntity(DataSourceRequest request);

    void updateEntity(DataSourceRequest request, @MappingTarget DataSource entity);

    DataSourceResponse toResponse(DataSource entity);
}
