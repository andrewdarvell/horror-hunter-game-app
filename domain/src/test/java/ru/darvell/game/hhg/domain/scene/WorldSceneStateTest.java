package ru.darvell.game.hhg.domain.scene;

import ru.darvell.game.hhg.domain.actor.CreaturePatrolState;
import ru.darvell.game.hhg.domain.actor.HunterState;
import ru.darvell.game.hhg.domain.tactical.CellCoordinates;
import ru.darvell.game.hhg.domain.tactical.TacticalEntryResult;
import ru.darvell.game.hhg.domain.tactical.TacticalGrid;
import ru.darvell.game.hhg.domain.world.StaticAabb;
import ru.darvell.game.hhg.domain.world.WorldGeometry;
import ru.darvell.game.hhg.domain.world.WorldPosition;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
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
        assertFrozen(scene);
    }

    @Test
    void automaticEntryUsesPostMovementPositionAndPreservesPreSnapLastSeen() {
        CreaturePatrolState creature = creature(new WorldPosition(3, 0), 10, WorldGeometry.empty());
        WorldSceneState scene = scene(new WorldPosition(0, 0), creature, WorldGeometry.empty());
        assertEquals(TacticalEntryResult.Outcome.SUCCESS, scene.update(1, 0, 0.2, true));
        assertTrue(scene.isCreatureAlerted());
        assertEquals(new WorldPosition(0.2, 0), scene.getLastSeenHunterPosition());
        assertNotEquals(scene.getHunterPosition(), scene.getLastSeenHunterPosition());
        assertFrozen(scene);
    }

    @Test
    void detectsAndEntersAtZeroTimeBeforePatrol() {
        CreaturePatrolState creature = creature(new WorldPosition(3, 0), 10, WorldGeometry.empty());
        WorldSceneState scene = scene(new WorldPosition(0, 0), creature, WorldGeometry.empty());
        assertEquals(TacticalEntryResult.Outcome.SUCCESS, scene.update(0, 0, 0, false));
        assertEquals(new WorldPosition(0, 0), scene.getLastSeenHunterPosition());
    }

    @Test
    void failedAttemptNeverPartiallyMovesActorsOrChangesPatrolMetadata() {
        for (boolean singleCell : new boolean[] {true, false}) {
            WorldGeometry geometry = new WorldGeometry(singleCell
                ? blockedCentersExcept(new CellCoordinates(0, 0))
                : blockedCentersExcept());
            WorldPosition p = new WorldPosition(0.2, 0.2);
            CreaturePatrolState creature = creature(p, 10, geometry);
            WorldSceneState scene = scene(singleCell ? p : new WorldPosition(9, 1), creature, geometry);
            Object[] before = snapshot(scene);
            assertEquals(TacticalEntryResult.Outcome.NO_VALID_PLACEMENT, scene.enterTactical());
            assertArrayEquals(before, snapshot(scene));
        }
    }

    @Test
    void failedDetectionIsNotRetriedAutomaticallyButManualRequestCanSucceedLater() {
        WorldGeometry geometry = new WorldGeometry(blockedCentersExcept(new CellCoordinates(0, 0)));
        CreaturePatrolState creature = creature(new WorldPosition(0.2, 0.2), 10, geometry);
        WorldSceneState scene = scene(new WorldPosition(0.2, 0.2), creature, geometry);
        assertEquals(TacticalEntryResult.Outcome.NO_VALID_PLACEMENT, scene.update(1, 0, 0.1, false));
        assertEquals(new WorldPosition(0.3, 0.2).getX(), scene.getHunterPosition().getX(), 1e-12);
        assertEquals(scene.getHunterPosition(), scene.getLastSeenHunterPosition());
        assertTrue(scene.isCreatureAlerted());
        assertNull(scene.update(1, 0, 4.7, false));
        assertEquals(SceneMode.EXPLORATION, scene.getMode());
        assertEquals(TacticalEntryResult.Outcome.SUCCESS, scene.update(0, 0, 0, true));
        assertFrozen(scene);
    }

    @Test
    void failedManualEntryContinuesUnalertedPatrolAndNormalExploration() {
        WorldGeometry geometry = new WorldGeometry(blockedCentersExcept());
        CreaturePatrolState creature = creature(new WorldPosition(0.2, 0.2), 0.1, geometry);
        WorldSceneState scene = scene(new WorldPosition(9, 1), creature, geometry);
        assertEquals(TacticalEntryResult.Outcome.NO_VALID_PLACEMENT, scene.update(0, 0, 0.1, true));
        assertFalse(scene.isCreatureAlerted());
        assertNull(scene.update(0, 0, 0.45, false));
        assertEquals(0.25, scene.getCreaturePosition().getX(), 1e-12);
    }

    @Test
    void invalidUpdateAndInvalidPlacementPreserveWholeSnapshot() {
        CreaturePatrolState creature = creature(new WorldPosition(3, 0), 1, WorldGeometry.empty());
        WorldSceneState scene = scene(new WorldPosition(0, 0), creature, WorldGeometry.empty());
        Object[] before = snapshot(scene);
        for (double bad : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> scene.update(0, 0, bad, true));
            assertArrayEquals(before, snapshot(scene));
        }
        assertThrows(IllegalArgumentException.class, () -> scene.update(Double.NaN, 0, 1, true));
        assertArrayEquals(before, snapshot(scene));
        WorldSceneState invalid = scene(new WorldPosition(Double.MAX_VALUE, 0), creature, WorldGeometry.empty());
        Object[] invalidBefore = snapshot(invalid);
        assertEquals(TacticalEntryResult.Outcome.INVALID_INPUT, invalid.enterTactical());
        assertArrayEquals(invalidBefore, snapshot(invalid));
    }

    @Test
    void sceneAndConstructorActorsRemainIndependentInBothModes() {
        WorldGeometry geometry = WorldGeometry.empty();
        HunterState hunter = new HunterState(new WorldPosition(8, 0), 1, 0.05);
        CreaturePatrolState creature = creature(new WorldPosition(0, 0), 0.1, geometry);
        WorldSceneState scene = new WorldSceneState(hunter, creature, grid, geometry);
        Object[] before = snapshot(scene);
        hunter.move(1, 0, 1, geometry);
        creature.advancePatrol(0.55);
        creature.update(0, new WorldPosition(20, 20));
        creature.checkDetection(creature.getPosition());
        assertArrayEquals(before, snapshot(scene));
        WorldPosition externalHunter = hunter.getPosition();
        WorldPosition externalCreature = creature.getPosition();
        WorldPosition externalLastSeen = creature.getLastSeenHunterPosition();
        scene.update(-1, 0, 0.55, false);
        assertNotEquals(before[2], scene.getHunterPosition());
        assertNotEquals(before[3], scene.getCreaturePosition());
        assertFalse(scene.isCreatureAlerted());
        assertEquals(externalHunter, hunter.getPosition());
        assertEquals(externalCreature, creature.getPosition());
        assertEquals(externalLastSeen, creature.getLastSeenHunterPosition());
        assertEquals(TacticalEntryResult.Outcome.SUCCESS, scene.enterTactical());
        Object[] tactical = snapshot(scene);
        hunter.move(0, 1, 1, geometry);
        creature.update(1, creature.getPosition());
        assertArrayEquals(tactical, snapshot(scene));
        assertFrozen(scene);
    }

    @Test
    void constructorCopiesCurrentPatrolPhaseAndAlertState() {
        WorldGeometry geometry = WorldGeometry.empty();
        CreaturePatrolState creature = new CreaturePatrolState(Arrays.asList(
            new WorldPosition(0, 0), new WorldPosition(1, 0)), 1, 0.05, 0.5, 0.1, geometry);
        creature.advancePatrol(1.75); // Разворот у второго waypoint, половина паузы осталась.
        WorldSceneState scene = scene(new WorldPosition(8, 0), creature, geometry);
        assertEquals(creature.getPosition(), scene.getCreaturePosition());
        creature.advancePatrol(0.5);
        assertNull(scene.update(0, 0, 0.5, false));
        assertEquals(creature.getPosition(), scene.getCreaturePosition());
        assertEquals(new WorldPosition(0.75, 0), scene.getCreaturePosition());
        creature.checkDetection(creature.getPosition());
        WorldSceneState alerted = scene(new WorldPosition(8, 0), creature, geometry);
        assertTrue(alerted.isCreatureAlerted());
        assertEquals(creature.getLastSeenHunterPosition(), alerted.getLastSeenHunterPosition());
        WorldPosition position = alerted.getCreaturePosition();
        assertNull(alerted.update(0, 0, 100, false));
        assertEquals(position, alerted.getCreaturePosition());
    }

    private void assertFrozen(WorldSceneState scene) {
        assertEquals(SceneMode.TACTICAL, scene.getMode());
        assertNotEquals(scene.getPlacement().getHunterCell(), scene.getPlacement().getCreatureCell());
        assertEquals(grid.cellToWorldCenter(scene.getPlacement().getHunterCell()), scene.getHunterPosition());
        assertEquals(grid.cellToWorldCenter(scene.getPlacement().getCreatureCell()), scene.getCreaturePosition());
        Object[] before = snapshot(scene);
        assertNull(scene.update(1, -1, 10000, false));
        assertEquals(TacticalEntryResult.Outcome.ALREADY_TACTICAL, scene.update(0, 0, 0, true));
        assertArrayEquals(before, snapshot(scene));
    }

    private Object[] snapshot(WorldSceneState scene) {
        return new Object[] {scene.getMode(), scene.getPlacement(), scene.getHunterPosition(),
            scene.getCreaturePosition(), scene.isCreatureAlerted(), scene.getLastSeenHunterPosition()};
    }

    private static List<StaticAabb> blockedCentersExcept(CellCoordinates... allowed) {
        List<StaticAabb> result = new ArrayList<>();
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) {
            if (!Arrays.asList(allowed).contains(new CellCoordinates(x, y))) {
                result.add(new StaticAabb(x * 2 + 0.9, y * 2 + 0.9, 0.2, 0.2));
            }
        }
        return result;
    }

    private WorldSceneState scene(WorldPosition hunter, CreaturePatrolState creature, WorldGeometry geometry) {
        return new WorldSceneState(new HunterState(hunter, 1, 0.05), creature, grid, geometry);
    }

    private CreaturePatrolState creature(WorldPosition from, double detection, WorldGeometry geometry) {
        return new CreaturePatrolState(Arrays.asList(from, from.translate(0.1, 0)), 1, 0.05, 0.5, detection, geometry);
    }
}
