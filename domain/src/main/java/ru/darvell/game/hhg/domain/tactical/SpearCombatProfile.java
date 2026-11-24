package ru.darvell.game.hhg.domain.tactical;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import lombok.Value;
import ru.darvell.game.hhg.domain.tactical.SpearClashState.Result;

/** Неизменяемые последствия попытки атаки; ordinary combat выбирается без spear config. */
public final class SpearCombatProfile {
    public enum Id { REGRESSION, V2 }

    @Value
    public static class Consequence {
        int hunterDamage;
        int wolfDamage;
        long hunterRecovery;
        long wolfRecovery;

        public Consequence(int hunterDamage, int wolfDamage, long hunterRecovery, long wolfRecovery) {
            if (hunterDamage < 0 || wolfDamage < 0 || hunterRecovery < 0 || wolfRecovery <= 0) {
                throw new IllegalArgumentException("Invalid attack consequence");
            }
            this.hunterDamage = hunterDamage;
            this.wolfDamage = wolfDamage;
            this.hunterRecovery = hunterRecovery;
            this.wolfRecovery = wolfRecovery;
        }
    }

    private final Id id;
    private final Map<Result, Consequence> consequences;
    private final Consequence biteHit;
    private final Consequence biteMiss;
    private final long bitePreparation;
    private final long warning;

    public SpearCombatProfile(Id id, Map<Result, Consequence> consequences,
                              Consequence biteHit, Consequence biteMiss, long bitePreparation, long warning) {
        if (id == null || consequences == null || biteHit == null || biteMiss == null
            || bitePreparation <= 0 || warning <= 0) throw new IllegalArgumentException("Invalid profile");
        EnumMap<Result, Consequence> copy = new EnumMap<>(Result.class);
        copy.putAll(consequences);
        for (Result result : Result.values()) {
            if (copy.get(result) == null) throw new IllegalArgumentException("Missing consequence: " + result);
        }
        this.id = id;
        this.consequences = Collections.unmodifiableMap(copy);
        this.biteHit = biteHit;
        this.biteMiss = biteMiss;
        this.bitePreparation = bitePreparation;
        this.warning = warning;
    }

    public Id getId() { return id; }
    public boolean isV2() { return id == Id.V2; }
    public Consequence consequence(Result result) { return consequences.get(result); }
    public Consequence bite(boolean hit) { return hit ? biteHit : biteMiss; }
    public long getBitePreparation() { return bitePreparation; }
    public long getWarning() { return warning; }

    public static SpearCombatProfile regression() { return defaults(Id.REGRESSION); }
    public static SpearCombatProfile v2() { return defaults(Id.V2); }

    private static SpearCombatProfile defaults(Id id) {
        boolean v2 = id == Id.V2;
        EnumMap<Result, Consequence> values = new EnumMap<>(Result.class);
        values.put(Result.REPELLED, new Consequence(0, 1, 2, v2 ? 4 : 6));
        values.put(Result.GRAZED_PAST, new Consequence(v2 ? 1 : 0, 1, 4, v2 ? 2 : 4));
        values.put(Result.BROKE_THROUGH, new Consequence(v2 ? 1 : 0, 0, v2 ? 4 : 6, v2 ? 1 : 2));
        values.put(Result.HIT, new Consequence(1, 0, 2, 4));
        values.put(Result.MISS, new Consequence(0, 0, 0, 4));
        values.put(Result.BLOCKED, new Consequence(0, 0, 0, 4));
        values.put(Result.STOPPED, new Consequence(0, 0, 2, 4));
        return new SpearCombatProfile(id, values, new Consequence(1, 0, 2, 2),
            new Consequence(0, 0, 0, 2), 1, 1);
    }
}
