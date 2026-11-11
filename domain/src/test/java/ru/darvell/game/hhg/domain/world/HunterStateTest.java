package ru.darvell.game.hhg.domain.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HunterStateTest {
    private static final double EPSILON = 1.0e-9;

    @Test
    void movesAlongSingleAxisUsingElapsedTime() {
        HunterState hunter = new HunterState(new WorldPosition(1.0, 2.0), 4.0);

        hunter.move(1.0, 0.0, 0.5);

        assertEquals(3.0, hunter.getPosition().getX(), EPSILON);
        assertEquals(2.0, hunter.getPosition().getY(), EPSILON);
    }

    @Test
    void diagonalMovementHasSameSpeedAsStraightMovement() {
        HunterState straight = new HunterState(new WorldPosition(0.0, 0.0), 6.0);
        HunterState diagonal = new HunterState(new WorldPosition(0.0, 0.0), 6.0);

        straight.move(1.0, 0.0, 1.0);
        diagonal.move(1.0, 1.0, 1.0);

        assertEquals(distanceFromOrigin(straight), distanceFromOrigin(diagonal), EPSILON);
    }

    @Test
    void zeroDirectionDoesNotMoveHunter() {
        WorldPosition initialPosition = new WorldPosition(-3.0, 5.0);
        HunterState hunter = new HunterState(initialPosition, 2.0);

        hunter.move(0.0, 0.0, 10.0);

        assertEquals(initialPosition, hunter.getPosition());
    }

    @Test
    void timeSplitAcrossUpdatesProducesSamePosition() {
        HunterState singleUpdate = new HunterState(new WorldPosition(0.0, 0.0), 5.0);
        HunterState multipleUpdates = new HunterState(new WorldPosition(0.0, 0.0), 5.0);

        singleUpdate.move(-1.0, 1.0, 1.0);
        for (int index = 0; index < 10; index++) {
            multipleUpdates.move(-1.0, 1.0, 0.1);
        }

        assertEquals(singleUpdate.getPosition().getX(), multipleUpdates.getPosition().getX(), EPSILON);
        assertEquals(singleUpdate.getPosition().getY(), multipleUpdates.getPosition().getY(), EPSILON);
    }

    private static double distanceFromOrigin(HunterState hunter) {
        WorldPosition position = hunter.getPosition();
        return Math.hypot(position.getX(), position.getY());
    }
}
