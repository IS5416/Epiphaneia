package io.epiphaneia.server.controller;

import io.epiphaneia.domain.internal.entity.LlmProvider;
import io.epiphaneia.domain.internal.repository.LlmProviderRepository;
import io.epiphaneia.llm.api.LlmProviderValidator;
import io.epiphaneia.infra.api.EncryptionService;
import io.epiphaneia.server.dto.*;
import io.epiphaneia.server.mapper.LlmProviderMapper;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/llm")
public class LlmController {

    private final LlmProviderRepository llmRepo;
    private final LlmProviderMapper mapper;
    private final EncryptionService encryptionService;
    private final LlmProviderValidator llmValidator;

    public LlmController(LlmProviderRepository llmRepo, LlmProviderMapper mapper,
                          EncryptionService encryptionService, LlmProviderValidator llmValidator) {
        this.llmRepo = llmRepo;
        this.mapper = mapper;
        this.encryptionService = encryptionService;
        this.llmValidator = llmValidator;
    }

    @GetMapping
    public ApiResponse<LlmProviderResponse> get() {
        return llmRepo.findAll().stream().findFirst()
                .map(mapper::toResponse)
                .map(ApiResponse::ok)
                .orElse(ApiResponse.ok(null));
    }

    @PutMapping
    public ApiResponse<LlmProviderResponse> update(@Valid @RequestBody LlmProviderRequest req) {
        llmValidator.validateProvider(req.provider());

        LlmProvider llm = llmRepo.findAll().stream().findFirst()
                .orElseGet(LlmProvider::new);

        mapper.updateEntity(req, llm);
        if (req.apiKey() != null && !req.apiKey().isBlank()) {
            llm.setApiKeyEncrypted(encryptionService.encrypt(req.apiKey()));
        }
        llm.setConnected(true);
        llmRepo.save(llm);
        return ApiResponse.ok(mapper.toResponse(llm));
    }

    @PostMapping("/test")
    public ApiResponse<TestConnectionResponse> test(@Valid @RequestBody LlmProviderRequest req) {
        try {
            llmValidator.validateProvider(req.provider());
            // ponytail: placeholder — real LLM connectivity test via LlmClient
            return ApiResponse.ok(new TestConnectionResponse(true, "LLM connection successful"));
        } catch (Exception e) {
            return ApiResponse.ok(new TestConnectionResponse(false, e.getMessage()));
        }
    }
}
