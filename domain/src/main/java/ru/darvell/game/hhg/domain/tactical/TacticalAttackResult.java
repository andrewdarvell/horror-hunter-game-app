package ru.darvell.game.hhg.domain.tactical;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Снимок одного разрешения; факты предназначены для показа, не для повторного применения. */
public final class TacticalAttackResult {
    public enum Outcome { HIT, INTERRUPTED, NOT_TACTICAL, INVALID_TARGET, TARGET_DEFEATED,
        HUNTER_DEFEATED, OUT_OF_RANGE, BLOCKED, MOVE }
    public enum Effect { BITE, HIT }
    public enum EventType { HIT, BITE, BITE_MISS, PREPARATION_STARTED, RECOVERY_STARTED, RECOVERY_ENDED }
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
    private final PrototypeBiteState biteBefore, biteAfter;
    private final List<Event> events;
    private final Outcome outcome;
    private final int hunterBefore, hunterAfter, creatureBefore, creatureAfter, duration;
    private final List<Effect> effects;

    public TacticalAttackResult(Outcome outcome, int hunterBefore, int hunterAfter,
                               int creatureBefore, int creatureAfter, int duration,
                               PrototypeBiteState biteBefore, PrototypeBiteState biteAfter, List<Event> events) {
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
    public PrototypeBiteState getBiteBefore() { return biteBefore; }
    public PrototypeBiteState getBiteAfter() { return biteAfter; }
    public List<Event> getEvents() { return events; }
    public Outcome getOutcome() { return outcome; }
    public boolean isAccepted() { return outcome == Outcome.HIT || outcome == Outcome.INTERRUPTED || outcome == Outcome.MOVE; }
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
