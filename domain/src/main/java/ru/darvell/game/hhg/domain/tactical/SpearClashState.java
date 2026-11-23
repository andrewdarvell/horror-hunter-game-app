package ru.darvell.game.hhg.domain.tactical;

import lombok.Value;
import ru.darvell.game.hhg.domain.world.WorldPosition;

/** Read-only projection of the scripted encounter, in tactical quanta. */
@Value
public class SpearClashState {
    public enum WolfPhase { UNUSED, PREPARING, IN_FLIGHT, RECOVERING, SPENT }
    public enum Result { REPELLED, GRAZED_PAST, BROKE_THROUGH, HIT, MISS, BLOCKED, STOPPED }

    Facing facing;
    boolean braced;
    long hunterRecovery;
    WolfPhase wolfPhase;
    long wolfRemaining;
    WorldPosition jumpTarget;
    long randomCursor;
}
