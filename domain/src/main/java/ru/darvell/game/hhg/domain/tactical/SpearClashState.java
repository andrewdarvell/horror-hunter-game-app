package ru.darvell.game.hhg.domain.tactical;

import lombok.Value;
import ru.darvell.game.hhg.domain.world.WorldPosition;

/** Read-only projection of the spear encounter, in tactical quanta. */
@Value
public class SpearClashState {
    public enum WolfPhase { READY, STEPPING, PREPARING, IN_FLIGHT, RECOVERING }
    public enum StopReason { HUNTER_READY, LEAP_RESOLVED, SIDE_THREAT, NO_ROUTE, GUARD_LIMIT, TERMINAL }
    public enum Result { REPELLED, GRAZED_PAST, BROKE_THROUGH, HIT, MISS, BLOCKED, STOPPED }

    Facing facing;
    boolean braced;
    long hunterRecovery;
    WolfPhase wolfPhase;
    long wolfRemaining;
    WorldPosition jumpTarget;
    WorldPosition stepTarget;
    boolean flankUsed;
    boolean noRoute;
    long randomCursor;
}
