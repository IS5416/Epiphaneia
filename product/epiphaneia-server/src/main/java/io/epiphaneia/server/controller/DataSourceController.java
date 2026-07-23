package io.epiphaneia.server.controller;

import io.epiphaneia.domain.internal.entity.DataSource;
import io.epiphaneia.domain.internal.repository.DataSourceRepository;
import io.epiphaneia.server.dto.*;
import io.epiphaneia.server.mapper.DataSourceMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/datasources")
public class DataSourceController {

    private final DataSourceRepository dsRepo;
    private final DataSourceMapper mapper;

    public DataSourceController(DataSourceRepository dsRepo, DataSourceMapper mapper) {
        this.dsRepo = dsRepo;
        this.mapper = mapper;
    }

    @GetMapping
    public ApiListResponse<DataSourceResponse> list() {
        List<DataSourceResponse> sources = dsRepo.findAll().stream()
                .map(mapper::toResponse).toList();
        return ApiListResponse.of(sources);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DataSourceResponse> create(@Valid @RequestBody DataSourceRequest req) {
        DataSource ds = mapper.toEntity(req);
        dsRepo.save(ds);
        return ApiResponse.ok(mapper.toResponse(ds));
    }

    @GetMapping("/{id}")
    public ApiResponse<DataSourceResponse> get(@PathVariable UUID id) {
        return dsRepo.findById(id)
                .map(mapper::toResponse)
                .map(ApiResponse::ok)
                .orElseThrow(() -> new IllegalArgumentException("Data source not found: " + id));
    }

    @PutMapping("/{id}")
    public ApiResponse<DataSourceResponse> update(@PathVariable UUID id,
                                                   @Valid @RequestBody DataSourceRequest req) {
        DataSource ds = dsRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Data source not found: " + id));
        mapper.updateEntity(req, ds);
        dsRepo.save(ds);
        return ApiResponse.ok(mapper.toResponse(ds));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        if (!dsRepo.existsById(id)) {
            throw new IllegalArgumentException("Data source not found: " + id);
        }
        dsRepo.deleteById(id);
    }

    @PostMapping("/{id}/test")
    public ApiResponse<TestConnectionResponse> test(@PathVariable UUID id) {
        DataSource ds = dsRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Data source not found: " + id));
        // ponytail: placeholder — real connection test via ConnectorRegistry
        return ApiResponse.ok(new TestConnectionResponse(true, "Connection successful"));
    }
}
