package ru.darvell.game.hhg.domain.actor;

import org.junit.jupiter.api.Test;
import ru.darvell.game.hhg.domain.actor.CreaturePatrolState.Behavior;
import ru.darvell.game.hhg.domain.scene.WorldSceneState;
import ru.darvell.game.hhg.domain.tactical.TacticalEntryResult;
import ru.darvell.game.hhg.domain.tactical.TacticalExitResult;
import ru.darvell.game.hhg.domain.tactical.TacticalGrid;
import ru.darvell.game.hhg.domain.world.WorldGeometry;
import ru.darvell.game.hhg.domain.world.WorldPosition;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class CreatureBehaviorConfigTest {
    private static final double TOLERANCE = 1e-10;

    @Test
    void identicalObservationsGiveEachCreatureItsOwnMovementAndSearchDuration() {
        CreaturePatrolState first = creature(new CreatureBehaviorConfig(2, 4, 3, 2, 2, 3, .5));
        CreaturePatrolState second = creature(new CreatureBehaviorConfig(1, 6, 1, 1, .5, 4, .25));
        assertFalse(first.canDetect(position(4)));
        assertTrue(second.canDetect(position(4)));
        first.advancePatrol(.5);
        second.advancePatrol(.5);
        assertX(.5, first);
        assertEquals(.75, second.getPosition().getY(), TOLERANCE);

        WorldPosition seen = position(2.5);
        assertTrue(first.observeHunter(seen));
        assertTrue(second.observeHunter(seen));
        first.advanceExploration(.1, null, 0);
        second.advanceExploration(.1, null, 0);
        assertX(.9, first);
        assertDistance(.6, new WorldPosition(.5, .75), second.getPosition());

        for (CreaturePatrolState creature : Arrays.asList(first, second)) {
            creature.observeHunter(position(20));
        }
        WorldPosition firstStart = first.getPosition();
        WorldPosition secondStart = second.getPosition();
        first.advanceExploration(.2, null, 0);
        second.advanceExploration(.2, null, 0);
        assertDistance(.6, firstStart, first.getPosition());
        assertDistance(.2, secondStart, second.getPosition());
        assertEquals(1.8, first.getSearchRemaining(), TOLERANCE);
        assertEquals(.3, second.getSearchRemaining(), TOLERANCE);
        assertEquals(seen, first.getLastSeenHunterPosition());
        assertEquals(seen, second.getLastSeenHunterPosition());

        second.advanceExploration(.3, null, 0);
        second.finishExplorationStep();
        assertEquals(Behavior.RETURN, second.getBehavior());
        assertEquals(Behavior.SEARCH, first.getBehavior());
        assertEquals(1.8, first.getSearchRemaining(), TOLERANCE);
        WorldPosition beforeReturn = second.getPosition();
        second.advanceExploration(.1, null, 0);
        assertDistance(.1, beforeReturn, second.getPosition());
    }

    @Test
    void copiesAndTacticalRoundTripKeepConfigAndActiveSearch() {
        CreatureBehaviorConfig config = new CreatureBehaviorConfig(1, 6, 1, 3, .5, 4, .25);
        CreaturePatrolState actor = creature(config);
        assertSame(config, actor.copy().getBehaviorConfig());
        assertSame(config, actor.withDamage(1).getBehaviorConfig());
        assertSame(config, actor.withTacticalPosition(position(.8)).getBehaviorConfig());
        actor.observeHunter(position(2.5));
        WorldSceneState scene = new WorldSceneState(new HunterState(position(8.5), 5, .4), actor.withDamage(1),
            new TacticalGrid(0, 0, 1), WorldGeometry.empty());
        scene.update(0, 0, .1, false);
        assertEquals(.4, scene.getCreatureSearchRemaining(), TOLERANCE);
        assertEquals(TacticalEntryResult.Outcome.SUCCESS, scene.enterTactical());
        scene.update(0, 0, 1, false);
        assertEquals(.4, scene.getCreatureSearchRemaining(), TOLERANCE);
        assertEquals(TacticalExitResult.SUCCESS, scene.tryExitTactical());
        double start = scene.getCreaturePosition().getX();
        scene.update(0, 0, .1, false);
        assertEquals(start + .1, scene.getCreaturePosition().getX(), TOLERANCE);
        assertEquals(.3, scene.getCreatureSearchRemaining(), TOLERANCE);
        assertEquals(1, scene.getCreatureHealth());
    }

    @Test
    void zeroDurationSkipsSearchAndUsesReturnSpeed() {
        CreaturePatrolState actor = creature(new CreatureBehaviorConfig(1, 4, 3, 1, 0, 3, .5));
        actor.observeHunter(position(2.5));
        actor.advanceExploration(.1, null, 0);
        actor.observeHunter(position(8));
        assertEquals(Behavior.RETURN, actor.getBehavior());
        assertEquals(0, actor.getSearchRemaining());
        actor.advanceExploration(.1, null, 0);
        assertX(.8, actor);
        assertEquals(position(2.5), actor.getLastSeenHunterPosition());
    }

    @Test
    void rejectsNonFiniteAndOutOfRangeParameters() {
        for (int index = 0; index < 7; index++) {
            for (double bad : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1, 0}) {
                if (index == 4 && bad == 0) continue;
                double[] values = {2, 4, 3, 2, 2, 3, .5};
                values[index] = bad;
                assertThrows(IllegalArgumentException.class, () -> new CreatureBehaviorConfig(
                    values[0], values[1], values[2], values[3], values[4], values[5], values[6]));
            }
        }
        assertThrows(IllegalArgumentException.class, () -> creature(null));
    }

    private static CreaturePatrolState creature(CreatureBehaviorConfig config) {
        return new CreaturePatrolState(Arrays.asList(position(.5), new WorldPosition(.5, 1.5)),
            config, .3, WorldGeometry.empty());
    }

    private static WorldPosition position(double x) { return new WorldPosition(x, .5); }

    private static void assertX(double expected, CreaturePatrolState actor) {
        assertEquals(expected, actor.getPosition().getX(), TOLERANCE);
    }

    private static void assertDistance(double expected, WorldPosition from, WorldPosition to) {
        assertEquals(expected, Math.hypot(from.getX() - to.getX(), from.getY() - to.getY()), TOLERANCE);
    }
}
