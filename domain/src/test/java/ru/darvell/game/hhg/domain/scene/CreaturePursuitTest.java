package ru.darvell.game.hhg.domain.scene;

import org.junit.jupiter.api.Test;
import ru.darvell.game.hhg.domain.actor.CreatureBehaviorConfig;
import ru.darvell.game.hhg.domain.actor.CreaturePatrolState;
import ru.darvell.game.hhg.domain.actor.CreaturePatrolState.Behavior;
import ru.darvell.game.hhg.domain.actor.HunterState;
import ru.darvell.game.hhg.domain.tactical.CellCoordinates;
import ru.darvell.game.hhg.domain.tactical.PrototypeBiteState.Phase;
import ru.darvell.game.hhg.domain.tactical.TacticalAttackResult;
import ru.darvell.game.hhg.domain.tactical.TacticalEntryResult.Outcome;
import ru.darvell.game.hhg.domain.tactical.TacticalExitResult;
import ru.darvell.game.hhg.domain.tactical.TacticalGrid;
import ru.darvell.game.hhg.domain.world.StaticAabb;
import ru.darvell.game.hhg.domain.world.WorldGeometry;
import ru.darvell.game.hhg.domain.world.WorldPosition;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class CreaturePursuitTest {
    private static final double EPSILON = 1e-10;
    private static final WorldGeometry EMPTY = WorldGeometry.empty();


    @Test
    void visibleChaseStartsBeforeEngagementAndCreatureMovementCreatesEntry() {
        WorldSceneState scene = scene(p(2.825, .5), p(.5, .5), p(.5, 1.5), EMPTY);
        assertNull(scene.update(0, 0, 0, false));
        assertEquals(Behavior.CHASE, scene.getCreatureBehavior());
        assertEquals(p(.5, .5), scene.getCreaturePosition());
        assertNull(scene.update(0, 0, .20, false));
        assertEquals(1.30, scene.getCreaturePosition().getX(), EPSILON);
        assertEquals(SceneMode.EXPLORATION, scene.getMode());
        assertEquals(Outcome.SUCCESS, scene.update(0, 0, .01, false));
        assertEquals(1.34, scene.getCreatureBeforeEntry().getX(), EPSILON);
        assertEquals(p(2.825, .5), scene.getLastSeenHunterPosition());
    }

    @Test
    void hunterMovementCreatesEntryBeforeCreatureMoves() {
        WorldSceneState scene = scene(p(2.025, .5), p(.5, .5), p(.5, 1.5), EMPTY);
        assertEquals(Outcome.SUCCESS, scene.update(-1, 0, .01, false));
        assertEquals(p(.5, .5), scene.getCreatureBeforeEntry());
        assertEquals(1.975, scene.getHunterBeforeEntry().getX(), EPSILON);
        assertEquals(scene.getHunterBeforeEntry(), scene.getLastSeenHunterPosition());
    }

    @Test
    void runningOrCoverCanBreakSightBeforeEngagement() {
        WorldSceneState running = scene(p(2.825, .5), p(.5, .5), p(.5, 1.5), EMPTY);
        assertNull(running.update(1, 0, .63, false));
        assertEquals(Behavior.CHASE, running.getCreatureBehavior());
        WorldPosition before = running.getCreaturePosition();
        assertNull(running.update(1, 0, .01, false));
        // Ход H разрывает LOS по дальности, C делает ход SEARCH и снова видит H.
        assertEquals(.03, distance(before, running.getCreaturePosition()), EPSILON);
        assertEquals(Behavior.CHASE, running.getCreatureBehavior());
        assertNull(running.update(1, 0, .01, false));
        WorldPosition lastSeen = running.getLastSeenHunterPosition();
        assertNull(running.update(1, 0, .01, false));
        assertEquals(Behavior.SEARCH, running.getCreatureBehavior());
        assertEquals(lastSeen, running.getLastSeenHunterPosition());

        WorldGeometry wall = new WorldGeometry(Collections.singletonList(new StaticAabb(2, -1, 1, 2)));
        CreaturePatrolState searcher = creature(p(1, 0), p(1, 2), wall);
        assertTrue(searcher.observeHunter(p(1, 2)));
        WorldSceneState hidden = new WorldSceneState(new HunterState(p(4, 0), 5, .4), searcher,
            new TacticalGrid(0, 0, 1), wall);
        assertNull(hidden.update(0, 0, .67, false));
        assertEquals(p(1, 2), hidden.getCreaturePosition());
        assertEquals(Behavior.SEARCH, hidden.getCreatureBehavior());
        assertNull(hidden.update(0, 0, 1.33, false));
        assertEquals(Behavior.RETURN, hidden.getCreatureBehavior());
        assertEquals(p(1, 2), hidden.getLastSeenHunterPosition());
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
    void alreadyVisibleHunterAfterWoundedEscapeReengagesOnStep38() {
        WorldSceneState scene = visibleEscape();
        TacticalAttackResult breakout = scene.getLastResolution();
        Object bite = scene.getBiteState();
        assertNull(scene.update(0, 0, 0, false));
        assertEquals(p(1.5, .5), scene.getLastSeenHunterPosition());
        assertNull(scene.update(0, 0, .01, false));
        assertEquals(Behavior.CHASE, scene.getCreatureBehavior());
        assertEquals(p(3.5, .5), scene.getLastSeenHunterPosition());
        assertNull(scene.update(0, 0, .36, false));
        assertEquals(1.52, distance(scene.getHunterPosition(), scene.getCreaturePosition()), EPSILON);
        assertEquals(Outcome.SUCCESS, scene.update(0, 0, .01, false));
        assertEquals(1.48, distance(scene.getHunterBeforeEntry(), scene.getCreatureBeforeEntry()), EPSILON);
        assertEquals(SceneMode.TACTICAL, scene.getMode());
        assertSame(breakout, scene.getLastResolution());
        assertSame(bite, scene.getBiteState());
        assertEquals(2, scene.getTacticalTimeUnits());
        assertEquals(3, scene.getHunterHealth());
        assertEquals(2, scene.getCreatureHealth());
    }

    @Test
    void runningFirstAfterVisibleEscapeKeepsOldMemoryAndStartsSearch() {
        WorldSceneState scene = visibleEscape();
        assertNull(scene.update(1, 0, .01, false));
        assertEquals(3.55, scene.getHunterPosition().getX(), EPSILON);
        assertEquals(Behavior.SEARCH, scene.getCreatureBehavior());
        assertEquals(p(1.5, .5), scene.getLastSeenHunterPosition());
        assertEquals(1.99, scene.getCreatureSearchRemaining(), EPSILON);
    }


    @Test
    void sightCanBeBlockedAtCloseRangeAndChaseCanStopAtAFreeRay() {
        WorldGeometry touchingRay = new WorldGeometry(Collections.singletonList(new StaticAabb(.85, .5, .1, .25)));
        WorldSceneState blockedSight = scene(p(1.5, .5), p(.5, .5), p(.5, 1.5), touchingRay);
        assertNull(blockedSight.update(0, 0, 0, false));
        assertFalse(blockedSight.isCreatureAlerted());

        WorldGeometry corner = new WorldGeometry(Collections.singletonList(new StaticAabb(1.25, .65, .5, .85)));
        WorldSceneState visible = scene(p(2.825, .5), p(.5, .5), p(.5, 1.5), corner);
        assertNull(visible.update(0, 0, 1, false));
        assertEquals(.9901923789, visible.getCreaturePosition().getX(), EPSILON);
        assertTrue(corner.hasLineOfSight(visible.getCreaturePosition(), visible.getHunterPosition()));
        assertEquals(Behavior.CHASE, visible.getCreatureBehavior());
        assertEquals(SceneMode.EXPLORATION, visible.getMode());
    }


    @Test
    void failedPlacementWaitsForObservedBreakBetweenTheTwoMoves() {
        WorldGeometry corridor = new WorldGeometry(Arrays.asList(
            new StaticAabb(-20, -10, 40, 10), new StaticAabb(-20, 1.1, 40, 8.9)));
        WorldSceneState scene = new WorldSceneState(new HunterState(p(1.925, .5), 5, .4),
            creature(p(.5, .5), p(1, .5), corridor), new TacticalGrid(0, 0, 4), corridor);
        assertEquals(Outcome.NO_VALID_PLACEMENT, scene.update(0, 0, 0, true));
        assertEquals(p(1.925, .5), scene.getHunterPosition());
        assertEquals(p(.5, .5), scene.getCreaturePosition());
        assertNull(scene.update(0, 0, 0, false));
        for (int step = 0; step < 3; step++) assertNull(scene.update(0, 0, .01, false));
        assertNull(scene.update(1, 0, .15, false));
        assertEquals(1.455, distance(scene.getHunterPosition(), scene.getCreaturePosition()), EPSILON);
        assertEquals(Outcome.NO_VALID_PLACEMENT, scene.update(1, 0, .01, false));
        assertEquals(1.465, distance(scene.getHunterPosition(), scene.getCreaturePosition()), EPSILON);
        assertEquals(Behavior.CHASE, scene.getCreatureBehavior());
        assertNull(scene.update(0, 0, .01, false));
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
        assertNull(scene.update(-1, 0, .01, false));
        assertEquals(Behavior.CHASE, scene.getCreatureBehavior());
        assertEquals(0, scene.getCreatureSearchRemaining());
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

    @Test
    void engagementSnapshotIsIndependentOfFrameSplitting() {
        for (int updates : new int[] {1, 21, 42}) {
            WorldSceneState scene = scene(p(2.825, .5), p(.5, .5), p(.5, 1.5), EMPTY);
            for (int frame = 1; frame < updates; frame++) {
                assertNull(scene.update(0, 0, .21 / updates, false));
            }
            assertEquals(Outcome.SUCCESS, scene.update(0, 0, .21 / updates, false));
            assertEquals(1.34, scene.getCreatureBeforeEntry().getX(), EPSILON);
            assertEquals(p(2.825, .5), scene.getHunterBeforeEntry());
        }
    }

    @Test
    void sameObservationCanCrossBothThresholdsButAnUnsampledPassDoesNot() {
        CreaturePatrolState creature = creature(p(.5, .5), p(.5, 1.5), EMPTY);
        TacticalGrid grid = new TacticalGrid(0, 0, 1);
        WorldSceneState simultaneous = new WorldSceneState(new HunterState(p(3.6, .5), 200, .4), creature, grid, EMPTY);
        assertEquals(Outcome.SUCCESS, simultaneous.update(-1, 0, .01, false));
        assertEquals(1.6, simultaneous.getHunterBeforeEntry().getX(), EPSILON);
        assertEquals(simultaneous.getHunterBeforeEntry(), simultaneous.getLastSeenHunterPosition());
        assertEquals(Behavior.CHASE, simultaneous.getCreatureBehavior());
        assertEquals(p(.5, .5), simultaneous.getCreatureBeforeEntry());

        WorldSceneState unsampled = new WorldSceneState(new HunterState(p(-4, 1.5), 900, .4), creature, grid, EMPTY);
        assertNull(unsampled.update(1, 0, .01, false));
        assertEquals(p(5, 1.5), unsampled.getHunterPosition());
        assertFalse(unsampled.isCreatureAlerted());
    }

    private static WorldSceneState visibleEscape() {
        WorldSceneState scene = scene(p(1.5, .5), p(.5, .5), p(.5, 1.5), EMPTY);
        assertEquals(Outcome.SUCCESS, scene.update(0, 0, 0, false));
        TacticalAttackResult result = scene.tryBreakoutTo(new CellCoordinates(3, 0));
        assertEquals(TacticalAttackResult.Outcome.ESCAPED, result.getOutcome());
        assertEquals(2, result.getDuration());
        assertEquals(Phase.RECOVERING, scene.getBiteState().getPhase());
        assertEquals(2, scene.getBiteState().getRemainingUnits());
        return scene;
    }

    private static WorldSceneState escaped(WorldGeometry geometry) {
        WorldSceneState scene = scene(p(2.5, .5), p(.5, .5), p(.5, 1.5), geometry);
        assertNull(scene.update(0, 0, 0, false));
        assertEquals(Outcome.SUCCESS, scene.enterTactical());
        assertEquals(TacticalAttackResult.Outcome.ESCAPED, scene.tryBreakoutTo(new CellCoordinates(4, 0)).getOutcome());
        return scene;
    }

    private static WorldSceneState scene(WorldPosition hunter, WorldPosition creature, WorldPosition endpoint,
                                         WorldGeometry geometry) {
        return new WorldSceneState(new HunterState(hunter, 5, .4), creature(creature, endpoint, geometry),
            new TacticalGrid(0, 0, 1), geometry);
    }

    private static CreaturePatrolState creature(WorldPosition position, WorldPosition endpoint, WorldGeometry geometry) {
        return new CreaturePatrolState(Arrays.asList(position, endpoint), new CreatureBehaviorConfig(2, 4, 3, 2, 2, 3, 1.5, .5), .3, geometry);
    }

    private static WorldPosition p(double x, double y) { return new WorldPosition(x, y); }

    private static double distance(WorldPosition from, WorldPosition to) {
        return Math.hypot(from.getX() - to.getX(), from.getY() - to.getY());
    }
}
