package ru.darvell.game.hhg.domain.tactical;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Снимок одного разрешения; факты предназначены для показа, не для повторного применения. */
public final class TacticalAttackResult {
    public enum Outcome { HIT, INTERRUPTED, NOT_TACTICAL, INVALID_TARGET, TARGET_DEFEATED,
        HUNTER_DEFEATED, OUT_OF_RANGE, BLOCKED, MOVE }
    public enum Effect { BITE, HIT }
    private final Outcome outcome;
    private final int hunterBefore, hunterAfter, creatureBefore, creatureAfter, duration;
    private final List<Effect> effects;

    public TacticalAttackResult(Outcome outcome, int hunterBefore, int hunterAfter,
                                int creatureBefore, int creatureAfter, int duration, List<Effect> effects) {
        this.outcome = outcome;
        this.hunterBefore = hunterBefore; this.hunterAfter = hunterAfter;
        this.creatureBefore = creatureBefore; this.creatureAfter = creatureAfter;
        this.duration = duration;
        this.effects = Collections.unmodifiableList(new ArrayList<>(effects));
    }
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
