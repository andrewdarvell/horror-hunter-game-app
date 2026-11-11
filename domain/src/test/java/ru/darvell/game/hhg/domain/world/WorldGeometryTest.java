package ru.darvell.game.hhg.domain.world;

import java.util.ArrayList;
import java.util.Arrays;
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

    @Test
    void slidesAroundEveryCornerWhenBothAxisCandidatesAreBlocked() {
        for (int signX : new int[] {-1, 1}) {
            for (int signY : new int[] {-1, 1}) {
                for (boolean swapAxes : new boolean[] {false, true}) {
                    double cornerX = signX < 0 ? 0.0 : 2.0;
                    double cornerY = signY < 0 ? 0.0 : 2.0;
                    WorldPosition start = new WorldPosition(
                        cornerX + signX * (swapAxes ? 0.801 : 0.601),
                        cornerY + signY * (swapAxes ? 0.601 : 0.801));
                    double dx = -signX * 0.2;
                    double dy = -signY * 0.2;
                    assertTrue(geometry.isCirclePositionValid(start, 1.0));
                    assertFalse(geometry.isCirclePositionValid(start.translate(dx, 0.0), 1.0));
                    assertFalse(geometry.isCirclePositionValid(start.translate(0.0, dy), 1.0));

                    WorldPosition result = geometry.resolveMovement(start, 1.0, dx, dy);

                    assertTrue(geometry.isCirclePositionValid(result, 1.0));
                    double movedX = result.getX() - start.getX();
                    double movedY = result.getY() - start.getY();
                    assertTrue(movedX * dx + movedY * dy > 0.0,
                        "Corner must preserve forward tangential movement: " + start);
                    assertTrue(Math.hypot(movedX, movedY) <= Math.hypot(dx, dy));
                }
            }
        }
    }

    @Test
    void stopsWhenInputPointsDirectlyIntoCorner() {
        WorldPosition start = new WorldPosition(-0.75, -0.75);

        for (int step = 0; step < 100; step++) {
            assertEquals(start, geometry.resolveMovement(start, 1.0, 0.1, 0.1));
        }
    }

    @Test
    void stopsForRadialInputAtAsymmetricCornerContact() {
        WorldPosition start = new WorldPosition(-0.6, -0.8);

        assertEquals(start, geometry.resolveMovement(start, 1.0, 0.15, 0.2));
    }

    @Test
    void tangentialFallbackCannotEnterAnotherObstacle() {
        WorldGeometry confined = new WorldGeometry(Arrays.asList(
            OBSTACLE, new StaticAabb(-2.0, -2.0, 2.0, 0.2)));
        WorldPosition start = new WorldPosition(-0.6, -0.8);

        assertTrue(confined.isCirclePositionValid(start, 1.0));
        assertEquals(start, confined.resolveMovement(start, 1.0, 0.2, 0.2));
    }
}
