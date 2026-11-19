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
    public enum Kind { ATTACK, MOVE, BREAKOUT }
    public enum RejectionReason { NOT_TACTICAL, HUNTER_DEFEATED, INVALID_TARGET, INVALID_DISTANCE,
        BLOCKED_START, BLOCKED_CENTER, BLOCKED_PATH, OCCUPIED_PATH }
    public enum Effect { BITE, HIT }
    public enum EventType { HIT, ATTACK_MISS, BITE, BITE_MISS, PREPARATION_STARTED, RECOVERY_STARTED, RECOVERY_ENDED, BREAKOUT_MOVED, BREAKOUT_INTERRUPTED, TACTICAL_EXITED }
    public static final class Event {
        private final EventType type;
        private final int offset;
        public Event(EventType type, int offset) {
            if (type == null || offset < 1) throw new IllegalArgumentException("Invalid event");
            this.type = type;
            this.offset = offset;
        }
        public EventType getType() { return type; }
        public int getOffset() { return offset; }
    }
    private final Kind kind;
    private final RejectionReason rejectionReason;
    private final int plannedDuration;
    private final WorldPosition positionBefore, positionAfter;
    private final SceneMode modeBefore, modeAfter;
    private final PrototypeBiteState biteBefore, biteAfter;
    private final List<Event> events;
    private final Outcome outcome;
    private final int hunterBefore, hunterAfter, creatureBefore, creatureAfter, duration;
    private final List<Effect> effects;

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
        this.kind = kind;
        this.rejectionReason = rejectionReason;
        this.plannedDuration = plannedDuration;
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
        this.duration = duration;
        this.effects = Collections.unmodifiableList(new ArrayList<>(effects));
    }
    public Kind getKind() { return kind; }
    public RejectionReason getRejectionReason() { return rejectionReason; }
    public int getPlannedDuration() { return plannedDuration; }
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
    public int getDuration() { return duration; }
    public int getHunterDamage() { return hunterBefore - hunterAfter; }
    public int getCreatureDamage() { return creatureBefore - creatureAfter; }
    public boolean isHunterDefeated() { return hunterAfter == 0; }
    public boolean isCreatureDefeated() { return creatureAfter == 0; }
    public List<Effect> getEffects() { return effects; }
}
