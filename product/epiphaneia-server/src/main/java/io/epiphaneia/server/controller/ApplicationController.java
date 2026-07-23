package io.epiphaneia.server.controller;

import io.epiphaneia.domain.internal.entity.Application;
import io.epiphaneia.domain.internal.repository.ApplicationRepository;
import io.epiphaneia.engine.internal.actuator.ActuatorProbeService;
import io.epiphaneia.server.dto.*;
import io.epiphaneia.server.mapper.ApplicationMapper;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationController {

    private static final Logger log = LoggerFactory.getLogger(ApplicationController.class);

    private final ApplicationRepository appRepo;
    private final ApplicationMapper mapper;
    private final ActuatorProbeService probeService;

    public ApplicationController(ApplicationRepository appRepo, ApplicationMapper mapper,
                                  ActuatorProbeService probeService) {
        this.appRepo = appRepo;
        this.mapper = mapper;
        this.probeService = probeService;
    }

    @GetMapping
    public ApiListResponse<ApplicationResponse> list() {
        List<ApplicationResponse> apps = appRepo.findAll().stream()
                .map(mapper::toResponse).toList();
        return ApiListResponse.of(apps);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ApplicationResponse> create(@Valid @RequestBody ApplicationRequest req) {
        Application app = mapper.toEntity(req);
        appRepo.save(app);
        return ApiResponse.ok(mapper.toResponse(app));
    }

    @GetMapping("/{id}")
    public ApiResponse<ApplicationResponse> get(@PathVariable UUID id) {
        return appRepo.findById(id)
                .map(mapper::toResponse)
                .map(ApiResponse::ok)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + id));
    }

    @PutMapping("/{id}")
    public ApiResponse<ApplicationResponse> update(@PathVariable UUID id,
                                                    @Valid @RequestBody ApplicationRequest req) {
        Application app = appRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + id));
        mapper.updateEntity(req, app);
        appRepo.save(app);
        return ApiResponse.ok(mapper.toResponse(app));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        if (!appRepo.existsById(id)) {
            throw new IllegalArgumentException("Application not found: " + id);
        }
        appRepo.deleteById(id);
    }

    @PostMapping("/{id}/probe")
    public ApiResponse<ProbeResponse> probe(@PathVariable UUID id) {
        Application app = appRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + id));
        if (app.getActuatorUrl() == null || app.getActuatorUrl().isBlank()) {
            return ApiResponse.ok(new ProbeResponse(id, false, "No actuator URL configured"));
        }
        try {
            String info = probeService.probe(app.getActuatorUrl());
            app.setActuatorInfo(info);
            appRepo.save(app);
            return ApiResponse.ok(new ProbeResponse(id, true, info));
        } catch (Exception e) {
            log.warn("Actuator probe failed for {}: {}", app.getName(), e.getMessage());
            return ApiResponse.ok(new ProbeResponse(id, false,
                    "Probe failed: " + e.getMessage()));
        }
    }
}
