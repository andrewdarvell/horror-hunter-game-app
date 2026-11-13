package ru.darvell.game.hhg.domain.scene;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import ru.darvell.game.hhg.domain.actor.*;
import ru.darvell.game.hhg.domain.tactical.*;
import ru.darvell.game.hhg.domain.world.*;
import static org.junit.jupiter.api.Assertions.*;
import static ru.darvell.game.hhg.domain.tactical.TacticalMoveResult.*;

class TacticalMovementTest {
    private WorldSceneState scene(TacticalGrid grid, double radius, WorldGeometry geometry,
                                  CellCoordinates start, CellCoordinates creature) {
        WorldPosition cp = grid.cellToWorldCenter(creature);
        WorldSceneState s = new WorldSceneState(new HunterState(grid.cellToWorldCenter(start), 1, radius),
            new CreaturePatrolState(Arrays.asList(cp, cp.translate(0, grid.getCellSize())), 2, 0.3, 0.5, 3, geometry), grid, geometry);
        assertEquals(TacticalEntryResult.Outcome.SUCCESS, s.enterTactical());
        return s;
    }
    private WorldSceneState scene(WorldGeometry geometry, double radius) {
        return scene(new TacticalGrid(0, 0, 1), radius, geometry, c(0, 0), c(5, 0));
    }
    private static CellCoordinates c(int x, int y) { return new CellCoordinates(x, y); }
    private static Object[] snapshot(WorldSceneState s) {
        return new Object[] {s.getMode(), s.getPlacement(), s.getHunterPosition(), s.getCreaturePosition(),
            s.isCreatureAlerted(), s.getLastSeenHunterPosition()};
    }
    private void rejected(WorldSceneState s, CellCoordinates target, TacticalMoveResult result) {
        Object[] before = snapshot(s);
        assertEquals(result, s.tryMoveHunterTo(target));
        assertArrayEquals(before, snapshot(s));
        assertSame(before[1], s.getPlacement());
    }
    @Test void eightDirectionsDifferentGridsAndSequentialReturn() {
        for (double size : new double[] {0.5, 1, 3}) for (double radius : new double[] {0.1, 0.4}) {
            TacticalGrid grid = new TacticalGrid(-2, 3, size);
            for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) {
                if (x == 0 && y == 0) continue;
                WorldSceneState s = scene(grid, radius, WorldGeometry.empty(), c(0, 0), c(8, 0));
                WorldPosition creature = s.getCreaturePosition();
                assertEquals(SUCCESS, s.tryMoveHunterTo(c(x, y)));
                assertEquals(grid.cellToWorldCenter(c(x, y)), s.getHunterPosition());
                assertEquals(c(x, y), s.getPlacement().getHunterCell());
                assertSame(creature, s.getCreaturePosition());
                Object[] frozen = snapshot(s);
                s.update(1, -1, 100, true);
                assertArrayEquals(frozen, snapshot(s));
                assertEquals(SUCCESS, s.tryMoveHunterTo(c(0, 0)));
                assertEquals(grid.cellToWorldCenter(c(0, 0)), s.getHunterPosition());
            }
        }
    }
    @Test void deterministicFailuresAndOccupiedCell() {
        WorldSceneState s = scene(WorldGeometry.empty(), .4);
        rejected(s, null, INVALID_INPUT); rejected(s, c(0, 0), SAME_CELL);
        rejected(s, c(5, 0), NOT_ADJACENT);
        for (int x = 1; x < 5; x++) assertEquals(SUCCESS, s.tryMoveHunterTo(c(x, 0)));
        rejected(s, c(5, 0), OCCUPIED);
        rejected(s, c(Integer.MIN_VALUE, Integer.MAX_VALUE), NOT_ADJACENT);
        TacticalGrid grid = new TacticalGrid(0, 0, 1);
        WorldSceneState extreme = scene(grid, .4, WorldGeometry.empty(), c(Integer.MIN_VALUE, 0), c(5, 0));
        rejected(extreme, c(Integer.MAX_VALUE, 0), NOT_ADJACENT);
    }
    @Test void explorationRejectsEvenNull() {
        WorldGeometry g = WorldGeometry.empty();
        WorldSceneState s = new WorldSceneState(new HunterState(new WorldPosition(0, 0), 1, .4),
            new CreaturePatrolState(Arrays.asList(new WorldPosition(5, 0), new WorldPosition(5, 1)), 1, .3, .5, 3, g),
            new TacticalGrid(0, 0, 1), g);
        rejected(s, null, NOT_TACTICAL); rejected(s, c(1, 0), NOT_TACTICAL);
    }
    @Test void centerThinWallAndRadiusOnlyDiagonal() {
        rejected(scene(g(new StaticAabb(1.4, .4, .2, .2)), .4), c(1, 0), BLOCKED_CENTER);
        rejected(scene(g(new StaticAabb(.99, 0, .02, 1)), .4), c(1, 0), BLOCKED_PATH);
        WorldGeometry corner = g(new StaticAabb(.95, .45, .1, .1));
        assertTrue(corner.hasLineOfSight(new WorldPosition(.5, .5), new WorldPosition(1.5, 1.5)));
        rejected(scene(corner, .4), c(1, 1), BLOCKED_PATH);
        assertEquals(SUCCESS, scene(corner, .1).tryMoveHunterTo(c(1, 1)));
    }
    @Test void tangentAllowedAndOrthogonalCentersNeedNotBeFree() {
        WorldGeometry tangent = g(new StaticAabb(.9, .75, .2, .1));
        assertEquals(SUCCESS, scene(tangent, .25).tryMoveHunterTo(c(1, 0)));
        rejected(scene(tangent, .3), c(1, 0), BLOCKED_PATH);
        WorldGeometry corridor = new WorldGeometry(Arrays.asList(new StaticAabb(1.45, .45, .1, .1),
            new StaticAabb(.45, 1.45, .1, .1)));
        assertEquals(SUCCESS, scene(corridor, .4).tryMoveHunterTo(c(1, 1)));
    }
    @Test void unstableCenterRejectedWithoutMutation() {
        TacticalGrid grid = new TacticalGrid(1e16, 0, 1);
        WorldSceneState s = scene(grid, .4, WorldGeometry.empty(), c(0, 0), c(8, 0));
        rejected(s, c(1, 0), INVALID_INPUT);
    }
    @Test void overflowingCenterAndDisplacementRejected() {
        TacticalGrid huge = new TacticalGrid(0, 0, 1e308);
        WorldSceneState s = scene(huge, .4, WorldGeometry.empty(), c(0, 0), c(-1, 0));
        assertEquals(SUCCESS, s.tryMoveHunterTo(c(1, 0)));
        rejected(s, c(2, 0), INVALID_INPUT);
        s = scene(new TacticalGrid(0, 0, 1.3e308), .4, WorldGeometry.empty(), c(-1, -1), c(0, -1));
        rejected(s, c(0, 0), INVALID_INPUT);
    }
    @Test void stepPreservesAlertAndLastSeenFromAutomaticEntry() {
        WorldGeometry g = WorldGeometry.empty();
        WorldSceneState s = new WorldSceneState(new HunterState(new WorldPosition(.5, .5), 1, .4),
            new CreaturePatrolState(Arrays.asList(new WorldPosition(2.5, .5), new WorldPosition(2.5, 1.5)),
                2, .3, .5, 3, g), new TacticalGrid(0, 0, 1), g);
        assertEquals(TacticalEntryResult.Outcome.SUCCESS, s.update(0, 0, 0, false));
        assertTrue(s.isCreatureAlerted());
        WorldPosition seen = s.getLastSeenHunterPosition(), creature = s.getCreaturePosition();
        CellCoordinates occupied = s.getPlacement().getCreatureCell();
        assertEquals(SUCCESS, s.tryMoveHunterTo(c(0, 1)));
        assertSame(seen, s.getLastSeenHunterPosition()); assertSame(creature, s.getCreaturePosition());
        assertSame(occupied, s.getPlacement().getCreatureCell()); assertTrue(s.isCreatureAlerted());
        Object[] frozen = snapshot(s);
        s.update(1, 1, 100, true); assertArrayEquals(frozen, snapshot(s));
    }
    private static WorldGeometry g(StaticAabb box) { return new WorldGeometry(Arrays.asList(box)); }
}
