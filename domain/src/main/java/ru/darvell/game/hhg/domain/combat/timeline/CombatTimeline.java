package ru.darvell.game.hhg.domain.combat.timeline;

import ru.darvell.game.hhg.domain.combat.timeline.ContactOutcome.Consequences;
import ru.darvell.game.hhg.domain.combat.timeline.TimelineEvent.Type;
import ru.darvell.game.hhg.domain.combat.timeline.TimelineResult.Stop;
import ru.darvell.game.hhg.domain.combat.timeline.TimelineState.ActiveAction;
import ru.darvell.game.hhg.domain.combat.timeline.TimelineState.Participant;
import ru.darvell.game.hhg.domain.tactical.CellCoordinates;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/** Чистые переходы между границами тактической симуляции. */
public final class CombatTimeline {
    public static final long QUANTA_PER_TICK = 2;

    private CombatTimeline() {
    }

    /** Досчитывает подготовленный снимок до первой границы. */
    public static TimelineResult advance(TimelineState state) {
        return advance(state, new ArrayList<>());
    }

    /**
     * Рассчитывает команду на открытом решении. Для preview достаточно прочитать результат.
     * Для применения вызывающая сторона принимает возвращённый снимок целиком.
     */
    public static TimelineResult resolve(TimelineState state, TimelineAction command) {
        if (availableCommands(state).isEmpty()) {
            throw new IllegalStateException("Player decision is closed");
        }
        if (!state.getCommands().contains(command) || !isAllowedByRestriction(state.getHunter(), command)) {
            throw new IllegalArgumentException("Command is not available to this participant");
        }
        ActiveAction action = new ActiveAction(command, state.getNow());
        Participant hunter = state.getHunter().withAction(action);
        List<TimelineEvent> events = new ArrayList<>();
        events.add(fact(Type.ACTION_STARTED, state.getNow(), hunter).action(action).build());
        return advance(new TimelineState(state.getNow(), hunter, state.getOpponent(), state.getCommands()), events);
    }

    /** Правила снимка определяют ближайший срок, порядок пакета и право остановки. */
    public static <S> S advanceToBoundary(S state, Predicate<S> atBoundary,
                                          Function<S, Long> nextDeadline, BiFunction<S, Long, S> resolveBatch) {
        while (!atBoundary.test(state)) {
            Long next = nextDeadline.apply(state);
            if (next == null) {
                break;
            }
            state = resolveBatch.apply(state, next);
        }
        return state;
    }

    private static TimelineResult advance(TimelineState state, List<TimelineEvent> events) {
        TimelineState boundary = advanceToBoundary(state,
            snapshot -> isTerminal(snapshot) || !availableCommands(snapshot).isEmpty(),
            CombatTimeline::nextDeadline, (snapshot, time) -> resolveBatch(snapshot, time, events));
        Set<TimelineAction> available = availableCommands(boundary);
        Stop stop = isTerminal(boundary) ? Stop.TERMINAL
            : available.isEmpty() ? Stop.NO_PROGRESS : Stop.DECISION;
        return new TimelineResult(boundary, stop, events, available);
    }

    private static boolean isTerminal(TimelineState state) {
        return state.getHunter().getHealth() == 0 || state.getOpponent().getHealth() == 0;
    }

    private static Set<TimelineAction> availableCommands(TimelineState state) {
        Participant hunter = state.getHunter();
        if (isTerminal(state) || hunter.getAction() != null || hunter.getRecoveryUntil() != null) {
            return Collections.emptySet();
        }
        Set<TimelineAction> available = new HashSet<>();
        for (TimelineAction command : state.getCommands()) {
            if (isAllowedByRestriction(hunter, command) && command.getDuration() <= Long.MAX_VALUE - state.getNow()) {
                available.add(command);
            }
        }
        return Collections.unmodifiableSet(available);
    }

    private static boolean isAllowedByRestriction(Participant participant, TimelineAction command) {
        return participant.getRestrictionUntil() == null || command.getKind() != TimelineAction.Kind.CONTACT;
    }

    private static Long nextDeadline(TimelineState state) {
        Long next = null;
        for (Participant participant : state.getParticipants()) {
            Long deadline = participant.nextDeadline();
            if (deadline != null && (next == null || deadline < next)) {
                next = deadline;
            }
        }
        return next;
    }

    private static TimelineState resolveBatch(TimelineState state, long time, List<TimelineEvent> events) {
        List<Participant> participants = new ArrayList<>(state.getParticipants());
        resolveMoves(participants, time, events);
        BatchParticipant first = new BatchParticipant(participants.get(0));
        BatchParticipant second = new BatchParticipant(participants.get(1));
        resolveContact(first, second, time, events);
        resolveContact(second, first, time, events);
        first.applyDamage(time, events);
        second.applyDamage(time, events);
        first.reportDefeat(time, events);
        second.reportDefeat(time, events);
        first.finishAction(time, events);
        second.finishAction(time, events);
        first.interruptAction(time, events);
        second.interruptAction(time, events);
        return new TimelineState(time, first.finishTimers(time, events), second.finishTimers(time, events),
            state.getCommands());
    }

    private static void resolveContact(BatchParticipant initiator, BatchParticipant target, long time,
                                       List<TimelineEvent> events) {
        ActiveAction action = dueAction(initiator.before, time);
        if (action == null || action.getCommand().getKind() != TimelineAction.Kind.CONTACT
            || !action.getCommand().getCell().equals(target.before.getCell())) {
            return;
        }
        ContactOutcome outcome = action.getCommand().getOutcome();
        events.add(fact(Type.CONTACT, time, initiator.before)
            .action(action).targetId(target.before.getId()).build());
        initiator.collect(outcome.getInitiator());
        target.collect(outcome.getTarget());
    }

    private static void resolveMoves(List<Participant> participants, long time, List<TimelineEvent> events) {
        for (int index = 0; index < participants.size(); index++) {
            Participant participant = participants.get(index);
            ActiveAction action = dueAction(participant, time);
            if (action == null || action.getCommand().getKind() != TimelineAction.Kind.MOVE) {
                continue;
            }
            CellCoordinates destination = action.getCommand().getCell();
            boolean occupied = participants.stream()
                .anyMatch(actor -> actor.getHealth() > 0 && actor.getCell().equals(destination));
            if (!occupied) {
                participants.set(index, participant.withCell(destination));
            }
            events.add(fact(occupied ? Type.MOVE_BLOCKED : Type.POSITION_CHANGED, time, participant)
                .cell(destination).build());
        }
    }

    private static ActiveAction dueAction(Participant participant, long time) {
        ActiveAction action = participant.getAction();
        return action != null && action.getFinish() == time ? action : null;
    }

    private static TimelineEvent.TimelineEventBuilder fact(Type type, long time, Participant participant) {
        return TimelineEvent.builder().type(type).time(time).participantId(participant.getId());
    }

    /** Локальные накопители пакета. Снимок создаётся только после всех стадий. */
    private static final class BatchParticipant {
        private final Participant before;
        private long damage;
        private boolean interrupt;
        private long recoveryDuration;
        private boolean restriction;
        private int health;
        private ActiveAction action;

        private BatchParticipant(Participant before) {
            this.before = before;
            this.health = before.getHealth();
            this.action = before.getAction();
        }

        private void collect(Consequences consequences) {
            damage += consequences.getDamage();
            interrupt |= consequences.isInterrupt();
            recoveryDuration = Math.max(recoveryDuration, consequences.getRecoveryDuration());
            restriction |= consequences.isRestriction();
        }

        private void applyDamage(long time, List<TimelineEvent> events) {
            health = (int) Math.max(0, health - damage);
            if (health != before.getHealth()) {
                events.add(fact(Type.HEALTH_CHANGED, time, before).health(health).build());
            }
        }

        private void reportDefeat(long time, List<TimelineEvent> events) {
            if (health == 0) {
                events.add(fact(Type.DEFEATED, time, before).build());
            }
        }

        private void finishAction(long time, List<TimelineEvent> events) {
            if (action != null && action.getFinish() == time) {
                events.add(fact(Type.ACTION_COMPLETED, time, before).action(action).build());
                action = null;
            }
        }

        private void interruptAction(long time, List<TimelineEvent> events) {
            if (action != null && (interrupt || health == 0)) {
                events.add(fact(Type.ACTION_INTERRUPTED, time, before).action(action).build());
                action = null;
            }
        }

        private Participant finishTimers(long time, List<TimelineEvent> events) {
            if (health == 0) {
                return new Participant(before.getId(), before.getRole(), before.getCell(), health, null);
            }

            Long recoveryUntil = before.getRecoveryUntil();
            if (recoveryDuration > 0) {
                if (action != null) {
                    throw new IllegalArgumentException("Recovery requires finishing or interrupting the active action");
                }
                long proposed = Math.addExact(time, recoveryDuration);
                if (recoveryUntil == null || proposed > recoveryUntil) {
                    Type type = recoveryUntil == null ? Type.RECOVERY_STARTED : Type.RECOVERY_EXTENDED;
                    recoveryUntil = proposed;
                    events.add(fact(type, time, before).deadline(recoveryUntil).build());
                }
            }
            Long restrictionUntil = before.getRestrictionUntil();
            if (restriction) {
                restrictionUntil = Math.addExact(time, QUANTA_PER_TICK);
                events.add(fact(Type.RESTRICTION_APPLIED, time, before).deadline(restrictionUntil).build());
            }
            if (recoveryUntil != null && recoveryUntil == time) {
                events.add(fact(Type.RECOVERY_ENDED, time, before).deadline(recoveryUntil).build());
                recoveryUntil = null;
            }
            if (restrictionUntil != null && restrictionUntil == time) {
                events.add(fact(Type.RESTRICTION_EXPIRED, time, before).deadline(restrictionUntil).build());
                restrictionUntil = null;
            }
            return new Participant(before.getId(), before.getRole(), before.getCell(), health, action,
                recoveryUntil, restrictionUntil);
        }
    }
}
