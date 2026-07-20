package io.epiphaneia.agent.internal.orchestration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static io.epiphaneia.agent.internal.orchestration.DiagnosisStateMachine.State;
import static io.epiphaneia.agent.internal.orchestration.DiagnosisStateMachine.State.*;
import static org.junit.jupiter.api.Assertions.*;

class DiagnosisStateMachineTest {

    @Test
    @DisplayName("CREATED only transitions to PLANNING")
    void createdToPlanning() {
        assertTrue(DiagnosisStateMachine.isValidTransition(CREATED, PLANNING));
        assertFalse(DiagnosisStateMachine.isValidTransition(CREATED, QUERYING));
        assertFalse(DiagnosisStateMachine.isValidTransition(CREATED, COMPLETED));
    }

    @Test
    @DisplayName("PLANNING → QUERYING, FAILED, COMPLETED_PARTIAL, ABORTED")
    void planningTransitions() {
        assertTrue(DiagnosisStateMachine.isValidTransition(PLANNING, QUERYING));
        assertTrue(DiagnosisStateMachine.isValidTransition(PLANNING, FAILED));
        assertTrue(DiagnosisStateMachine.isValidTransition(PLANNING, COMPLETED_PARTIAL));
        assertTrue(DiagnosisStateMachine.isValidTransition(PLANNING, ABORTED));
        assertFalse(DiagnosisStateMachine.isValidTransition(PLANNING, COMPLETED));
        assertFalse(DiagnosisStateMachine.isValidTransition(PLANNING, CREATED));
    }

    @Test
    @DisplayName("QUERYING self-loop + ANALYZING, COMPLETED_PARTIAL, ABORTED")
    void queryingTransitions() {
        assertTrue(DiagnosisStateMachine.isValidTransition(QUERYING, QUERYING));
        assertTrue(DiagnosisStateMachine.isValidTransition(QUERYING, ANALYZING));
        assertTrue(DiagnosisStateMachine.isValidTransition(QUERYING, COMPLETED_PARTIAL));
        assertTrue(DiagnosisStateMachine.isValidTransition(QUERYING, ABORTED));
        assertFalse(DiagnosisStateMachine.isValidTransition(QUERYING, COMPLETED));
    }

    @Test
    @DisplayName("ANALYZING → COMPLETED, COMPLETED_PARTIAL, FAILED, ABORTED")
    void analyzingTransitions() {
        assertTrue(DiagnosisStateMachine.isValidTransition(ANALYZING, COMPLETED));
        assertTrue(DiagnosisStateMachine.isValidTransition(ANALYZING, COMPLETED_PARTIAL));
        assertTrue(DiagnosisStateMachine.isValidTransition(ANALYZING, FAILED));
        assertTrue(DiagnosisStateMachine.isValidTransition(ANALYZING, ABORTED));
        assertFalse(DiagnosisStateMachine.isValidTransition(ANALYZING, QUERYING));
    }

    @Test
    @DisplayName("terminal states have no outgoing transitions")
    void terminalNoTransitions() {
        for (var state : DiagnosisStateMachine.getTerminalStates()) {
            assertTrue(DiagnosisStateMachine.isTerminal(state));
            for (var other : State.values()) {
                assertFalse(DiagnosisStateMachine.isValidTransition(state, other),
                        state + " should not transition to " + other);
            }
        }
    }

    @Test
    @DisplayName("isActive returns true for CREATED/PLANNING/QUERYING/ANALYZING")
    void activeStates() {
        assertTrue(DiagnosisStateMachine.isActive(CREATED));
        assertTrue(DiagnosisStateMachine.isActive(PLANNING));
        assertTrue(DiagnosisStateMachine.isActive(QUERYING));
        assertTrue(DiagnosisStateMachine.isActive(ANALYZING));
        assertFalse(DiagnosisStateMachine.isActive(COMPLETED));
        assertFalse(DiagnosisStateMachine.isActive(ABORTED));
    }

    @Test
    @DisplayName("initial state is CREATED")
    void initialState() {
        assertEquals(CREATED, DiagnosisStateMachine.initialState());
    }

    @Test
    @DisplayName("isTimedOut: active diagnosis older than 150s returns true")
    void timedOutActive() {
        Instant now = Instant.now();
        Instant old = now.minusSeconds(151);
        assertTrue(DiagnosisStateMachine.isTimedOut(PLANNING, old, now));
        assertTrue(DiagnosisStateMachine.isTimedOut(CREATED, old, now));
    }

    @Test
    @DisplayName("isTimedOut: recent diagnosis returns false")
    void notTimedOut() {
        Instant now = Instant.now();
        Instant recent = now.minusSeconds(10);
        assertFalse(DiagnosisStateMachine.isTimedOut(PLANNING, recent, now));
    }

    @Test
    @DisplayName("isTimedOut: terminal state always returns false")
    void timedOutTerminal() {
        Instant now = Instant.now();
        Instant old = now.minusSeconds(200);
        assertFalse(DiagnosisStateMachine.isTimedOut(COMPLETED, old, now));
        assertFalse(DiagnosisStateMachine.isTimedOut(ABORTED, old, now));
    }

    @Test
    @DisplayName("isTimedOut: exactly at boundary returns false")
    void timedOutBoundary() {
        Instant now = Instant.now();
        Instant at = now.minusSeconds(150);
        assertFalse(DiagnosisStateMachine.isTimedOut(PLANNING, at, now));
    }
}
