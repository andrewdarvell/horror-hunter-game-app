package ru.darvell.game.hhg.domain.scene;

import org.junit.jupiter.api.Test;
import ru.darvell.game.hhg.domain.actor.CreaturePatrolState;
import ru.darvell.game.hhg.domain.actor.CreaturePatrolState.Behavior;
import ru.darvell.game.hhg.domain.actor.HunterState;
import ru.darvell.game.hhg.domain.tactical.CellCoordinates;
import ru.darvell.game.hhg.domain.tactical.TacticalAttackResult;
import ru.darvell.game.hhg.domain.tactical.TacticalEntryResult.Outcome;
import ru.darvell.game.hhg.domain.tactical.TacticalExitResult;
import ru.darvell.game.hhg.domain.tactical.TacticalGrid;
import ru.darvell.game.hhg.domain.world.StaticAabb;
import ru.darvell.game.hhg.domain.world.WorldGeometry;
import ru.darvell.game.hhg.domain.world.WorldPosition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CreaturePursuitTest {
    private static final double EPSILON = 1e-10;
    private static final WorldGeometry EMPTY = WorldGeometry.empty();

    @Test
    void patrolCreatesContactWithStationaryHunterAndStopsAtEntry() {
        WorldSceneState scene = scene(p(0, 4), p(0, 0), p(0, 2), EMPTY);
        assertNull(scene.update(0, 0, .99, false));
        assertEquals(Behavior.PATROL, scene.getCreatureBehavior());
        assertEquals(.98, scene.getCreaturePosition().getY(), EPSILON);
        assertEquals(Outcome.SUCCESS, scene.update(0, 0, 5, false));
        assertEquals(1.0, scene.getCreatureBeforeEntry().getY(), EPSILON);
        assertEquals(p(0, 4), scene.getLastSeenHunterPosition());
        assertEquals(Behavior.CHASE, scene.getCreatureBehavior());
    }

    @Test
    void successfulBreakoutLetsHunterEscapeAndPatrolResumesAfterSearch() {
        WorldSceneState scene = escaped(EMPTY);
        assertNull(scene.update(0, 0, 0, false));
        assertEquals(Behavior.CHASE, scene.getCreatureBehavior());
        TacticalAttackResult breakout = scene.getLastResolution();
        Object bite = scene.getBiteState();
        assertNull(scene.update(1, 0, 2, false));
        assertEquals(SceneMode.EXPLORATION, scene.getMode());
        assertEquals(14.5, scene.getHunterPosition().getX(), EPSILON);
        assertEquals(p(2.5, .5), scene.getCreaturePosition());
        assertEquals(p(2.5, .5), scene.getLastSeenHunterPosition());
        assertEquals(Behavior.RETURN, scene.getCreatureBehavior());
        assertNull(scene.update(0, 0, 1, false));
        assertEquals(p(.5, .5), scene.getCreaturePosition());
        assertEquals(Behavior.PATROL, scene.getCreatureBehavior());
        assertTrue(scene.isCreatureAlerted());
        assertNull(scene.update(0, 0, .6, false));
        assertEquals(.7, scene.getCreaturePosition().getY(), EPSILON);
        assertSame(breakout, scene.getLastResolution());
        assertSame(bite, scene.getBiteState());
        assertEquals(2, scene.getTacticalTimeUnits());
        assertEquals(4, scene.getHunterHealth());
    }

    @Test
    void stationaryHunterAfterBreakoutIsDetectedOnStep34() {
        WorldSceneState scene = escaped(EMPTY);
        TacticalAttackResult breakout = scene.getLastResolution();
        Object bite = scene.getBiteState();
        assertNull(scene.update(0, 0, .33, false));
        assertEquals(Behavior.SEARCH, scene.getCreatureBehavior());
        assertEquals(1.49, scene.getCreaturePosition().getX(), EPSILON);
        assertEquals(Outcome.SUCCESS, scene.update(0, 0, .01, false));
        assertEquals(1.52, scene.getCreatureBeforeEntry().getX(), EPSILON);
        assertEquals(p(4.5, .5), scene.getLastSeenHunterPosition());
        assertEquals(SceneMode.TACTICAL, scene.getMode());
        assertEquals(Behavior.CHASE, scene.getCreatureBehavior());
        assertSame(breakout, scene.getLastResolution());
        assertSame(bite, scene.getBiteState());
        assertEquals(2, scene.getTacticalTimeUnits());
    }

    @Test
    void deniedPlacementAfterBreakoutStartsChaseWithoutRetryOrWallPenetration() {
        WorldGeometry geometry = new WorldGeometry(Arrays.asList(
            new StaticAabb(4, 1.2, .55, 1.8), new StaticAabb(5.45, 1.2, 1.55, 1.8)));
        WorldSceneState scene = escaped(geometry);
        assertNull(scene.update(1, 0, .10, false));
        assertNull(scene.update(0, 1, .16, false));
        assertNull(scene.update(0, 0, .27, false));
        assertEquals(Outcome.NO_VALID_PLACEMENT, scene.update(0, 0, .01, false));
        assertEquals(2.12, scene.getCreaturePosition().getX(), EPSILON);
        assertEquals(Behavior.CHASE, scene.getCreatureBehavior());
        assertEquals(SceneMode.EXPLORATION, scene.getMode());
        WorldPosition start = scene.getCreaturePosition();
        assertNull(scene.update(0, 0, .01, false));
        WorldPosition next = scene.getCreaturePosition();
        assertEquals(.04, distance(start, next), EPSILON);
        for (int step = 0; step < 80; step++) {
            WorldPosition before = scene.getCreaturePosition();
            assertNull(scene.update(0, 0, .01, false));
            assertTrue(geometry.isCirclePathValid(before, scene.getCreaturePosition(), .3));
        }
        WorldPosition blocked = scene.getCreaturePosition();
        assertNull(scene.update(0, 0, 1, false));
        assertEquals(blocked, scene.getCreaturePosition());
        assertTrue(geometry.hasLineOfSight(blocked, scene.getHunterPosition()));
        assertEquals(Behavior.CHASE, scene.getCreatureBehavior());
    }

    @Test
    void longUpdateCanRecordNewContactAfterObservedBreak() {
        List<StaticAabb> obstacles = new ArrayList<>();
        obstacles.add(new StaticAabb(.95, .13, .1, .07));
        for (int x = 1; x <= 3; x++) {
            for (int y = -2; y <= 1; y++) {
                obstacles.add(new StaticAabb(x + .45, y + .45, .1, .1));
            }
        }
        WorldSceneState scene = scene(p(2, -.05), p(0, 0), p(0, -1), new WorldGeometry(obstacles));
        assertEquals(Outcome.NO_VALID_PLACEMENT, scene.update(0, 0, 0, false));
        assertEquals(Outcome.NO_VALID_PLACEMENT, scene.update(0, 1, .16, false));
        assertEquals(.75, scene.getHunterPosition().getY(), EPSILON);
        assertTrue(scene.getLastSeenHunterPosition().getY() > .2);
        assertEquals(SceneMode.EXPLORATION, scene.getMode());
        assertNull(scene.update(0, 0, 0, false));
    }

    @Test
    void manualTacticalFreezesSearchAndResumeUsesOnlyLastSeen() {
        WorldSceneState scene = escaped(EMPTY);
        scene.update(1, 0, .1, false);
        assertEquals(Behavior.SEARCH, scene.getCreatureBehavior());
        double remaining = scene.getCreatureSearchRemaining();
        WorldPosition memory = scene.getLastSeenHunterPosition();
        assertEquals(Outcome.SUCCESS, scene.update(0, 0, 0, true));
        assertNull(scene.update(-1, 0, 50, false));
        assertEquals(Behavior.SEARCH, scene.getCreatureBehavior());
        assertEquals(remaining, scene.getCreatureSearchRemaining());
        assertEquals(memory, scene.getLastSeenHunterPosition());
        assertEquals(TacticalExitResult.SUCCESS, scene.tryExitTactical());
        assertNull(scene.update(0, 0, 0, false));
        scene.update(1, 0, .1, false);
        assertEquals(remaining - .1, scene.getCreatureSearchRemaining(), EPSILON);
        assertEquals(memory, scene.getLastSeenHunterPosition());
    }

    @Test
    void contactOnLastSearchStepTakesPriorityOverReturn() {
        WorldSceneState scene = escaped(EMPTY);
        assertNull(scene.update(1, 0, .20, false));
        assertNull(scene.update(.4, 0, .01, false));
        assertNull(scene.update(0, 0, 1.78, false));
        assertEquals(Behavior.SEARCH, scene.getCreatureBehavior());
        assertEquals(.01, scene.getCreatureSearchRemaining(), EPSILON);
        assertEquals(Outcome.SUCCESS, scene.update(-1, 0, .01, false));
        assertEquals(Behavior.CHASE, scene.getCreatureBehavior());
    }

    @Test
    void fixedStepPreservesLongTimeAndLatchesOnlyTheUnfinishedStepsDirection() {
        WorldSceneState single = scene(p(20, 0), p(0, 0), p(0, 2), EMPTY);
        WorldSceneState split = scene(p(20, 0), p(0, 0), p(0, 2), EMPTY);
        single.update(1, 0, 1.27, false);
        for (int i = 0; i < 25; i++) {
            split.update(1, 0, .013, false);
            split.update(1, 0, .037, false);
        }
        split.update(1, 0, .02, false);
        assertEquals(single.getHunterPosition(), split.getHunterPosition());
        assertEquals(single.getCreaturePosition(), split.getCreaturePosition());
        assertEquals(26.35, single.getHunterPosition().getX(), EPSILON);

        single.update(1, 0, .015, false);
        single.update(0, -1, 0, false);
        single.update(0, 1, .015, false);
        assertEquals(26.45, single.getHunterPosition().getX(), EPSILON);
        assertEquals(.05, single.getHunterPosition().getY(), EPSILON);

        WorldSceneState remainder = scene(p(20, 0), p(0, 0), p(0, 2), EMPTY);
        remainder.update(1, 0, 1.275, false);
        assertEquals(26.35, remainder.getHunterPosition().getX(), EPSILON);
        remainder.update(0, 1, .005, false);
        assertEquals(26.40, remainder.getHunterPosition().getX(), EPSILON);
        assertEquals(0, remainder.getHunterPosition().getY(), EPSILON);
    }

    @Test
    void contactBetweenChecksCanBeMissedButThinWallCannotBeCrossed() {
        WorldSceneState brief = scene(p(-.025, 2.99995), p(0, 0), p(0, -1), EMPTY);
        assertNull(brief.update(1, 0, .01, false));
        assertEquals(p(.025, 2.99995), brief.getHunterPosition());
        assertFalse(brief.isCreatureAlerted());

        WorldGeometry wall = new WorldGeometry(Collections.singletonList(new StaticAabb(0, -1, .001, 2)));
        WorldSceneState thin = new WorldSceneState(new HunterState(p(-.41, 0), 100, .4),
            creature(p(5, 0), p(5, 1), wall), new TacticalGrid(0, 0, 1), wall);
        assertNull(thin.update(1, 0, .01, false));
        assertEquals(p(-.41, 0), thin.getHunterPosition());
    }

    private static WorldSceneState escaped(WorldGeometry geometry) {
        WorldSceneState scene = scene(p(2.5, .5), p(.5, .5), p(.5, 1.5), geometry);
        assertEquals(Outcome.SUCCESS, scene.update(0, 0, 0, false));
        assertEquals(TacticalAttackResult.Outcome.ESCAPED, scene.tryBreakoutTo(new CellCoordinates(4, 0)).getOutcome());
        return scene;
    }

    private static WorldSceneState scene(WorldPosition hunter, WorldPosition creature, WorldPosition endpoint,
                                         WorldGeometry geometry) {
        return new WorldSceneState(new HunterState(hunter, 5, .4), creature(creature, endpoint, geometry),
            new TacticalGrid(0, 0, 1), geometry);
    }

    private static CreaturePatrolState creature(WorldPosition position, WorldPosition endpoint, WorldGeometry geometry) {
        return new CreaturePatrolState(Arrays.asList(position, endpoint), 2, .3, .5, 3, geometry);
    }

    private static WorldPosition p(double x, double y) { return new WorldPosition(x, y); }

    private static double distance(WorldPosition from, WorldPosition to) {
        return Math.hypot(from.getX() - to.getX(), from.getY() - to.getY());
    }
}
