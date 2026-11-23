package ru.darvell.game.hhg.domain.scene;

import lombok.Value;
import ru.darvell.game.hhg.domain.tactical.SpearClashState.WolfPhase;
import ru.darvell.game.hhg.domain.world.WorldPosition;

/** One scheduled action (or recovery) of the wolf in the spear encounter. */
@Value
class WolfTurn {
    WolfPhase phase;
    long deadline;
    SpearClash.Plan leap;
    long contact;
    Step step;
    boolean flankUsed;
    boolean noRoute;

    @Value
    static class Step {
        WorldPosition from;
        WorldPosition to;
        long start;
        boolean flank;
    }

    static WolfTurn ready(boolean flankUsed, boolean noRoute) {
        return new WolfTurn(WolfPhase.READY, 0, null, 0, null, flankUsed, noRoute);
    }

    static WolfTurn preparing(SpearClash.Plan plan, long launch, long contact, boolean flankUsed) {
        return new WolfTurn(WolfPhase.PREPARING, launch, plan, contact, null, flankUsed, false);
    }

    static WolfTurn recovering(long until, boolean flankUsed) {
        return new WolfTurn(WolfPhase.RECOVERING, until, null, 0, null, flankUsed, false);
    }

    static WolfTurn stepping(Step step, long deadline, boolean flankUsed) {
        return new WolfTurn(WolfPhase.STEPPING, deadline, null, 0, step, flankUsed || step.flank, false);
    }

    WolfTurn launch() {
        return new WolfTurn(WolfPhase.IN_FLIGHT, contact, leap, contact, null, flankUsed, false);
    }

    WolfTurn afterExit() {
        return phase == WolfPhase.RECOVERING ? this : ready(flankUsed, false);
    }
}
