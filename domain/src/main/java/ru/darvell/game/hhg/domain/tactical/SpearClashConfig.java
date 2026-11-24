package ru.darvell.game.hhg.domain.tactical;

import java.util.Arrays;
import lombok.Getter;
import ru.darvell.game.hhg.domain.combat.outcome.OutcomeTable;
import ru.darvell.game.hhg.domain.combat.outcome.WeightedOutcome;

import static ru.darvell.game.hhg.domain.tactical.SpearClashState.Result.*;

@Getter
public final class SpearClashConfig {
    public static final int MIN_REACH_CELLS = 1;
    public static final int MAX_REACH_CELLS = 2;
    public static final int HALF_ANGLE_DEGREES = 45;
    public static final long GUARD_LIMIT_QUANTA = 8;

    private final SpearCombatProfile profile;
    private final long turnQuanta;
    private final long lowerQuanta;
    private final long guardLimitQuanta;
    private final double minReachCells;
    private final double maxReachCells;
    private final double halfAngleDegrees;
    private final Facing facing;
    private final long seed;
    private final long braceQuanta;
    private final long preparationQuanta;
    private final long flightQuanta;
    private final long wolfStepQuanta;
    private final OutcomeTable weights;

    public SpearClashConfig(Facing facing, long seed) {
        this(facing, seed, 2, 2, 2, 50, 30, 20);
    }

    public SpearClashConfig(Facing facing, long seed, long braceQuanta, long preparationQuanta,
                            long flightQuanta, double repelWeight, double passWeight, double closeWeight) {
        this(facing, seed, braceQuanta, preparationQuanta, flightQuanta, 2, repelWeight, passWeight, closeWeight);
    }

    public SpearClashConfig(Facing facing, long seed, long braceQuanta, long preparationQuanta,
                            long flightQuanta, long wolfStepQuanta, double repelWeight,
                            double passWeight, double closeWeight) {
        this(facing, seed, braceQuanta, preparationQuanta, flightQuanta, wolfStepQuanta,
            repelWeight, passWeight, closeWeight, SpearCombatProfile.regression());
    }

    public SpearClashConfig(Facing facing, long seed, long braceQuanta, long preparationQuanta,
                            long flightQuanta, long wolfStepQuanta, double repelWeight,
                            double passWeight, double closeWeight, SpearCombatProfile profile) {
        this(facing, seed, braceQuanta, preparationQuanta, flightQuanta, wolfStepQuanta,
            repelWeight, passWeight, closeWeight, profile, 2, 2, GUARD_LIMIT_QUANTA,
            MIN_REACH_CELLS, MAX_REACH_CELLS, HALF_ANGLE_DEGREES);
    }

    public SpearClashConfig(Facing facing, long seed, long braceQuanta, long preparationQuanta,
                            long flightQuanta, long wolfStepQuanta, double repelWeight,
                            double passWeight, double closeWeight, SpearCombatProfile profile,
                            long turnQuanta, long lowerQuanta, long guardLimitQuanta,
                            double minReachCells, double maxReachCells, double halfAngleDegrees) {
        this.turnQuanta = positiveQuanta(turnQuanta);
        this.lowerQuanta = positiveQuanta(lowerQuanta);
        this.guardLimitQuanta = positiveQuanta(guardLimitQuanta);
        if (!Double.isFinite(minReachCells) || !Double.isFinite(maxReachCells)
            || minReachCells <= 0 || maxReachCells < Math.sqrt(2) || minReachCells > maxReachCells
            || !Double.isFinite(halfAngleDegrees) || halfAngleDegrees < 0 || halfAngleDegrees > 180) {
            throw new IllegalArgumentException("Invalid spear geometry");
        }
        this.minReachCells = minReachCells;
        this.maxReachCells = maxReachCells;
        this.halfAngleDegrees = halfAngleDegrees;
        if (profile == null) throw new IllegalArgumentException("Profile is required");
        this.profile = profile;
        if (facing == null) throw new IllegalArgumentException("Facing is required");
        this.facing = facing;
        this.seed = seed;
        this.braceQuanta = positiveQuanta(braceQuanta);
        this.preparationQuanta = positiveQuanta(preparationQuanta);
        this.flightQuanta = positiveQuanta(flightQuanta);
        this.wolfStepQuanta = positiveQuanta(wolfStepQuanta);
        weights = OutcomeTable.of(Arrays.asList(new WeightedOutcome(REPELLED.name(), repelWeight),
            new WeightedOutcome(GRAZED_PAST.name(), passWeight),
            new WeightedOutcome(BROKE_THROUGH.name(), closeWeight)));
    }

    public static SpearClashConfig v2(Facing facing, long seed) {
        return new SpearClashConfig(facing, seed, 2, 2, 2, 2, 35, 35, 30, SpearCombatProfile.v2());
    }

    private static long positiveQuanta(long duration) {
        if (duration <= 0) {
            throw new IllegalArgumentException("Duration must be positive");
        }
        return duration;
    }
}
