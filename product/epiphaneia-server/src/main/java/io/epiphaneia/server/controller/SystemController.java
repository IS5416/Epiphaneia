package io.epiphaneia.server.controller;

import io.epiphaneia.agent.api.repository.AdminRepository;
import io.epiphaneia.agent.api.repository.ApplicationRepository;
import io.epiphaneia.agent.api.repository.DataSourceRepository;
import io.epiphaneia.agent.api.repository.LlmProviderRepository;
import io.epiphaneia.server.dto.ApiResponse;
import io.epiphaneia.server.dto.SystemStatusResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    private final AdminRepository adminRepo;
    private final LlmProviderRepository llmRepo;
    private final DataSourceRepository dsRepo;
    private final ApplicationRepository appRepo;

    public SystemController(AdminRepository adminRepo, LlmProviderRepository llmRepo,
                            DataSourceRepository dsRepo, ApplicationRepository appRepo) {
        this.adminRepo = adminRepo;
        this.llmRepo = llmRepo;
        this.dsRepo = dsRepo;
        this.appRepo = appRepo;
    }

    @GetMapping("/status")
    public ApiResponse<SystemStatusResponse> status() {
        boolean adminExists = adminRepo.existsByUsername("admin");
        long llmCount = llmRepo.count();
        long dsCount = dsRepo.count();
        long appCount = appRepo.count();

        return ApiResponse.ok(new SystemStatusResponse(
                adminExists && llmCount > 0 && dsCount > 0,
                adminExists,
                llmCount > 0,
                dsCount,
                appCount));
    }
}
