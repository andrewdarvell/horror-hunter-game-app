package ru.darvell.game.hhg.domain.world;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldSceneStateTest {
    private final TacticalGrid grid = new TacticalGrid(0, 0, 2);

    @Test
    void manualEntryFreezesBothActorsWithoutInventingDetection() {
        CreaturePatrolState creature = creature(new WorldPosition(5, 0), 0.1, WorldGeometry.empty());
        WorldSceneState scene = scene(new WorldPosition(0, 0), creature, WorldGeometry.empty());
        assertEquals(TacticalEntryResult.Outcome.SUCCESS, scene.update(0, 0, 0, true));
        assertFalse(scene.isCreatureAlerted());
        assertNull(scene.getLastSeenHunterPosition());
        assertFrozen(scene, creature);
    }

    @Test
    void automaticEntryUsesPostMovementPositionAndPreservesPreSnapLastSeen() {
        CreaturePatrolState creature = creature(new WorldPosition(3, 0), 10, WorldGeometry.empty());
        WorldSceneState scene = scene(new WorldPosition(0, 0), creature, WorldGeometry.empty());
        assertEquals(TacticalEntryResult.Outcome.SUCCESS, scene.update(1, 0, 0.2, true));
        assertTrue(scene.isCreatureAlerted());
        assertEquals(new WorldPosition(0.2, 0), scene.getLastSeenHunterPosition());
        assertNotEquals(scene.getHunterPosition(), scene.getLastSeenHunterPosition());
        assertEquals(0.5, creature.getWaitRemaining());
        assertFrozen(scene, creature);
    }

    @Test
    void detectsAndEntersAtZeroTimeBeforePatrol() {
        CreaturePatrolState creature = creature(new WorldPosition(3, 0), 10, WorldGeometry.empty());
        WorldSceneState scene = scene(new WorldPosition(0, 0), creature, WorldGeometry.empty());
        assertEquals(TacticalEntryResult.Outcome.SUCCESS, scene.update(0, 0, 0, false));
        assertEquals(0.5, creature.getWaitRemaining());
        assertEquals(new WorldPosition(0, 0), scene.getLastSeenHunterPosition());
    }

    @Test
    void failedAttemptNeverPartiallyMovesActorsOrChangesPatrolMetadata() {
        for (boolean singleCell : new boolean[] {true, false}) {
            WorldGeometry geometry = new WorldGeometry(singleCell
                ? TacticalEntryPlannerTest.blockedCentersExcept(new CellCoordinates(0, 0))
                : TacticalEntryPlannerTest.blockedCentersExcept());
            WorldPosition p = new WorldPosition(0.2, 0.2);
            CreaturePatrolState creature = creature(p, 10, geometry);
            WorldSceneState scene = scene(singleCell ? p : new WorldPosition(9, 1), creature, geometry);
            Object[] before = snapshot(scene, creature);
            assertEquals(TacticalEntryResult.Outcome.NO_VALID_PLACEMENT, scene.enterTactical());
            assertArrayEquals(before, snapshot(scene, creature));
        }
    }

    @Test
    void failedDetectionIsNotRetriedAutomaticallyButManualRequestCanSucceedLater() {
        WorldGeometry geometry = new WorldGeometry(TacticalEntryPlannerTest.blockedCentersExcept(new CellCoordinates(0, 0)));
        CreaturePatrolState creature = creature(new WorldPosition(0.2, 0.2), 10, geometry);
        WorldSceneState scene = scene(new WorldPosition(0.2, 0.2), creature, geometry);
        assertEquals(TacticalEntryResult.Outcome.NO_VALID_PLACEMENT, scene.update(1, 0, 0.1, false));
        assertEquals(new WorldPosition(0.3, 0.2).getX(), scene.getHunterPosition().getX(), 1e-12);
        assertEquals(scene.getHunterPosition(), scene.getLastSeenHunterPosition());
        assertTrue(scene.isCreatureAlerted());
        assertEquals(0.5, creature.getWaitRemaining());
        assertNull(scene.update(1, 0, 4.7, false));
        assertEquals(SceneMode.EXPLORATION, scene.getMode());
        assertEquals(TacticalEntryResult.Outcome.SUCCESS, scene.update(0, 0, 0, true));
        assertFrozen(scene, creature);
    }

    @Test
    void failedManualEntryContinuesUnalertedPatrolAndNormalExploration() {
        WorldGeometry geometry = new WorldGeometry(TacticalEntryPlannerTest.blockedCentersExcept());
        CreaturePatrolState creature = creature(new WorldPosition(0.2, 0.2), 0.1, geometry);
        WorldSceneState scene = scene(new WorldPosition(9, 1), creature, geometry);
        assertEquals(TacticalEntryResult.Outcome.NO_VALID_PLACEMENT, scene.update(0, 0, 0.1, true));
        assertFalse(scene.isCreatureAlerted());
        assertEquals(0.4, creature.getWaitRemaining(), 1e-12);
        assertNull(scene.update(0, 0, 0.45, false));
        assertEquals(0.25, scene.getCreaturePosition().getX(), 1e-12);
    }

    @Test
    void invalidUpdateAndInvalidPlacementPreserveWholeSnapshot() {
        CreaturePatrolState creature = creature(new WorldPosition(3, 0), 1, WorldGeometry.empty());
        WorldSceneState scene = scene(new WorldPosition(0, 0), creature, WorldGeometry.empty());
        Object[] before = snapshot(scene, creature);
        for (double bad : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> scene.update(0, 0, bad, true));
            assertArrayEquals(before, snapshot(scene, creature));
        }
        assertThrows(IllegalArgumentException.class, () -> scene.update(Double.NaN, 0, 1, true));
        assertArrayEquals(before, snapshot(scene, creature));
        WorldSceneState invalid = scene(new WorldPosition(Double.MAX_VALUE, 0), creature, WorldGeometry.empty());
        Object[] invalidBefore = snapshot(invalid, creature);
        assertEquals(TacticalEntryResult.Outcome.INVALID_INPUT, invalid.enterTactical());
        assertArrayEquals(invalidBefore, snapshot(invalid, creature));
    }

    private void assertFrozen(WorldSceneState scene, CreaturePatrolState creature) {
        assertEquals(SceneMode.TACTICAL, scene.getMode());
        assertNotEquals(scene.getPlacement().getHunterCell(), scene.getPlacement().getCreatureCell());
        assertEquals(grid.cellToWorldCenter(scene.getPlacement().getHunterCell()), scene.getHunterPosition());
        assertEquals(grid.cellToWorldCenter(scene.getPlacement().getCreatureCell()), scene.getCreaturePosition());
        Object[] before = snapshot(scene, creature);
        assertNull(scene.update(1, -1, 10000, false));
        assertEquals(TacticalEntryResult.Outcome.ALREADY_TACTICAL, scene.update(0, 0, 0, true));
        assertArrayEquals(before, snapshot(scene, creature));
    }

    private Object[] snapshot(WorldSceneState scene, CreaturePatrolState creature) {
        return new Object[] {scene.getMode(), scene.getPlacement(), scene.getHunterPosition(),
            scene.getCreaturePosition(), scene.isCreatureAlerted(), scene.getLastSeenHunterPosition(),
            creature.getTargetIndex(), creature.getDirection(), creature.getWaitRemaining()};
    }

    private WorldSceneState scene(WorldPosition hunter, CreaturePatrolState creature, WorldGeometry geometry) {
        return new WorldSceneState(new HunterState(hunter, 1, 0.05), creature, grid, geometry);
    }

    private CreaturePatrolState creature(WorldPosition from, double detection, WorldGeometry geometry) {
        return new CreaturePatrolState(Arrays.asList(from, from.translate(0.1, 0)), 1, 0.05, 0.5, detection, geometry);
    }
}
