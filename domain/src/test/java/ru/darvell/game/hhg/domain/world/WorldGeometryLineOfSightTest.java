package ru.darvell.game.hhg.domain.world;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldGeometryLineOfSightTest {
    private final WorldGeometry geometry = new WorldGeometry(Arrays.asList(
        new StaticAabb(20, 20, 1, 1), new StaticAabb(0, 0, 2, 2)));

    @Test
    void checksEntireClosedSegmentInBothDirections() {
        sight(false, -1, 1, 3, 1); // Горизонтальный луч через стену.
        sight(false, 1, -1, 1, 3); // Вертикальный луч.
        sight(false, -1, -1, 3, 3);
        sight(false, -1, 1, 1, -1); // Касание угла (0, 0).
        sight(false, -1, 0, 3, 0); // Вдоль границы.
        sight(false, -1, 1, 0, 1); // Endpoint на границе.
        sight(false, -1, 1, 1, 1); // Endpoint внутри.
        sight(false, 1, 1, 1, 1);
        sight(false, 0, 0, 0, 0);
        sight(true, -1, -1, -1, -1);
        sight(true, -1, -1, 3, -1);
        sight(true, 3, -1, 3, 3);
        sight(true, -1, 3, 3, 3); // Пересечение бесконечной прямой за отрезком не мешает.
        WorldGeometry thin = new WorldGeometry(Arrays.asList(new StaticAabb(0, 0, 0.000001, 2)));
        assertFalse(thin.hasLineOfSight(p(-1, 1), p(1, 1)));
        assertFalse(thin.hasLineOfSight(p(1, 1), p(-1, 1)));
    }

    @Test
    void emptyWorldAndNarrowVisibleRayDoNotUseBodyRadius() {
        assertTrue(WorldGeometry.empty().hasLineOfSight(p(-1, 1), p(3, 1)));
        assertTrue(WorldGeometry.empty().hasLineOfSight(p(3, 1), p(-1, 1)));
        assertTrue(WorldGeometry.empty().hasLineOfSight(p(0, 0), p(0, 0)));
        sight(true, -1, -0.1, 3, -0.1);
        assertFalse(geometry.isCirclePathValid(p(-1, -0.1), p(3, -0.1), 0.3));
    }

    @Test
    void validatesBothEndpointsBeforeObstacleShortCircuitEvenInEmptyWorld() {
        WorldPosition[] invalid = {null, p(Double.NaN, 0), p(0, Double.NaN),
            p(Double.POSITIVE_INFINITY, 0), p(0, Double.NEGATIVE_INFINITY)};
        for (WorldGeometry world : Arrays.asList(geometry, WorldGeometry.empty())) {
            for (WorldPosition point : invalid) {
                assertThrows(IllegalArgumentException.class, () -> world.hasLineOfSight(p(1, 1), point));
                assertThrows(IllegalArgumentException.class, () -> world.hasLineOfSight(point, p(1, 1)));
            }
            assertThrows(IllegalArgumentException.class,
                () -> world.hasLineOfSight(p(-Double.MAX_VALUE, 0), p(Double.MAX_VALUE, 0)));
            assertThrows(IllegalArgumentException.class,
                () -> world.hasLineOfSight(p(0, 0), p(Double.MAX_VALUE, Double.MAX_VALUE)));
        }
    }

    private void sight(boolean expected, double x1, double y1, double x2, double y2) {
        assertEquals(expected, geometry.hasLineOfSight(p(x1, y1), p(x2, y2)));
        assertEquals(expected, geometry.hasLineOfSight(p(x2, y2), p(x1, y1)));
    }

    private static WorldPosition p(double x, double y) {
        return new WorldPosition(x, y);
    }
}
