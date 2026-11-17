package ru.darvell.game.hhg.domain.actor;

import org.junit.jupiter.api.Test;
import ru.darvell.game.hhg.domain.actor.CreaturePatrolState.Behavior;
import ru.darvell.game.hhg.domain.world.StaticAabb;
import ru.darvell.game.hhg.domain.world.WorldGeometry;
import ru.darvell.game.hhg.domain.world.WorldPosition;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class CreatureReturnTest {
    @Test
    void snapReturnsToOriginalPositionBeforeSpendingPatrolPause() {
        CreaturePatrolState creature = creature(WorldGeometry.empty());
        creature.advancePatrol(.3);
        CreaturePatrolState snapped = creature.withTacticalPosition(p(.5, 0));
        snapped.advanceExploration(.25, null, 0);
        assertEquals(Behavior.PATROL, snapped.getBehavior());
        assertEquals(p(0, 0), snapped.getPosition());
        assertEquals(.2, snapped.getWaitRemaining(), 1e-10);
        assertEquals(p(0, 0), creature.getPosition());
    }

    @Test
    void blockedAnchorAndOriginUseReachableWaypointAndNormalArrival() {
        WorldGeometry geometry = new WorldGeometry(Collections.singletonList(new StaticAabb(1, 1, 1, 1.1)));
        CreaturePatrolState creature = creature(geometry);
        creature.advancePatrol(1);
        CreaturePatrolState snapped = creature.withTacticalPosition(p(3, 2));
        snapped.advanceExploration(1, null, 0);
        assertEquals(Behavior.RETURN, snapped.getBehavior());
        assertFalse(snapped.isReturnBlocked());
        snapped.advanceExploration(1, null, 0);
        assertEquals(Behavior.PATROL, snapped.getBehavior());
        assertEquals(p(0, 4), snapped.getPosition());
        assertEquals(0, snapped.getTargetIndex());
        assertEquals(-1, snapped.getDirection());
        assertEquals(.5, snapped.getWaitRemaining());
    }

    @Test
    void inaccessibleRouteWaitsAndBodyCanBlockReturn() {
        WorldGeometry wall = new WorldGeometry(Collections.singletonList(new StaticAabb(1, -1, 1, 6)));
        CreaturePatrolState blocked = creature(wall).withTacticalPosition(p(3, 2));
        blocked.advanceExploration(2, null, 0);
        assertEquals(Behavior.RETURN, blocked.getBehavior());
        assertTrue(blocked.isReturnBlocked());
        assertEquals(p(3, 2), blocked.getPosition());
        assertEquals(.5, blocked.getWaitRemaining());

        CreaturePatrolState bodyBlocked = creature(WorldGeometry.empty()).withTacticalPosition(p(0, -2));
        bodyBlocked.advanceExploration(2, p(0, -1), .4);
        assertTrue(bodyBlocked.isReturnBlocked());
        assertEquals(p(0, -2), bodyBlocked.getPosition());
        bodyBlocked.advanceExploration(1, null, 0);
        assertEquals(Behavior.PATROL, bodyBlocked.getBehavior());
        assertFalse(bodyBlocked.isReturnBlocked());
    }

    @Test
    void searchStopsAtWallWithoutChangingLastSeenEvenWhileTimerRuns() {
        WorldGeometry wall = new WorldGeometry(Collections.singletonList(new StaticAabb(1, -1, .1, 6)));
        CreaturePatrolState creature = creature(wall);
        assertTrue(creature.observeHunter(p(0, 1)));
        CreaturePatrolState snapped = creature.withTacticalPosition(p(2, 1));
        assertFalse(snapped.observeHunter(p(0, 2)));
        snapped.advanceExploration(1, null, 0);
        assertEquals(1.4, snapped.getPosition().getX(), 1e-10);
        assertEquals(p(0, 1), snapped.getLastSeenHunterPosition());
        assertEquals(1, snapped.getSearchRemaining(), 1e-10);
        snapped.advanceExploration(1, null, 0);
        snapped.finishExplorationStep();
        assertEquals(Behavior.RETURN, snapped.getBehavior());
    }

    private static CreaturePatrolState creature(WorldGeometry geometry) {
        return new CreaturePatrolState(Arrays.asList(p(0, 0), p(0, 4)), 2, .3, .5, 3, geometry);
    }

    private static WorldPosition p(double x, double y) { return new WorldPosition(x, y); }
}
