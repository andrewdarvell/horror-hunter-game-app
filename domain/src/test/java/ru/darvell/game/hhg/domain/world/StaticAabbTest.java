package ru.darvell.game.hhg.domain.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StaticAabbTest {
    @Test
    void exposesFiniteBounds() {
        StaticAabb obstacle = new StaticAabb(-2.0, 3.0, 4.0, 5.0);

        assertEquals(-2.0, obstacle.getX());
        assertEquals(3.0, obstacle.getY());
        assertEquals(4.0, obstacle.getWidth());
        assertEquals(5.0, obstacle.getHeight());
        assertEquals(2.0, obstacle.getMaxX());
        assertEquals(8.0, obstacle.getMaxY());
    }

    @Test
    void rejectsNonFinitePositionAndBounds() {
        assertThrows(IllegalArgumentException.class,
            () -> new StaticAabb(Double.NaN, 0.0, 1.0, 1.0));
        assertThrows(IllegalArgumentException.class,
            () -> new StaticAabb(0.0, Double.POSITIVE_INFINITY, 1.0, 1.0));
        assertThrows(IllegalArgumentException.class,
            () -> new StaticAabb(Double.MAX_VALUE, 0.0, Double.MAX_VALUE, 1.0));
    }

    @Test
    void rejectsNonPositiveOrNonFiniteDimensions() {
        assertThrows(IllegalArgumentException.class,
            () -> new StaticAabb(0.0, 0.0, 0.0, 1.0));
        assertThrows(IllegalArgumentException.class,
            () -> new StaticAabb(0.0, 0.0, 1.0, -1.0));
        assertThrows(IllegalArgumentException.class,
            () -> new StaticAabb(0.0, 0.0, 1.0, Double.NaN));
    }
}
