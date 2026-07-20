package io.epiphaneia.agent.internal.orchestration;

import java.time.Instant;
import java.util.*;

/**
 * Manages the 8-state diagnosis lifecycle and validates transitions.
 *
 * <pre>
 * CREATED → PLANNING → QUERYING ⇄ QUERYING → ANALYZING → COMPLETED
 *                ↘ FAILED    ↘ COMPLETED_PARTIAL   ↘ COMPLETED_PARTIAL
 *                ↘ ABORTED   ↘ ABORTED             ↘ FAILED
 *                ↘ COMPLETED_PARTIAL               ↘ ABORTED
 *
 * Terminal states: COMPLETED, COMPLETED_PARTIAL, FAILED, ABORTED
 * Active states: CREATED, PLANNING, QUERYING, ANALYZING
 * </pre>
 *
 * Timeout threshold: 120s diagnosis timeout + 30s buffer = 150s.
 * Any non-terminal diagnosis older than this is eligible for ABORTED.
 */
public class DiagnosisStateMachine {

    public static final long TIMEOUT_SECONDS = 150;

    public enum State {
        CREATED, PLANNING, QUERYING, ANALYZING,
        COMPLETED, COMPLETED_PARTIAL, FAILED, ABORTED
    }

    private static final Set<State> TERMINAL = EnumSet.of(
            State.COMPLETED, State.COMPLETED_PARTIAL, State.FAILED, State.ABORTED);

    private static final Set<State> ACTIVE = EnumSet.of(
            State.CREATED, State.PLANNING, State.QUERYING, State.ANALYZING);

    private static final Map<State, Set<State>> TRANSITIONS = Map.copyOf(Map.of(
            State.CREATED,            EnumSet.of(State.PLANNING),
            State.PLANNING,           EnumSet.of(State.QUERYING, State.FAILED, State.COMPLETED_PARTIAL, State.ABORTED),
            State.QUERYING,           EnumSet.of(State.QUERYING, State.ANALYZING, State.COMPLETED_PARTIAL, State.ABORTED),
            State.ANALYZING,          EnumSet.of(State.COMPLETED, State.COMPLETED_PARTIAL, State.FAILED, State.ABORTED)
    ));

    public static boolean isValidTransition(State from, State to) {
        if (isTerminal(from)) return false;
        Set<State> allowed = TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    public static Set<State> getTerminalStates() {
        return TERMINAL;
    }

    public static boolean isTerminal(State state) {
        return TERMINAL.contains(state);
    }

    public static boolean isActive(State state) {
        return ACTIVE.contains(state);
    }

    public static Set<State> getActiveStates() {
        return ACTIVE;
    }

    public static State initialState() {
        return State.CREATED;
    }

    /**
     * Determine if the diagnosis should be auto-aborted based on timeout.
     *
     * @param state   current diagnosis state
     * @param createdAt when the diagnosis message was created
     * @param now     current time
     * @return true if the diagnosis has exceeded the timeout buffer and should be marked ABORTED
     */
    public static boolean isTimedOut(State state, Instant createdAt, Instant now) {
        if (isTerminal(state)) return false;
        return createdAt.plusSeconds(TIMEOUT_SECONDS).isBefore(now);
    }

    /**
     * Get a compact description of the timeout boundary for error messages.
     */
    public static String timeoutDescription() {
        return TIMEOUT_SECONDS + "s (120s diagnosis + 30s buffer)";
    }
}
