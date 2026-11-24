package ru.darvell.game.hhg.domain.tactical;

import ru.darvell.game.hhg.domain.world.WorldPosition;
import ru.darvell.game.hhg.domain.scene.SceneMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Снимок одного разрешения; факты предназначены для показа, не для повторного применения. */
public final class TacticalAttackResult {
    public enum Outcome { HIT, MISS, INTERRUPTED, NOT_TACTICAL, INVALID_TARGET, TARGET_DEFEATED,
        HUNTER_DEFEATED, OUT_OF_RANGE, BLOCKED, MOVE, REJECTED, MOVED_STILL_TACTICAL, ESCAPED }
    public enum Kind { ATTACK, MOVE, BREAKOUT, TURN, BRACE, LOWER_SPEAR }
    public enum RejectionReason { NOT_TACTICAL, HUNTER_DEFEATED, INVALID_TARGET, INVALID_DISTANCE,
        BLOCKED_START, BLOCKED_CENTER, BLOCKED_PATH, OCCUPIED_PATH }
    public enum Effect { BITE, HIT }
    public enum EventType {
        HIT, ATTACK_MISS, BITE, BITE_MISS, PREPARATION_STARTED, RECOVERY_STARTED, RECOVERY_ENDED,
        BREAKOUT_MOVED, BREAKOUT_INTERRUPTED, TACTICAL_EXITED, TURN_COMPLETED, BRACE_READY, SPEAR_LOWERED,
        LEAP_LAUNCHED, LEAP_CANCELLED, LEAP_CONTACT, LEAP_MISSED, LEAP_BLOCKED, ACTION_INTERRUPTED,
        BREAKOUT_BLOCKED, HUNTER_RECOVERY_STARTED, HUNTER_RECOVERY_ENDED, WOLF_RECOVERY_STARTED, WOLF_RECOVERY_ENDED,
        WOLF_STEP_STARTED, WOLF_FLANK_STARTED, WOLF_MOVED, WOLF_STEP_BLOCKED, WOLF_NO_ROUTE,
        LEAP_PREPARATION_STARTED, BITE_PREPARATION_STARTED, STANCE_RELEASED
    }
    public static final class Event {
        private final EventType type;
        private final long offsetQuanta;
        public Event(EventType type, int offset) {
            this(type, Math.multiplyExact((long) offset, 2));
        }
        public Event(EventType type, long offsetQuanta) {
            if (type == null || offsetQuanta < 1) throw new IllegalArgumentException("Invalid event");
            this.type = type;
            this.offsetQuanta = offsetQuanta;
        }
        public EventType getType() { return type; }
        public int getOffset() { return wholeTicks(offsetQuanta); }
        public long getOffsetQuanta() { return offsetQuanta; }
    }
    private final Kind kind;
    private final RejectionReason rejectionReason;
    private final long plannedDurationQuanta;
    private final long startQuanta;
    private final WorldPosition positionBefore, positionAfter;
    private final SceneMode modeBefore, modeAfter;
    private final PrototypeBiteState biteBefore, biteAfter;
    private final List<Event> events;
    private final Outcome outcome;
    private final int hunterBefore, hunterAfter, creatureBefore, creatureAfter;
    private final long durationQuanta;
    private final List<Effect> effects;
    private final ClashFacts clash;

    /** An encounter fact uses quanta relative to the command and may describe its starting mark. */
    @lombok.Value
    public static class ClashFact {
        EventType type;
        long offset;
        SpearClashState state;
        long motionStart;
        List<WorldPosition> route;
        SpearClashState.Result contact;
        long hunterRecovery;
        long wolfRecovery;
        int contactIndex;
        int hunterHealth;
        int wolfHealth;

        public ClashFact(EventType type, long offset, SpearClashState state, long motionStart,
                         List<WorldPosition> route, SpearClashState.Result contact,
                         long hunterRecovery, long wolfRecovery, int contactIndex, int hunterHealth, int wolfHealth) {
            this.contactIndex = contactIndex;
            this.hunterHealth = hunterHealth;
            this.wolfHealth = wolfHealth;
            this.type = type;
            this.offset = offset;
            this.state = state;
            this.motionStart = motionStart;
            this.route = Collections.unmodifiableList(new ArrayList<>(route));
            this.contact = contact;
            this.hunterRecovery = hunterRecovery;
            this.wolfRecovery = wolfRecovery;
        }
    }

    @lombok.Value
    public static class ClashFacts {
        SpearClashState before;
        SpearClashState after;
        WorldPosition wolfBefore;
        WorldPosition wolfAfter;
        List<ClashFact> timeline;
        SpearClashState.StopReason stopReason;

        public ClashFacts(SpearClashState before, SpearClashState after, WorldPosition wolfBefore,
                          WorldPosition wolfAfter, List<ClashFact> timeline, SpearClashState.StopReason stopReason) {
            this.before = before;
            this.after = after;
            this.wolfBefore = wolfBefore;
            this.wolfAfter = wolfAfter;
            this.timeline = Collections.unmodifiableList(new ArrayList<>(timeline));
            this.stopReason = stopReason;
        }

        public ClashFact getLastContact() {
            for (int i = timeline.size() - 1; i >= 0; i--) {
                ClashFact fact = timeline.get(i);
                if (fact.contact != null) return fact;
            }
            return null;
        }
    }

    public TacticalAttackResult(Outcome outcome, int hunterBefore, int hunterAfter,
                               int creatureBefore, int creatureAfter, int duration,
                               PrototypeBiteState biteBefore, PrototypeBiteState biteAfter, List<Event> events) {
        this(outcome, hunterBefore, hunterAfter, creatureBefore, creatureAfter, duration,
            biteBefore, biteAfter, events, outcome == Outcome.MOVE ? Kind.MOVE : Kind.ATTACK,
            null, duration, null, null, null, null);
    }

    public TacticalAttackResult(Outcome outcome, int hunterBefore, int hunterAfter,
                               int creatureBefore, int creatureAfter, int duration,
                               PrototypeBiteState biteBefore, PrototypeBiteState biteAfter, List<Event> events,
                               Kind kind, RejectionReason rejectionReason, int plannedDuration,
                               WorldPosition positionBefore, WorldPosition positionAfter,
                               SceneMode modeBefore, SceneMode modeAfter) {
        this(outcome, hunterBefore, hunterAfter, creatureBefore, creatureAfter, duration, biteBefore, biteAfter,
            events, kind, rejectionReason, plannedDuration, positionBefore, positionAfter, modeBefore, modeAfter, null);
    }

    public TacticalAttackResult(Outcome outcome, int hunterBefore, int hunterAfter,
                               int creatureBefore, int creatureAfter, int duration,
                               PrototypeBiteState biteBefore, PrototypeBiteState biteAfter, List<Event> events,
                               Kind kind, RejectionReason rejectionReason, int plannedDuration,
                               WorldPosition positionBefore, WorldPosition positionAfter, SceneMode modeBefore,
                               SceneMode modeAfter, ClashFacts clash) {
        this(outcome, hunterBefore, hunterAfter, creatureBefore, creatureAfter,
            Math.multiplyExact((long) duration, 2), biteBefore, biteAfter, events, kind, rejectionReason,
            Math.multiplyExact((long) plannedDuration, 2), positionBefore, positionAfter, modeBefore, modeAfter, clash, 0);
    }

    public TacticalAttackResult(Outcome outcome, int hunterBefore, int hunterAfter,
                               int creatureBefore, int creatureAfter, long durationQuanta,
                               PrototypeBiteState biteBefore, PrototypeBiteState biteAfter, List<Event> events,
                               Kind kind, RejectionReason rejectionReason, long plannedDurationQuanta,
                               WorldPosition positionBefore, WorldPosition positionAfter, SceneMode modeBefore,
                               SceneMode modeAfter, ClashFacts clash, long startQuanta) {
        if (durationQuanta < 0 || plannedDurationQuanta < 0) throw new IllegalArgumentException("Negative duration");
        Math.addExact(startQuanta, durationQuanta);
        this.startQuanta = startQuanta;
        this.clash = clash;
        this.kind = kind;
        this.rejectionReason = rejectionReason;
        this.plannedDurationQuanta = plannedDurationQuanta;
        this.positionBefore = positionBefore;
        this.positionAfter = positionAfter;
        this.modeBefore = modeBefore;
        this.modeAfter = modeAfter;
        this.biteBefore = biteBefore;
        this.biteAfter = biteAfter;
        this.events = Collections.unmodifiableList(new ArrayList<>(events));
        List<Effect> effects = new ArrayList<>();
        for (Event event : events) {
            if (event.type == EventType.HIT) effects.add(Effect.HIT);
            if (event.type == EventType.BITE) effects.add(Effect.BITE);
        }
        this.outcome = outcome;
        this.hunterBefore = hunterBefore; this.hunterAfter = hunterAfter;
        this.creatureBefore = creatureBefore; this.creatureAfter = creatureAfter;
        this.durationQuanta = durationQuanta;
        this.effects = Collections.unmodifiableList(new ArrayList<>(effects));
    }
    public ClashFacts getClash() { return clash; }
    public Kind getKind() { return kind; }
    public RejectionReason getRejectionReason() { return rejectionReason; }
    public int getPlannedDuration() { return wholeTicks(plannedDurationQuanta); }
    public long getPlannedDurationQuanta() { return plannedDurationQuanta; }
    public WorldPosition getPositionBefore() { return positionBefore; }
    public WorldPosition getPositionAfter() { return positionAfter; }
    public SceneMode getModeBefore() { return modeBefore; }
    public SceneMode getModeAfter() { return modeAfter; }
    public PrototypeBiteState getBiteBefore() { return biteBefore; }
    public PrototypeBiteState getBiteAfter() { return biteAfter; }
    public List<Event> getEvents() { return events; }
    public Outcome getOutcome() { return outcome; }
    public boolean isAccepted() { return outcome == Outcome.HIT || outcome == Outcome.MISS
        || outcome == Outcome.INTERRUPTED || outcome == Outcome.MOVE
        || outcome == Outcome.MOVED_STILL_TACTICAL || outcome == Outcome.ESCAPED; }
    public int getHunterBefore() { return hunterBefore; }
    public int getHunterAfter() { return hunterAfter; }
    public int getCreatureBefore() { return creatureBefore; }
    public int getCreatureAfter() { return creatureAfter; }
    public int getDuration() { return wholeTicks(durationQuanta); }
    public long getDurationQuanta() { return durationQuanta; }
    public long getStartQuanta() { return startQuanta; }
    public long getEndQuanta() { return Math.addExact(startQuanta, durationQuanta); }
    private static int wholeTicks(long quanta) {
        if (quanta % 2 != 0) throw new IllegalStateException("Expected whole tactical ticks");
        return Math.toIntExact(quanta / 2);
    }
    public int getHunterDamage() { return hunterBefore - hunterAfter; }
    public int getCreatureDamage() { return creatureBefore - creatureAfter; }
    public boolean isHunterDefeated() { return hunterAfter == 0; }
    public boolean isCreatureDefeated() { return creatureAfter == 0; }
    public List<Effect> getEffects() { return effects; }
}
