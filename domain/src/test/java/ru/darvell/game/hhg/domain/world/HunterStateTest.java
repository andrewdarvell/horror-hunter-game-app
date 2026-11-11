package ru.darvell.game.hhg.domain.world;

import java.util.Collections;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HunterStateTest {
    private static final double EPSILON = 1.0e-9;
    private static final WorldGeometry EMPTY_GEOMETRY = WorldGeometry.empty();
    private static final WorldGeometry OBSTACLE_GEOMETRY = new WorldGeometry(
        Collections.singletonList(new StaticAabb(0.0, 0.0, 2.0, 2.0)));

    @Test
    void movesAlongSingleAxisUsingElapsedTime() {
        HunterState hunter = new HunterState(new WorldPosition(1.0, 2.0), 4.0, 0.4);

        hunter.move(1.0, 0.0, 0.5, EMPTY_GEOMETRY);

        assertEquals(3.0, hunter.getPosition().getX(), EPSILON);
        assertEquals(2.0, hunter.getPosition().getY(), EPSILON);
    }

    @Test
    void diagonalMovementHasSameSpeedAsStraightMovement() {
        HunterState straight = new HunterState(new WorldPosition(0.0, 0.0), 6.0, 0.4);
        HunterState diagonal = new HunterState(new WorldPosition(0.0, 0.0), 6.0, 0.4);

        straight.move(1.0, 0.0, 1.0, EMPTY_GEOMETRY);
        diagonal.move(1.0, 1.0, 1.0, EMPTY_GEOMETRY);

        assertEquals(distanceFromOrigin(straight), distanceFromOrigin(diagonal), EPSILON);
    }

    @Test
    void zeroDirectionDoesNotMoveHunter() {
        WorldPosition initialPosition = new WorldPosition(-3.0, 5.0);
        HunterState hunter = new HunterState(initialPosition, 2.0, 0.4);

        hunter.move(0.0, 0.0, 10.0, EMPTY_GEOMETRY);

        assertEquals(initialPosition, hunter.getPosition());
    }

    @Test
    void timeSplitAcrossUpdatesProducesSamePosition() {
        HunterState singleUpdate = new HunterState(new WorldPosition(0.0, 0.0), 5.0, 0.4);
        HunterState multipleUpdates = new HunterState(new WorldPosition(0.0, 0.0), 5.0, 0.4);

        singleUpdate.move(-1.0, 1.0, 1.0, EMPTY_GEOMETRY);
        for (int index = 0; index < 10; index++) {
            multipleUpdates.move(-1.0, 1.0, 0.1, EMPTY_GEOMETRY);
        }

        assertEquals(singleUpdate.getPosition().getX(), multipleUpdates.getPosition().getX(), EPSILON);
        assertEquals(singleUpdate.getPosition().getY(), multipleUpdates.getPosition().getY(), EPSILON);
    }

    @Test
    void rejectsNonPositiveOrNonFiniteRadius() {
        assertThrows(IllegalArgumentException.class,
            () -> new HunterState(new WorldPosition(0.0, 0.0), 1.0, 0.0));
        assertThrows(IllegalArgumentException.class,
            () -> new HunterState(new WorldPosition(0.0, 0.0), 1.0, Double.NaN));
    }

    @Test
    void stopsBeforeObstacleFromEverySide() {
        assertBlocked(new WorldPosition(-0.6, 1.0), 1.0, 0.0);
        assertBlocked(new WorldPosition(2.6, 1.0), -1.0, 0.0);
        assertBlocked(new WorldPosition(1.0, -0.6), 0.0, 1.0);
        assertBlocked(new WorldPosition(1.0, 2.6), 0.0, -1.0);
    }

    @Test
    void movesSafelyBesideObstacle() {
        HunterState hunter = new HunterState(new WorldPosition(-1.0, -0.6), 1.0, 0.5);

        hunter.move(1.0, 0.0, 1.0, OBSTACLE_GEOMETRY);

        assertEquals(0.0, hunter.getPosition().getX(), EPSILON);
        assertEquals(-0.6, hunter.getPosition().getY(), EPSILON);
    }

    @Test
    void slidesAlongVerticalSurfaceWhenHorizontalComponentIsBlocked() {
        HunterState hunter = new HunterState(new WorldPosition(-0.6, 0.5), 1.0, 0.5);

        hunter.move(1.0, 1.0, 0.2, OBSTACLE_GEOMETRY);

        assertEquals(-0.6, hunter.getPosition().getX(), EPSILON);
        assertEquals(0.5 + Math.sqrt(0.02), hunter.getPosition().getY(), EPSILON);
    }

    @Test
    void slidesAlongHorizontalSurfaceWhenVerticalComponentIsBlocked() {
        HunterState hunter = new HunterState(new WorldPosition(0.5, -0.6), 1.0, 0.5);

        hunter.move(1.0, 1.0, 0.2, OBSTACLE_GEOMETRY);

        assertEquals(0.5 + Math.sqrt(0.02), hunter.getPosition().getX(), EPSILON);
        assertEquals(-0.6, hunter.getPosition().getY(), EPSILON);
    }

    private static void assertBlocked(
        WorldPosition initialPosition,
        double directionX,
        double directionY
    ) {
        HunterState hunter = new HunterState(initialPosition, 1.0, 0.5);

        hunter.move(directionX, directionY, 0.2, OBSTACLE_GEOMETRY);

        assertEquals(initialPosition, hunter.getPosition());
    }

    private static double distanceFromOrigin(HunterState hunter) {
        WorldPosition position = hunter.getPosition();
        return Math.hypot(position.getX(), position.getY());
    }
}
