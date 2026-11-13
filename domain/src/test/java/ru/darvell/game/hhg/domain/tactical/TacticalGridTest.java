package ru.darvell.game.hhg.domain.tactical;

import ru.darvell.game.hhg.domain.world.StaticAabb;
import ru.darvell.game.hhg.domain.world.WorldGeometry;
import ru.darvell.game.hhg.domain.world.WorldPosition;

import java.util.Collections;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TacticalGridTest {
    private static final double EPSILON = 1.0e-9;
    private final TacticalGrid grid = new TacticalGrid(1.0, -2.0, 2.0);

    @Test
    void mapsPositionInsideCell() {
        assertEquals(new CellCoordinates(1, 1), grid.worldToCell(new WorldPosition(4.5, 1.5)));
    }

    @Test
    void mapsExactBoundaryToCellStartingAtBoundary() {
        assertEquals(new CellCoordinates(2, 3), grid.worldToCell(new WorldPosition(5.0, 4.0)));
    }

    @Test
    void mapsPositionImmediatelyBelowOriginToNegativeCell() {
        assertEquals(new CellCoordinates(-1, -1), grid.worldToCell(new WorldPosition(0.999, -2.001)));
    }

    @Test
    void mapsNegativeCellToWorldCenter() {
        WorldPosition center = grid.cellToWorldCenter(new CellCoordinates(-2, -1));

        assertEquals(-2.0, center.getX(), EPSILON);
        assertEquals(-3.0, center.getY(), EPSILON);
    }

    @Test
    void mapsCellCenterBackToSameCell() {
        CellCoordinates cell = new CellCoordinates(-4, 7);

        assertEquals(cell, grid.worldToCell(grid.cellToWorldCenter(cell)));
    }

    @Test
    void rejectsNonPositiveCellSize() {
        assertThrows(IllegalArgumentException.class, () -> new TacticalGrid(0.0, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new TacticalGrid(0.0, 0.0, -1.0));
    }

    @Test
    void reportsCellPassableWhenHunterCircleIsSafeAtCenter() {
        WorldGeometry geometry = geometryWith(new StaticAabb(4.0, 4.0, 1.0, 1.0));

        assertTrue(grid.isCellPassable(new CellCoordinates(0, 0), 0.5, geometry));
    }

    @Test
    void reportsCellBlockedWhenCenterIsInsideObstacle() {
        WorldGeometry geometry = geometryWith(new StaticAabb(1.75, -1.25, 0.5, 0.5));

        assertFalse(grid.isCellPassable(new CellCoordinates(0, 0), 0.5, geometry));
    }

    @Test
    void accountsForPartialOverlapOutsideObstacle() {
        WorldGeometry geometry = geometryWith(new StaticAabb(2.4, -1.25, 0.5, 0.5));

        assertFalse(grid.isCellPassable(new CellCoordinates(0, 0), 0.5, geometry));
    }

    @Test
    void passabilityDependsOnRadiusAgainstSameGeometry() {
        WorldGeometry geometry = geometryWith(new StaticAabb(2.4, -1.25, 0.5, 0.5));
        CellCoordinates cell = new CellCoordinates(0, 0);

        assertTrue(grid.isCellPassable(cell, 0.3, geometry));
        assertFalse(grid.isCellPassable(cell, 0.5, geometry));
    }

    private static WorldGeometry geometryWith(StaticAabb obstacle) {
        return new WorldGeometry(Collections.singletonList(obstacle));
    }
}
