package io.epiphaneia.server.controller;

import io.epiphaneia.domain.internal.entity.Application;
import io.epiphaneia.domain.internal.repository.ApplicationRepository;
import io.epiphaneia.server.dto.*;
import io.epiphaneia.server.mapper.ApplicationMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationController {

    private final ApplicationRepository appRepo;
    private final ApplicationMapper mapper;

    public ApplicationController(ApplicationRepository appRepo, ApplicationMapper mapper) {
        this.appRepo = appRepo;
        this.mapper = mapper;
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
        // ponytail: Phase 3 actuator probe is synchronous; move to @Async in Phase 5
        Application app = appRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + id));
        // ponytail: placeholder — real probe in engine module
        return ApiResponse.ok(new ProbeResponse(id, true, "Actuator endpoint reachable"));
    }
}
