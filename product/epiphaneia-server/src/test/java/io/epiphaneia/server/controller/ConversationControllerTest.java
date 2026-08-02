package io.epiphaneia.server.controller;

import io.epiphaneia.agent.api.ReportSynthesizer;
import io.epiphaneia.domain.entity.Application;
import io.epiphaneia.domain.entity.Conversation;
import io.epiphaneia.domain.repository.ApplicationRepository;
import io.epiphaneia.domain.repository.ConversationRepository;
import io.epiphaneia.server.dto.ConversationResponse;
import io.epiphaneia.server.dto.CreateConversationRequest;
import io.epiphaneia.server.dto.ReportResponse;
import io.epiphaneia.server.mapper.ConversationMapper;
import io.epiphaneia.server.skill.DiagnosisSkill;
import io.epiphaneia.server.sse.SseEmitterManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ConversationControllerTest {

    private ConversationRepository convRepo;
    private ApplicationRepository appRepo;
    private ConversationMapper mapper;
    private DiagnosisSkill diagnosisSkill;
    private ReportSynthesizer reportSynthesizer;
    private SseEmitterManager sseManager;
    private ConversationController controller;

    private UUID convId;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        convRepo = mock(ConversationRepository.class);
        appRepo = mock(ApplicationRepository.class);
        mapper = mock(ConversationMapper.class);
        diagnosisSkill = mock(DiagnosisSkill.class);
        reportSynthesizer = mock(ReportSynthesizer.class);
        sseManager = mock(SseEmitterManager.class);

        controller = new ConversationController(convRepo, appRepo, mapper,
                diagnosisSkill, reportSynthesizer, sseManager);

        convId = UUID.randomUUID();
        conversation = new Conversation();
        conversation.setTitle("test");
    }

    @Test
    @DisplayName("list without filters returns all conversations")
    void listAll() {
        when(convRepo.findAll()).thenReturn(List.of(conversation));
        when(mapper.toConversationResponse(any())).thenReturn(
                new ConversationResponse(convId, null, null, "test", null, null, null));

        var result = controller.list(null, null);

        assertEquals(1, result.data().data().size());
        verify(convRepo).findAll();
    }

    @Test
    @DisplayName("list by application filters with appId")
    void listByApplication() {
        when(convRepo.findByApplicationIdOrderByUpdatedAtDesc(any())).thenReturn(List.of());

        controller.list(UUID.randomUUID(), null);

        verify(convRepo).findByApplicationIdOrderByUpdatedAtDesc(any());
        verify(convRepo, never()).findAll();
    }

    @Test
    @DisplayName("list by keyword uses search")
    void listByKeyword() {
        when(convRepo.searchByTitle("slow")).thenReturn(List.of());

        controller.list(null, "slow");

        verify(convRepo).searchByTitle("slow");
    }

    @Test
    @DisplayName("create requires existing application")
    void createWithMissingApplicationThrows() {
        when(appRepo.findById(any())).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> controller.create(new CreateConversationRequest(UUID.randomUUID(), "t", "q")));
        verify(convRepo, never()).save(any());
    }

    @Test
    @DisplayName("create saves conversation for existing application")
    void createSaves() {
        Application app = new Application();
        when(appRepo.findById(any())).thenReturn(Optional.of(app));
        when(convRepo.save(any())).thenReturn(conversation);

        controller.create(new CreateConversationRequest(UUID.randomUUID(), "t", "q"));

        verify(convRepo).save(argThat(c -> "t".equals(c.getTitle())));
    }

    @Test
    @DisplayName("get returns detail for existing conversation")
    void getExisting() {
        when(convRepo.findById(convId)).thenReturn(Optional.of(conversation));
        when(mapper.toConversationDetailResponse(conversation))
                .thenReturn(new io.epiphaneia.server.dto.ConversationDetailResponse(convId, null, null, "t", null, null, List.of()));

        var result = controller.get(convId);

        assertNotNull(result.data());
        verify(convRepo).findById(convId);
    }

    @Test
    @DisplayName("get throws for missing conversation")
    void getMissingThrows() {
        when(convRepo.findById(convId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> controller.get(convId));
    }

    @Test
    @DisplayName("delete closes SSE connections before removing the conversation")
    void deleteClosesSseFirst() {
        when(convRepo.existsById(convId)).thenReturn(true);

        controller.delete(convId);

        InOrder order = inOrder(sseManager, convRepo);
        order.verify(sseManager).close(convId);
        order.verify(convRepo).deleteById(convId);
    }

    @Test
    @DisplayName("delete throws for missing conversation, no SSE close")
    void deleteMissingThrows() {
        when(convRepo.existsById(convId)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> controller.delete(convId));
        verify(sseManager, never()).close(any());
        verify(convRepo, never()).deleteById(any());
    }

    @Test
    @DisplayName("sendMessage rejects blank question")
    void sendMessageBlankQuestion() {
        assertThrows(IllegalArgumentException.class,
                () -> controller.sendMessage(convId, "  "));
    }

    @Test
    @DisplayName("sendMessage rejects oversized question")
    void sendMessageOversizedQuestion() {
        String tooLong = "a".repeat(2001);
        assertThrows(IllegalArgumentException.class,
                () -> controller.sendMessage(convId, tooLong));
        verify(diagnosisSkill, never()).diagnose(any(), any(), any());
    }

    @Test
    @DisplayName("sendMessage accepts valid question and starts diagnosis asynchronously")
    void sendMessageValid() {
        when(sseManager.createEmitter(convId)).thenReturn(
                new org.springframework.web.servlet.mvc.method.annotation.SseEmitter());

        var emitter = controller.sendMessage(convId, "Why is it slow?");

        assertNotNull(emitter);
        verify(sseManager).createEmitter(convId);
        // diagnosis runs on a virtual thread — generous window for CI load
        verify(diagnosisSkill, timeout(5000).atLeastOnce()).diagnose(eq(convId), eq("Why is it slow?"), any());
    }

    @Test
    @DisplayName("getReport synthesizes report for existing conversation")
    void getReportExisting() {
        when(convRepo.findById(convId)).thenReturn(Optional.of(conversation));
        when(reportSynthesizer.synthesize(conversation)).thenReturn("# Report");

        ReportResponse result = controller.getReport(convId).data();

        assertEquals("# Report", result.content());
    }

    @Test
    @DisplayName("diagnosis failure propagates through SSE error path")
    void sendMessageDiagnosisFailureSendsError() throws Exception {
        when(sseManager.createEmitter(convId)).thenReturn(
                new org.springframework.web.servlet.mvc.method.annotation.SseEmitter());
        doThrow(new IllegalStateException("A diagnosis is already in progress for this conversation"))
                .when(diagnosisSkill).diagnose(eq(convId), eq("q"), any());

        controller.sendMessage(convId, "q");

        verify(diagnosisSkill, timeout(5000).atLeastOnce()).diagnose(eq(convId), eq("q"), any());
        verify(sseManager, timeout(5000).atLeastOnce()).error(eq(convId), any(), contains("already in progress"));
        verify(sseManager, timeout(5000).atLeastOnce()).close(convId);
    }
}
