package io.epiphaneia.server.controller;

import io.epiphaneia.domain.entity.Conversation;
import io.epiphaneia.agent.api.ReportSynthesizer;
import io.epiphaneia.domain.entity.Application;
import io.epiphaneia.domain.repository.ApplicationRepository;
import io.epiphaneia.domain.repository.ConversationRepository;
import io.epiphaneia.server.dto.*;
import io.epiphaneia.server.mapper.ConversationMapper;
import io.epiphaneia.server.skill.DiagnosisSkill;
import io.epiphaneia.server.sse.SseEmitterManager;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {
    // ponytail: single-admin model — all CRUD operations assume single admin context.
    // Ownership/authz checks deferred to multi-user phase.

    private static final int MAX_QUESTION_LENGTH = 2000;

    private final ConversationRepository convRepo;
    private final ApplicationRepository appRepo;
    private final ConversationMapper mapper;
    private final DiagnosisSkill diagnosisSkill;
    private final ReportSynthesizer reportSynthesizer;
    private final SseEmitterManager sseManager;

    public ConversationController(ConversationRepository convRepo, ApplicationRepository appRepo,
                                   ConversationMapper mapper, DiagnosisSkill diagnosisSkill,
                                   ReportSynthesizer reportSynthesizer, SseEmitterManager sseManager) {
        this.convRepo = convRepo;
        this.appRepo = appRepo;
        this.mapper = mapper;
        this.diagnosisSkill = diagnosisSkill;
        this.reportSynthesizer = reportSynthesizer;
        this.sseManager = sseManager;
    }

    @Transactional(readOnly = true)
    @GetMapping
    public ApiResponse<ApiListResponse<ConversationResponse>> list(
            @RequestParam(name = "appId", required = false) UUID applicationId,
            @RequestParam(required = false) String keyword) {
        List<Conversation> conversations;
        if (applicationId != null) {
            conversations = convRepo.findByApplicationIdOrderByUpdatedAtDesc(applicationId);
        } else if (keyword != null && !keyword.isBlank()) {
            conversations = convRepo.searchByTitle(keyword);
        } else {
            conversations = convRepo.findAll();
        }

        List<ConversationResponse> result = conversations.stream()
                .map(mapper::toConversationResponse).toList();
        return ApiResponse.ok(ApiListResponse.of(result));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ConversationResponse> create(@Valid @RequestBody CreateConversationRequest req) {
        Application app = appRepo.findById(req.applicationId())
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));

        Conversation conv = new Conversation();
        conv.setApplication(app);
        conv.setTitle(req.title());
        convRepo.save(conv);

        return ApiResponse.ok(mapper.toConversationResponse(conv));
    }

    @Transactional(readOnly = true)
    @GetMapping("/{id}")
    public ApiResponse<ConversationDetailResponse> get(@PathVariable UUID id) {
        return convRepo.findById(id)
                .map(mapper::toConversationDetailResponse)
                .map(ApiResponse::ok)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        if (!convRepo.existsById(id)) {
            throw new IllegalArgumentException("Conversation not found: " + id);
        }
        // SSE lifecycle stays outside the DB transaction: close connections before the
        // delete so no events fire into a removed conversation, but a failed cascade
        // delete (rollback) must not leave the SSE connection already dead
        sseManager.close(id);
        convRepo.deleteById(id); // JpaRepository.deleteById has its own transaction
    }

    @PostMapping(value = "/{id}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessage(@PathVariable UUID id, @RequestParam String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
        if (question.length() > MAX_QUESTION_LENGTH) {
            throw new IllegalArgumentException(
                    "question too long (max " + MAX_QUESTION_LENGTH + " chars)");
        }
        // Create SSE emitter first (client gets immediate connection)
        SseEmitter emitter = sseManager.createEmitter(id);

        // Run diagnosis asynchronously so SSE events stream in real-time.
        // Java 21 virtual threads provide lightweight concurrency without pool sizing.
        Thread.startVirtualThread(() -> {
            try {
                diagnosisSkill.diagnose(id, question, sseManager);
            } catch (IllegalStateException e) {
                if (e.getMessage().contains("already in progress")) {
                    sseManager.error(id, UUID.randomUUID(), "Diagnosis already in progress");
                } else {
                    sseManager.error(id, UUID.randomUUID(), "Diagnosis failed. Check server logs.");
                }
                sseManager.close(id);
            } catch (Exception e) {
                sseManager.error(id, UUID.randomUUID(), "Diagnosis failed. Check server logs.");
                sseManager.close(id);
            }
        });

        return emitter;
    }

    @Transactional(readOnly = true)
    @GetMapping("/{id}/report")
    public ApiResponse<ReportResponse> getReport(@PathVariable UUID id) {
        Conversation conv = convRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + id));
        String content = reportSynthesizer.synthesize(conv);
        return ApiResponse.ok(new ReportResponse(content));
    }
}
