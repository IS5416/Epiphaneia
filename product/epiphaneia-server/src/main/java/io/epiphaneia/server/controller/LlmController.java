package io.epiphaneia.server.controller;

import io.epiphaneia.domain.internal.entity.LlmProvider;
import io.epiphaneia.domain.internal.repository.LlmProviderRepository;
import io.epiphaneia.llm.api.LlmProviderValidator;
import io.epiphaneia.llm.internal.client.LlmClient;
import io.epiphaneia.infra.api.EncryptionService;
import io.epiphaneia.server.dto.*;
import io.epiphaneia.server.mapper.LlmProviderMapper;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/llm")
public class LlmController {

    private static final Logger log = LoggerFactory.getLogger(LlmController.class);

    private final LlmProviderRepository llmRepo;
    private final LlmProviderMapper mapper;
    private final EncryptionService encryptionService;
    private final LlmProviderValidator llmValidator;
    private final LlmClient llmClient;

    public LlmController(LlmProviderRepository llmRepo, LlmProviderMapper mapper,
                          EncryptionService encryptionService, LlmProviderValidator llmValidator,
                          LlmClient llmClient) {
        this.llmRepo = llmRepo;
        this.mapper = mapper;
        this.encryptionService = encryptionService;
        this.llmValidator = llmValidator;
        this.llmClient = llmClient;
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
            LlmProvider temp = mapper.toEntity(req);
            if (req.apiKey() != null && !req.apiKey().isBlank()) {
                temp.setApiKeyEncrypted(encryptionService.encrypt(req.apiKey()));
            }
            boolean ok = llmClient.testConnection(temp);
            String msg = ok ? "LLM connection successful"
                    : "LLM connection failed — check API key and network";
            return ApiResponse.ok(new TestConnectionResponse(ok, msg));
        } catch (Exception e) {
            log.warn("LLM connection test failed: {}", e.getMessage());
            return ApiResponse.ok(new TestConnectionResponse(false, e.getMessage()));
        }
    }
}
