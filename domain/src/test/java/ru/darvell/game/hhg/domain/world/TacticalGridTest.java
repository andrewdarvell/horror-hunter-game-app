package ru.darvell.game.hhg.domain.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
