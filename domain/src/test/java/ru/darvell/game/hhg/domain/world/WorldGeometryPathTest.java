package ru.darvell.game.hhg.domain.world;

import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldGeometryPathTest {
    private final WorldGeometry geometry = new WorldGeometry(
        Collections.singletonList(new StaticAabb(0, 0, 2, 2)));

    @Test
    void rejectsCrossingBetweenSafeEndpointsInBothDirections() {
        assertPath(false, new WorldPosition(-2, 1), new WorldPosition(4, 1), 0.5);
        assertPath(false, new WorldPosition(1, -2), new WorldPosition(1, 4), 0.5);
        assertPath(false, new WorldPosition(-2, -2), new WorldPosition(4, 4), 0.5);
    }

    @Test
    void accountsForRadiusAndAllowsSideTangency() {
        assertPath(true, new WorldPosition(-2, -0.5), new WorldPosition(4, -0.5), 0.5);
        assertPath(false, new WorldPosition(-2, -0.5), new WorldPosition(4, -0.5), 0.5001);
        assertPath(true, new WorldPosition(-0.5, -2), new WorldPosition(-0.5, 4), 0.5);
        assertPath(false, new WorldPosition(-0.49, -2), new WorldPosition(-0.49, 4), 0.5);
    }

    @Test
    void distinguishesRoundedCornerFromExpandedBox() {
        assertPath(true, new WorldPosition(-0.9, -0.9), new WorldPosition(-1.1, -0.7), 1);
        // Ближайшая точка к углу лежит внутри отрезка: (0.6, 0.8), расстояние 1.25.
        assertPath(true, new WorldPosition(-1.95, -0.1), new WorldPosition(0.45, -1.9), 1.25);
        assertPath(false, new WorldPosition(-1.95, -0.1), new WorldPosition(0.45, -1.9), 1.251);
    }

    @Test
    void checksEndpointsAndDegeneratePaths() {
        assertPath(false, new WorldPosition(1, 1), new WorldPosition(4, 4), 0.5);
        assertPath(false, new WorldPosition(-0.1, 1), new WorldPosition(-0.1, 1), 0.5);
        assertPath(true, new WorldPosition(-1, -1), new WorldPosition(-1, -1), 0.5);
    }

    @Test
    void handlesEmptyWorldAndMultipleObstacles() {
        assertTrue(WorldGeometry.empty().isCirclePathValid(new WorldPosition(-3, 1), new WorldPosition(5, 1), 1));
        WorldGeometry multiple = new WorldGeometry(Arrays.asList(
            new StaticAabb(10, 10, 1, 1), new StaticAabb(0, 0, 0.001, 2)));
        assertFalse(multiple.isCirclePathValid(new WorldPosition(-5, 1), new WorldPosition(5, 1), 0.1));
    }

    @Test
    void rejectsInvalidArgumentsEvenInEmptyWorld() {
        WorldPosition p = new WorldPosition(0, 0);
        assertThrows(IllegalArgumentException.class, () -> geometry.isCirclePathValid(null, p, 1));
        assertThrows(IllegalArgumentException.class, () -> geometry.isCirclePathValid(p, null, 1));
        for (double invalid : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> WorldGeometry.empty().isCirclePathValid(p, p, invalid));
        }
        for (WorldPosition invalid : Arrays.asList(new WorldPosition(Double.NaN, 0),
            new WorldPosition(0, Double.POSITIVE_INFINITY))) {
            assertThrows(IllegalArgumentException.class, () -> geometry.isCirclePathValid(p, invalid, 1));
            assertThrows(IllegalArgumentException.class, () -> geometry.isCirclePathValid(invalid, p, 1));
        }
        assertThrows(IllegalArgumentException.class, () -> WorldGeometry.empty().isCirclePathValid(
            new WorldPosition(-Double.MAX_VALUE, 0), new WorldPosition(Double.MAX_VALUE, 0), 1));
    }

    private void assertPath(boolean valid, WorldPosition a, WorldPosition b, double radius) {
        assertEquals(valid, geometry.isCirclePathValid(a, b, radius));
        assertEquals(valid, geometry.isCirclePathValid(b, a, radius));
    }
}
