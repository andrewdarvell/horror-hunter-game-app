package ru.darvell.game.hhg.domain.combat.timeline;

import org.junit.jupiter.api.Test;
import ru.darvell.game.hhg.domain.combat.timeline.ContactOutcome.Consequences;
import ru.darvell.game.hhg.domain.combat.timeline.TimelineEvent.Type;
import ru.darvell.game.hhg.domain.combat.timeline.TimelineState.ActiveAction;
import ru.darvell.game.hhg.domain.combat.timeline.TimelineState.Participant;
import ru.darvell.game.hhg.domain.combat.timeline.TimelineState.Role;
import ru.darvell.game.hhg.domain.tactical.CellCoordinates;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ru.darvell.game.hhg.domain.combat.timeline.TimelineAction.contact;
import static ru.darvell.game.hhg.domain.combat.timeline.TimelineAction.move;
import static ru.darvell.game.hhg.domain.combat.timeline.TimelineAction.waitFor;
import static ru.darvell.game.hhg.domain.combat.timeline.TimelineEvent.Type.ACTION_COMPLETED;
import static ru.darvell.game.hhg.domain.combat.timeline.TimelineEvent.Type.ACTION_INTERRUPTED;
import static ru.darvell.game.hhg.domain.combat.timeline.TimelineEvent.Type.ACTION_STARTED;
import static ru.darvell.game.hhg.domain.combat.timeline.TimelineEvent.Type.CONTACT;
import static ru.darvell.game.hhg.domain.combat.timeline.TimelineEvent.Type.DEFEATED;
import static ru.darvell.game.hhg.domain.combat.timeline.TimelineEvent.Type.HEALTH_CHANGED;
import static ru.darvell.game.hhg.domain.combat.timeline.TimelineEvent.Type.MOVE_BLOCKED;
import static ru.darvell.game.hhg.domain.combat.timeline.TimelineEvent.Type.POSITION_CHANGED;
import static ru.darvell.game.hhg.domain.combat.timeline.TimelineEvent.Type.RECOVERY_ENDED;
import static ru.darvell.game.hhg.domain.combat.timeline.TimelineEvent.Type.RECOVERY_EXTENDED;
import static ru.darvell.game.hhg.domain.combat.timeline.TimelineEvent.Type.RECOVERY_STARTED;
import static ru.darvell.game.hhg.domain.combat.timeline.TimelineEvent.Type.RESTRICTION_APPLIED;
import static ru.darvell.game.hhg.domain.combat.timeline.TimelineEvent.Type.RESTRICTION_EXPIRED;
import static ru.darvell.game.hhg.domain.combat.timeline.TimelineResult.Stop.DECISION;
import static ru.darvell.game.hhg.domain.combat.timeline.TimelineResult.Stop.NO_PROGRESS;
import static ru.darvell.game.hhg.domain.combat.timeline.TimelineResult.Stop.TERMINAL;

class CombatTimelineTest {
    private static final CellCoordinates HUNTER_CELL = new CellCoordinates(0, 0);
    private static final CellCoordinates OPPONENT_CELL = new CellCoordinates(1, 0);
    private static final CellCoordinates FREE_CELL = new CellCoordinates(2, 0);
    private static final Consequences NONE = new Consequences(0, false, 0, false);

    @Test
    void pausesAndResumesTheSameOpponentAction() {
        TimelineAction first = waitFor(2);
        TimelineAction second = waitFor(4);
        ActiveAction opponentAction = new ActiveAction(waitFor(5), 0);
        TimelineState initial = state(0, hunter(null), opponent(opponentAction), first, second);

        TimelineResult paused = CombatTimeline.resolve(initial, first);
        assertEquals(DECISION, paused.getStop());
        assertEquals(2, paused.getState().getNow());
        assertEquals(opponentAction, paused.getState().getOpponent().getAction());
        assertEquals(3, opponentAction.getFinish() - paused.getState().getNow());
        assertSame(paused.getState(), CombatTimeline.advance(paused.getState()).getState());

        TimelineResult resumed = CombatTimeline.resolve(paused.getState(), second);
        assertEquals(DECISION, resumed.getStop());
        assertEquals(6, resumed.getState().getNow());
        assertNull(resumed.getState().getOpponent().getAction());
        assertEquals(Arrays.asList(
            actionEvent(ACTION_STARTED, 2, 1, new ActiveAction(second, 2)),
            actionEvent(ACTION_COMPLETED, 5, 2, opponentAction),
            actionEvent(ACTION_COMPLETED, 6, 1, new ActiveAction(second, 2))), resumed.getEvents());
    }

    @Test
    void jumpsToHalfTickAndDistantDeadlines() {
        TimelineAction longWait = waitFor(1_000_000_000L);
        ActiveAction halfTick = new ActiveAction(waitFor(1), 0);
        TimelineResult result = CombatTimeline.resolve(state(0, hunter(null), opponent(halfTick), longWait), longWait);

        assertEquals(2, CombatTimeline.QUANTA_PER_TICK);
        assertEquals(DECISION, result.getStop());
        assertEquals(1_000_000_000L, result.getState().getNow());
        assertEquals(Arrays.asList(
            actionEvent(ACTION_STARTED, 0, 1, new ActiveAction(longWait, 0)),
            actionEvent(ACTION_COMPLETED, 1, 2, halfTick),
            actionEvent(ACTION_COMPLETED, 1_000_000_000L, 1, new ActiveAction(longWait, 0))), result.getEvents());
    }

    @Test
    void rejectsInvalidStartDurationAndInactiveDeadlines() {
        assertThrows(IllegalArgumentException.class, () -> state(-1, hunter(null), opponent(null)));
        assertThrows(IllegalArgumentException.class, () -> waitFor(0));
        assertThrows(IllegalArgumentException.class, () -> waitFor(-1));
        assertThrows(IllegalArgumentException.class, () -> new ActiveAction(waitFor(1), -1));
        Participant alreadyFinished = opponent(new ActiveAction(waitFor(2), 0));
        assertThrows(IllegalArgumentException.class, () -> state(2, hunter(null), alreadyFinished));
        Participant notYetStarted = opponent(new ActiveAction(waitFor(2), 4));
        assertThrows(IllegalArgumentException.class, () -> state(3, hunter(null), notYetStarted));
    }

    @Test
    void longMaxIsADeadlineAndOverflowDoesNotPublish() {
        TimelineAction fits = waitFor(1);
        TimelineAction overflows = waitFor(2);
        TimelineState initial = state(Long.MAX_VALUE - 1, hunter(null), opponent(null), fits, overflows);
        assertThrows(ArithmeticException.class, () -> CombatTimeline.resolve(initial, overflows));
        assertNull(initial.getHunter().getAction());
        assertEquals(Long.MAX_VALUE - 1, initial.getNow());

        TimelineResult limit = CombatTimeline.resolve(initial, fits);
        assertEquals(NO_PROGRESS, limit.getStop());
        assertEquals(Long.MAX_VALUE, limit.getState().getNow());
        assertEquals(actionEvent(ACTION_COMPLETED, Long.MAX_VALUE, 1,
            new ActiveAction(fits, Long.MAX_VALUE - 1)), limit.getEvents().get(1));
    }

    @Test
    void lowerIdGetsContestedCellRegardlessOfParticipantOrderOrRole() {
        CellCoordinates destination = new CellCoordinates(2, 0);
        ActiveAction action = new ActiveAction(move(2, destination), 0);
        Participant hunter = new Participant(4, Role.HUNTER, HUNTER_CELL, 10, action);
        Participant opponent = opponent(action);
        TimelineResult result = CombatTimeline.advance(state(0, hunter, opponent, waitFor(2)));

        assertEquals(result, CombatTimeline.advance(state(0, opponent, hunter, waitFor(2))));
        assertEquals(destination, result.getState().getOpponent().getCell());
        assertEquals(HUNTER_CELL, result.getState().getHunter().getCell());
        assertEquals(Arrays.asList(
            TimelineEvent.builder().type(POSITION_CHANGED).time(2).participantId(2).cell(destination).build(),
            TimelineEvent.builder().type(MOVE_BLOCKED).time(2).participantId(4).cell(destination).build(),
            actionEvent(ACTION_COMPLETED, 2, 2, action),
            actionEvent(ACTION_COMPLETED, 2, 4, action)), result.getEvents());
    }

    @Test
    void blocksSwappingTwoOccupiedCells() {
        ActiveAction hunterMove = new ActiveAction(move(2, OPPONENT_CELL), 0);
        ActiveAction opponentMove = new ActiveAction(move(2, HUNTER_CELL), 0);
        TimelineResult result = CombatTimeline.advance(state(0, hunter(hunterMove), opponent(opponentMove), waitFor(1)));

        assertEquals(HUNTER_CELL, result.getState().getHunter().getCell());
        assertEquals(OPPONENT_CELL, result.getState().getOpponent().getCell());
        assertEquals(MOVE_BLOCKED, result.getEvents().get(0).getType());
        assertEquals(MOVE_BLOCKED, result.getEvents().get(1).getType());
    }

    @Test
    void usesCellsAfterMovementToAdmitContacts() {
        TimelineAction reachesDestination = contact(2, FREE_CELL, new ContactOutcome(NONE, damage(1)));
        TimelineAction checksOldCell = contact(2, OPPONENT_CELL, new ContactOutcome(NONE, damage(1)));
        ActiveAction opponentMove = new ActiveAction(move(2, FREE_CELL), 0);
        TimelineState initial = state(0, hunter(null), opponent(opponentMove), reachesDestination, checksOldCell);

        TimelineResult hit = CombatTimeline.resolve(initial, reachesDestination);
        assertTypes(hit, ACTION_STARTED, POSITION_CHANGED, CONTACT, HEALTH_CHANGED, ACTION_COMPLETED, ACTION_COMPLETED);
        assertEquals(9, hit.getState().getOpponent().getHealth());
        TimelineEvent collision = hit.getEvents().get(2);
        assertEquals(2, collision.getTime());
        assertEquals(1, collision.getParticipantId());
        assertEquals(2, collision.getTargetId());
        assertEquals(reachesDestination, collision.getAction().getCommand());

        TimelineResult miss = CombatTimeline.resolve(initial, checksOldCell);
        assertTypes(miss, ACTION_STARTED, POSITION_CHANGED, ACTION_COMPLETED, ACTION_COMPLETED);
        assertEquals(10, miss.getState().getOpponent().getHealth());
    }

    @Test
    void admitsBothLethalContactsBeforeApplyingAnyDamage() {
        Consequences lethal = new Consequences(Integer.MAX_VALUE, true, 0, false);
        ContactOutcome outcome = new ContactOutcome(lethal, lethal);
        Participant hunter = hunter(new ActiveAction(contact(2, OPPONENT_CELL, outcome), 0));
        Participant opponent = opponent(new ActiveAction(contact(2, HUNTER_CELL, outcome), 0));
        TimelineResult result = CombatTimeline.advance(state(0, hunter, opponent, waitFor(1)));

        assertEquals(result, CombatTimeline.advance(state(0, opponent, hunter, waitFor(1))));
        assertEquals(TERMINAL, result.getStop());
        assertEquals(0, result.getState().getHunter().getHealth());
        assertEquals(0, result.getState().getOpponent().getHealth());
        assertTypes(result, CONTACT, CONTACT, HEALTH_CHANGED, HEALTH_CHANGED, DEFEATED, DEFEATED,
            ACTION_COMPLETED, ACTION_COMPLETED);
        assertEquals(Arrays.asList(1, 2, 1, 2, 1, 2, 1, 2), result.getEvents().stream()
            .map(TimelineEvent::getParticipantId).collect(Collectors.toList()));
    }

    @Test
    void onlyExplicitInterruptionCancelsLivingTargetsFutureAction() {
        TimelineAction weak = contact(2, OPPONENT_CELL, new ContactOutcome(NONE, damage(1)));
        TimelineAction interrupting = contact(2, OPPONENT_CELL,
            new ContactOutcome(NONE, new Consequences(1, true, 0, false)));
        TimelineAction later = waitFor(4);
        ActiveAction plannedContact = new ActiveAction(contact(5, HUNTER_CELL, new ContactOutcome(NONE, damage(1))), 0);
        TimelineState initial = state(0, hunter(null), opponent(plannedContact), weak, interrupting, later);

        TimelineResult preserved = CombatTimeline.resolve(initial, weak);
        assertEquals(plannedContact, preserved.getState().getOpponent().getAction());
        TimelineResult afterWeak = CombatTimeline.resolve(preserved.getState(), later);
        assertTypes(afterWeak, ACTION_STARTED, CONTACT, HEALTH_CHANGED, ACTION_COMPLETED, ACTION_COMPLETED);
        assertEquals(9, afterWeak.getState().getHunter().getHealth());

        TimelineResult interrupted = CombatTimeline.resolve(initial, interrupting);
        assertEquals(actionEvent(ACTION_INTERRUPTED, 2, 2, plannedContact), interrupted.getEvents().get(4));
        assertNull(interrupted.getState().getOpponent().getAction());
        assertTypes(CombatTimeline.resolve(interrupted.getState(), later), ACTION_STARTED, ACTION_COMPLETED);
    }

    @Test
    void defeatCancelsFutureActionAndRestriction() {
        TimelineAction lethal = contact(1, OPPONENT_CELL, new ContactOutcome(NONE, damage(10)));
        ActiveAction future = new ActiveAction(waitFor(4), 0);
        Participant opponent = new Participant(2, Role.OPPONENT, OPPONENT_CELL, 10, future, null, 2L);
        TimelineResult result = CombatTimeline.resolve(state(0, hunter(null), opponent, lethal), lethal);

        assertEquals(TERMINAL, result.getStop());
        assertNull(result.getState().getOpponent().getAction());
        assertNull(result.getState().getOpponent().getRestrictionUntil());
        assertTypes(result, ACTION_STARTED, CONTACT, HEALTH_CHANGED, DEFEATED, ACTION_COMPLETED, ACTION_INTERRUPTED);
    }

    @Test
    void pausesDuringOpponentsRemainingRecovery() {
        TimelineAction impact = contact(2, OPPONENT_CELL,
            new ContactOutcome(new Consequences(0, false, 2, false), new Consequences(1, true, 4, false)));
        TimelineAction later = waitFor(3);
        TimelineState initial = state(0, hunter(null), opponent(new ActiveAction(waitFor(5), 0)), impact, later);
        TimelineResult first = CombatTimeline.resolve(initial, impact);

        assertEquals(DECISION, first.getStop());
        assertEquals(4, first.getState().getNow());
        assertNull(first.getState().getHunter().getRecoveryUntil());
        assertNull(first.getState().getOpponent().getAction());
        assertEquals(6L, first.getState().getOpponent().getRecoveryUntil());
        assertTypes(first, ACTION_STARTED, CONTACT, HEALTH_CHANGED, ACTION_COMPLETED, ACTION_INTERRUPTED,
            RECOVERY_STARTED, RECOVERY_STARTED, RECOVERY_ENDED);

        TimelineResult next = CombatTimeline.resolve(first.getState(), later);
        assertTypes(next, ACTION_STARTED, RECOVERY_ENDED, ACTION_COMPLETED);
        assertEquals(6, next.getEvents().get(1).getTime());
        assertEquals(7, next.getState().getNow());
    }

    @Test
    void recoveryKeepsLaterDeadlineIncludingRefreshAtOldDeadline() {
        assertRecoveryRefresh(5, 0, 8, false);
        assertRecoveryRefresh(5, 2, 8, false);
        assertRecoveryRefresh(5, 5, 10, true);
        assertRecoveryRefresh(8, 2, 10, true);
    }

    @Test
    void rejectsRecoveryThatOverlapsAnUninterruptedAction() {
        TimelineAction impact = contact(2, OPPONENT_CELL, new ContactOutcome(NONE, new Consequences(1, false, 2, false)));
        TimelineAction later = waitFor(6);
        ActiveAction continuing = new ActiveAction(waitFor(5), 0);
        TimelineState initial = state(0, hunter(null), opponent(continuing), impact, later);

        assertThrows(IllegalArgumentException.class, () -> CombatTimeline.resolve(initial, impact));
        assertEquals(0, initial.getNow());
        assertEquals(10, initial.getOpponent().getHealth());
        assertEquals(continuing, initial.getOpponent().getAction());
        assertTypes(CombatTimeline.resolve(initial, later), ACTION_STARTED, ACTION_COMPLETED, ACTION_COMPLETED);
    }

    @Test
    void restrictionCanExpireBeforeAtOrAfterRecovery() {
        assertRestrictionAtDecision(3, 9, true);
        assertRestrictionAtDecision(2, 8, true);
        assertRestrictionAtDecision(1, 7, false);
    }

    @Test
    void refreshesRestrictionWithoutStackingOrReportingInterimExpiry() {
        assertRestrictionRefresh(1, 9);
        assertRestrictionRefresh(2, 10);
    }

    @Test
    void restrictionDoesNotInterruptAlreadyStartedContact() {
        TimelineAction restrict = contact(1, OPPONENT_CELL, new ContactOutcome(NONE, new Consequences(0, false, 0, true)));
        TimelineAction later = waitFor(4);
        ActiveAction prepared = new ActiveAction(contact(2, HUNTER_CELL, new ContactOutcome(NONE, damage(1))), 0);
        TimelineResult first = CombatTimeline.resolve(state(0, hunter(null), opponent(prepared), restrict, later), restrict);
        assertEquals(prepared, first.getState().getOpponent().getAction());
        assertEquals(3L, first.getState().getOpponent().getRestrictionUntil());

        TimelineResult next = CombatTimeline.resolve(first.getState(), later);
        assertTypes(next, ACTION_STARTED, CONTACT, HEALTH_CHANGED, ACTION_COMPLETED, RESTRICTION_EXPIRED, ACTION_COMPLETED);
        assertEquals(9, next.getState().getHunter().getHealth());
    }

    @Test
    void busyHunterCannotQueueCommandWhenOpponentMoves() {
        TimelineAction offered = waitFor(1);
        TimelineState busy = state(0, hunter(new ActiveAction(waitFor(6), 0)),
            opponent(new ActiveAction(move(3, FREE_CELL), 0)), offered);

        assertThrows(IllegalStateException.class, () -> CombatTimeline.resolve(busy, offered));
        TimelineResult result = CombatTimeline.advance(busy);
        assertTypes(result, POSITION_CHANGED, ACTION_COMPLETED, ACTION_COMPLETED);
        assertEquals(3, result.getEvents().get(0).getTime());
        assertEquals(6, result.getState().getNow());
        assertEquals(DECISION, result.getStop());
        assertEquals(OPPONENT_CELL, busy.getOpponent().getCell());
    }

    @Test
    void defeatingHunterOnRecoveryDeadlinePreventsDecision() {
        Participant recovering = new Participant(1, Role.HUNTER, HUNTER_CELL, 10, null, 4L, 4L);
        ActiveAction opponentHit = new ActiveAction(contact(4, HUNTER_CELL, new ContactOutcome(NONE, damage(10))), 0);
        TimelineResult result = CombatTimeline.advance(state(2, recovering, opponent(opponentHit), waitFor(2)));

        assertEquals(4, result.getState().getNow());
        assertEquals(TERMINAL, result.getStop());
        assertTrue(result.getAvailableCommands().isEmpty());
        assertNull(result.getState().getHunter().getRecoveryUntil());
        assertNull(result.getState().getHunter().getRestrictionUntil());
        assertTypes(result, CONTACT, HEALTH_CHANGED, DEFEATED, ACTION_COMPLETED);
    }

    @Test
    void advancesPastBlockedDecisionOrReportsNoProgressWithoutEvents() {
        TimelineAction restricted = contact(1, OPPONENT_CELL, new ContactOutcome(NONE, NONE));
        Participant hunter = new Participant(1, Role.HUNTER, HUNTER_CELL, 10, null, null, 4L);
        TimelineState blocked = state(2, hunter, opponent(null), restricted);
        assertThrows(IllegalStateException.class, () -> CombatTimeline.resolve(blocked, restricted));
        TimelineResult result = CombatTimeline.advance(blocked);
        assertEquals(DECISION, result.getStop());
        assertEquals(4, result.getState().getNow());
        assertTrue(result.getAvailableCommands().contains(restricted));
        assertTypes(result, RESTRICTION_EXPIRED);

        TimelineState empty = state(2, hunter(null), opponent(null));
        TimelineResult noProgress = CombatTimeline.advance(empty);
        assertEquals(NO_PROGRESS, noProgress.getStop());
        assertSame(empty, noProgress.getState());
        assertTrue(noProgress.getEvents().isEmpty());
    }

    @Test
    void validatesRestoredParticipantsAndTimers() {
        Participant hunter = hunter(null);
        Participant duplicateId = new Participant(1, Role.OPPONENT, OPPONENT_CELL, 10, null);
        Participant occupiedCell = new Participant(2, Role.OPPONENT, HUNTER_CELL, 10, null);
        Participant expiredRecovery = new Participant(2, Role.OPPONENT, OPPONENT_CELL, 10, null, 3L, null);
        Participant tooLongRestriction = new Participant(2, Role.OPPONENT, OPPONENT_CELL, 10, null, null, 6L);
        assertThrows(IllegalArgumentException.class, () -> state(3, hunter, duplicateId));
        assertThrows(IllegalArgumentException.class, () -> state(3, hunter, occupiedCell));
        assertThrows(IllegalArgumentException.class, () -> state(3, hunter, expiredRecovery));
        assertThrows(IllegalArgumentException.class, () -> state(3, hunter, tooLongRestriction));
        ActiveAction action = new ActiveAction(waitFor(3), 2);
        assertThrows(IllegalArgumentException.class,
            () -> new Participant(2, Role.OPPONENT, OPPONENT_CELL, 10, action, 6L, null));
    }

    @Test
    void previewAndApplyingSameTransitionProduceIndependentImmutableValues() {
        TimelineAction impact = contact(2, FREE_CELL, new ContactOutcome(new Consequences(0, false, 1, true), damage(2)));
        TimelineAction later = waitFor(2);
        ActiveAction plannedMove = new ActiveAction(move(1, FREE_CELL), 0);
        HashSet<TimelineAction> commands = new HashSet<>(Arrays.asList(impact, later));
        TimelineState initial = new TimelineState(0, hunter(null), opponent(plannedMove), commands);
        commands.clear();

        TimelineResult preview = CombatTimeline.resolve(initial, impact);
        assertEquals(preview, CombatTimeline.resolve(initial, impact));
        TimelineResult applied = CombatTimeline.resolve(initial, impact);
        assertEquals(preview, applied);
        assertTypes(applied, ACTION_STARTED, POSITION_CHANGED, ACTION_COMPLETED, CONTACT, HEALTH_CHANGED,
            ACTION_COMPLETED, RECOVERY_STARTED, RESTRICTION_APPLIED, RECOVERY_ENDED);
        assertEquals(0, initial.getNow());
        assertEquals(10, initial.getOpponent().getHealth());
        assertEquals(OPPONENT_CELL, initial.getOpponent().getCell());
        assertEquals(plannedMove, initial.getOpponent().getAction());
        assertEquals(2, initial.getCommands().size());
        assertThrows(UnsupportedOperationException.class, () -> preview.getEvents().clear());
        assertThrows(UnsupportedOperationException.class, () -> preview.getAvailableCommands().clear());
        assertThrows(UnsupportedOperationException.class, () -> preview.getState().getParticipants().clear());
        assertThrows(UnsupportedOperationException.class, () -> initial.getCommands().clear());

        TimelineResult laterResult = CombatTimeline.resolve(applied.getState(), later);
        assertEquals(5, laterResult.getState().getNow());
        assertEquals(3, preview.getState().getNow());
        assertEquals(4L, preview.getState().getHunter().getRestrictionUntil());
        assertEquals(preview, applied);
    }

    @Test
    void rollsBackEntireCalculationWhenContactTimerOverflows() {
        assertTimerOverflow(new Consequences(1, false, 2, false));
        assertTimerOverflow(new Consequences(1, false, 0, true));
    }

    private static void assertTimerOverflow(Consequences outcome) {
        long start = Long.MAX_VALUE - 5;
        TimelineAction contact = contact(4, FREE_CELL, new ContactOutcome(NONE, outcome));
        TimelineAction safe = waitFor(2);
        ActiveAction earlierMove = new ActiveAction(move(1, FREE_CELL), start);
        TimelineState initial = state(start, hunter(null), opponent(earlierMove), contact, safe);

        assertThrows(ArithmeticException.class, () -> CombatTimeline.resolve(initial, contact));
        assertEquals(start, initial.getNow());
        assertEquals(10, initial.getOpponent().getHealth());
        assertEquals(OPPONENT_CELL, initial.getOpponent().getCell());
        assertEquals(earlierMove, initial.getOpponent().getAction());
        assertNull(initial.getOpponent().getRecoveryUntil());
        assertNull(initial.getOpponent().getRestrictionUntil());
        assertTypes(CombatTimeline.resolve(initial, safe), ACTION_STARTED, POSITION_CHANGED, ACTION_COMPLETED, ACTION_COMPLETED);
    }

    private static void assertRestrictionAtDecision(long recovery, long decision, boolean contactAvailable) {
        TimelineAction restricted = contact(6, OPPONENT_CELL,
            new ContactOutcome(new Consequences(0, false, recovery, true), NONE));
        TimelineAction fallback = waitFor(2);
        TimelineResult result = CombatTimeline.resolve(state(0, hunter(null), opponent(null), restricted, fallback), restricted);
        assertEquals(DECISION, result.getStop());
        assertEquals(decision, result.getState().getNow());
        assertEquals(contactAvailable, result.getAvailableCommands().contains(restricted));
        assertTrue(result.getAvailableCommands().contains(fallback));

        List<TimelineEvent> expired = eventsOf(result, RESTRICTION_EXPIRED);
        if (contactAvailable) {
            assertEquals(1, expired.size());
            assertEquals(8, expired.get(0).getTime());
            assertNull(result.getState().getHunter().getRestrictionUntil());
        } else {
            assertTrue(expired.isEmpty());
            assertEquals(8L, result.getState().getHunter().getRestrictionUntil());
            assertThrows(IllegalArgumentException.class, () -> CombatTimeline.resolve(result.getState(), restricted));
        }
    }

    private static void assertRestrictionRefresh(long delay, long deadline) {
        ContactOutcome outcome = new ContactOutcome(NONE, new Consequences(0, false, 0, true));
        TimelineAction first = contact(6, OPPONENT_CELL, outcome);
        TimelineAction refresh = contact(delay, OPPONENT_CELL, outcome);
        TimelineAction later = waitFor(3);
        TimelineResult beforeRefresh = CombatTimeline.resolve(state(0, hunter(null), opponent(null), first, refresh, later), first);
        assertEquals(8L, beforeRefresh.getState().getOpponent().getRestrictionUntil());

        TimelineResult afterRefresh = CombatTimeline.resolve(beforeRefresh.getState(), refresh);
        assertEquals(deadline, afterRefresh.getState().getOpponent().getRestrictionUntil());
        assertTrue(eventsOf(afterRefresh, RESTRICTION_EXPIRED).isEmpty());
        assertEquals(deadline, eventsOf(afterRefresh, RESTRICTION_APPLIED).get(0).getDeadline());

        TimelineResult end = CombatTimeline.resolve(afterRefresh.getState(), later);
        List<TimelineEvent> expired = eventsOf(end, RESTRICTION_EXPIRED);
        assertEquals(1, expired.size());
        assertEquals(deadline, expired.get(0).getTime());
        assertNull(end.getState().getOpponent().getRestrictionUntil());
    }

    private static void assertRecoveryRefresh(long time, long duration, long deadline, boolean extended) {
        TimelineAction impact = contact(time, OPPONENT_CELL, new ContactOutcome(NONE, new Consequences(0, false, duration, false)));
        TimelineAction next = waitFor(8);
        Participant recovering = new Participant(2, Role.OPPONENT, OPPONENT_CELL, 10, null, 8L, null);
        TimelineResult hit = CombatTimeline.resolve(state(0, hunter(null), recovering, impact, next), impact);
        assertEquals(deadline, hit.getState().getOpponent().getRecoveryUntil());
        assertEquals(extended, !eventsOf(hit, RECOVERY_EXTENDED).isEmpty());
        assertTrue(eventsOf(hit, RECOVERY_ENDED).isEmpty());

        TimelineResult end = CombatTimeline.resolve(hit.getState(), next);
        List<TimelineEvent> ended = eventsOf(end, RECOVERY_ENDED);
        assertEquals(1, ended.size());
        assertEquals(deadline, ended.get(0).getTime());
        assertNull(end.getState().getOpponent().getRecoveryUntil());
    }

    private static Consequences damage(int damage) {
        return new Consequences(damage, false, 0, false);
    }

    private static void assertTypes(TimelineResult result, Type... types) {
        assertEquals(Arrays.asList(types), result.getEvents().stream().map(TimelineEvent::getType).collect(Collectors.toList()));
    }

    private static List<TimelineEvent> eventsOf(TimelineResult result, Type type) {
        return result.getEvents().stream().filter(event -> event.getType() == type).collect(Collectors.toList());
    }

    private static TimelineEvent actionEvent(TimelineEvent.Type type, long time, int id, ActiveAction action) {
        return TimelineEvent.builder().type(type).time(time).participantId(id).action(action).build();
    }

    private static Participant hunter(ActiveAction action) {
        return new Participant(1, Role.HUNTER, HUNTER_CELL, 10, action);
    }

    private static Participant opponent(ActiveAction action) {
        return new Participant(2, Role.OPPONENT, OPPONENT_CELL, 10, action);
    }

    private static TimelineState state(long now, Participant first, Participant second, TimelineAction... commands) {
        return new TimelineState(now, first, second, new HashSet<>(Arrays.asList(commands)));
    }
}
