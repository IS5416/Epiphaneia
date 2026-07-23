package io.epiphaneia.server.controller;

import io.epiphaneia.domain.internal.entity.DataSource;
import io.epiphaneia.domain.internal.repository.DataSourceRepository;
import io.epiphaneia.infra.api.ConnectorRegistry;
import io.epiphaneia.infra.api.connector.AuthConfig;
import io.epiphaneia.infra.api.connector.ConnectionConfig;
import io.epiphaneia.server.dto.*;
import io.epiphaneia.server.mapper.DataSourceMapper;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/datasources")
public class DataSourceController {

    private static final Logger log = LoggerFactory.getLogger(DataSourceController.class);

    private final DataSourceRepository dsRepo;
    private final DataSourceMapper mapper;
    private final ConnectorRegistry connectorRegistry;

    public DataSourceController(DataSourceRepository dsRepo, DataSourceMapper mapper,
                                 ConnectorRegistry connectorRegistry) {
        this.dsRepo = dsRepo;
        this.mapper = mapper;
        this.connectorRegistry = connectorRegistry;
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
        try {
            var connector = connectorRegistry.getConnector(ds.getType());
            AuthConfig auth = switch (ds.getAuthType()) {
                case "BASIC" -> AuthConfig.basic("", "");
                case "BEARER" -> AuthConfig.bearer("");
                default -> AuthConfig.none();
            };
            var config = new ConnectionConfig(ds.getUrl(), auth);
            boolean ok = connector.testConnection(config);
            ds.setConnected(ok);
            dsRepo.save(ds);
            String msg = ok ? "Connection successful"
                    : "Connection failed — check URL and network";
            return ApiResponse.ok(new TestConnectionResponse(ok, msg));
        } catch (Exception e) {
            log.warn("Connection test failed for data source {}: {}", ds.getName(), e.getMessage());
            return ApiResponse.ok(new TestConnectionResponse(false,
                    "Connection test error: " + e.getMessage()));
        }
    }
}
