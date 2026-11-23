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

    private final Facing facing;
    private final long seed;
    private final long braceQuanta;
    private final long preparationQuanta;
    private final long flightQuanta;
    private final OutcomeTable weights;

    public SpearClashConfig(Facing facing, long seed) {
        this(facing, seed, 2, 2, 2, 50, 30, 20);
    }

    public SpearClashConfig(Facing facing, long seed, long braceQuanta, long preparationQuanta,
                            long flightQuanta, double repelWeight, double passWeight, double closeWeight) {
        if (facing == null) throw new IllegalArgumentException("Facing is required");
        this.facing = facing;
        this.seed = seed;
        this.braceQuanta = wholeTicks(braceQuanta);
        this.preparationQuanta = wholeTicks(preparationQuanta);
        this.flightQuanta = wholeTicks(flightQuanta);
        weights = OutcomeTable.of(Arrays.asList(new WeightedOutcome(REPELLED.name(), repelWeight),
            new WeightedOutcome(GRAZED_PAST.name(), passWeight),
            new WeightedOutcome(BROKE_THROUGH.name(), closeWeight)));
    }

    private static long wholeTicks(long duration) {
        if (duration <= 0 || duration % 2 != 0 || duration / 2 > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Duration must fit the whole-tick projection");
        }
        return duration;
    }
}
