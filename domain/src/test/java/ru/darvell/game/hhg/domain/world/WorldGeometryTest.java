package ru.darvell.game.hhg.domain.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldGeometryTest {
    private static final StaticAabb OBSTACLE = new StaticAabb(0.0, 0.0, 2.0, 2.0);
    private final WorldGeometry geometry = new WorldGeometry(Collections.singletonList(OBSTACLE));

    @Test
    void ownsAnImmutableCopyOfObstacles() {
        List<StaticAabb> source = new ArrayList<>();
        source.add(OBSTACLE);
        WorldGeometry copiedGeometry = new WorldGeometry(source);

        source.clear();

        assertEquals(Collections.singletonList(OBSTACLE), copiedGeometry.getObstacles());
        assertThrows(UnsupportedOperationException.class,
            () -> copiedGeometry.getObstacles().clear());
    }

    @Test
    void rejectsCirclePenetrationAtEverySide() {
        assertFalse(geometry.isCirclePositionValid(new WorldPosition(-0.5, 1.0), 1.0));
        assertFalse(geometry.isCirclePositionValid(new WorldPosition(2.5, 1.0), 1.0));
        assertFalse(geometry.isCirclePositionValid(new WorldPosition(1.0, -0.5), 1.0));
        assertFalse(geometry.isCirclePositionValid(new WorldPosition(1.0, 2.5), 1.0));
    }

    @Test
    void allowsTouchingEverySideWithoutPenetration() {
        assertTrue(geometry.isCirclePositionValid(new WorldPosition(-1.0, 1.0), 1.0));
        assertTrue(geometry.isCirclePositionValid(new WorldPosition(3.0, 1.0), 1.0));
        assertTrue(geometry.isCirclePositionValid(new WorldPosition(1.0, -1.0), 1.0));
        assertTrue(geometry.isCirclePositionValid(new WorldPosition(1.0, 3.0), 1.0));
    }

    @Test
    void rejectsCenterInsideObstacle() {
        assertFalse(geometry.isCirclePositionValid(new WorldPosition(1.0, 1.0), 0.1));
    }

    @Test
    void distinguishesCornerPenetrationFromSafeExpandedBoxArea() {
        assertFalse(geometry.isCirclePositionValid(new WorldPosition(-0.5, -0.5), 1.0));
        assertTrue(geometry.isCirclePositionValid(new WorldPosition(-0.9, -0.9), 1.0));
    }

    @Test
    void allowsSafePositionAwayFromObstacle() {
        assertTrue(geometry.isCirclePositionValid(new WorldPosition(-2.0, 1.0), 0.5));
    }

    @Test
    void rejectsInvalidRadius() {
        assertThrows(IllegalArgumentException.class,
            () -> geometry.isCirclePositionValid(new WorldPosition(0.0, 0.0), 0.0));
        assertThrows(IllegalArgumentException.class,
            () -> geometry.isCirclePositionValid(new WorldPosition(0.0, 0.0), Double.NaN));
    }
}
