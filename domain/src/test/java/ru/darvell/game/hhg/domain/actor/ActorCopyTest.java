package ru.darvell.game.hhg.domain.actor;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import ru.darvell.game.hhg.domain.world.StaticAabb;
import ru.darvell.game.hhg.domain.world.WorldGeometry;
import ru.darvell.game.hhg.domain.world.WorldPosition;

import static org.junit.jupiter.api.Assertions.*;

class ActorCopyTest {
    private final WorldGeometry geometry = new WorldGeometry(Arrays.asList(new StaticAabb(4, -1, 1, 4)));

    @Test
    void hunterCopiesCurrentPositionParametersAndIndependentMovement() {
        HunterState hunter = new HunterState(p(0, 0), 2, 0.3);
        hunter.move(1, 0, 0.5, geometry);
        HunterState copy = hunter.copy();
        assertNotSame(hunter, copy);
        assertEquals(hunter.getPosition(), copy.getPosition());
        assertEquals(hunter.getMovementSpeed(), copy.getMovementSpeed());
        assertEquals(hunter.getRadius(), copy.getRadius());
        WorldPosition before = hunter.getPosition();
        copy.move(1, 0, 10, geometry);
        assertEquals(before, hunter.getPosition());
        hunter.move(1, 0, 10, geometry);
        assertEquals(hunter.getPosition(), copy.getPosition());
        hunter.move(-1, 0, 0.5, geometry);
        assertNotEquals(hunter.getPosition(), copy.getPosition());

        HunterState placed = hunter.withTacticalPosition(p(2, 2));
        assertNotSame(hunter, placed);
        assertEquals(p(2, 2), placed.getPosition());
        assertEquals(2, placed.getMovementSpeed());
        assertEquals(0.3, placed.getRadius());
        WorldPosition sourcePosition = hunter.getPosition();
        placed.move(0, 1, 0.5, geometry);
        assertEquals(sourcePosition, hunter.getPosition());
    }

    @Test
    void copiesDoNotAddValidationToExistingConstructorStates() {
        HunterState hunter = new HunterState(p(Double.NaN, Double.POSITIVE_INFINITY), 1, 0.1);
        assertEquals(hunter.getPosition(), hunter.copy().getPosition());
        assertEquals(p(0, 0), hunter.withTacticalPosition(p(0, 0)).getPosition());
    }

    @Test
    void patrolCopiesPreserveEveryPhaseAndFutureBehaviour() {
        for (double elapsed : new double[] {0, 0.25, 0.75, 1, 1.8, 2.7, 4.2}) {
            CreaturePatrolState creature = creature();
            creature.advancePatrol(elapsed);
            CreaturePatrolState copy = creature.copy();
            assertNotSame(creature, copy);
            assertArrayEquals(snapshot(creature), snapshot(copy));
            Object[] sourceBefore = snapshot(creature);
            copy.advancePatrol(0.13);
            assertArrayEquals(sourceBefore, snapshot(creature));
            creature.advancePatrol(0.13);
            assertArrayEquals(snapshot(creature), snapshot(copy));
            for (double delta : new double[] {0.1, 0.7, 1.5, 3}) {
                creature.advancePatrol(delta);
                copy.advancePatrol(delta);
                assertArrayEquals(snapshot(creature), snapshot(copy));
            }
            Object[] copyBefore = snapshot(copy);
            creature.advancePatrol(0.17);
            assertArrayEquals(copyBefore, snapshot(copy));
        }
    }

    @Test
    void tacticalCopiesPreservePatrolMetadataAndPreSnapDetection() {
        for (double elapsed : new double[] {0, 0.25, 0.75, 1.8, 2.7}) {
            for (boolean alerted : new boolean[] {false, true}) {
                CreaturePatrolState creature = creature();
                creature.advancePatrol(elapsed);
                if (alerted) assertTrue(creature.checkDetection(creature.getPosition()));
                Object[] before = snapshot(creature);
                CreaturePatrolState placed = creature.withTacticalPosition(p(-2, 3));
                assertNotSame(creature, placed);
                assertEquals(p(-2, 3), placed.getPosition());
                assertArrayEquals(metadata(creature), metadata(placed));
                assertArrayEquals(before, snapshot(creature));
                placed.advancePatrol(0);
                assertArrayEquals(metadata(creature), metadata(placed));
                if (alerted) {
                    Object[] placedBefore = snapshot(placed);
                    placed.advancePatrol(10000);
                    assertArrayEquals(placedBefore, snapshot(placed));
                    assertEquals(before[3], placed.getLastSeenHunterPosition());
                } else {
                    placed.checkDetection(placed.getPosition());
                    assertFalse(creature.isAlerted());
                }
                assertArrayEquals(before, snapshot(creature));
            }
        }
    }

    @Test
    void copiedDetectionKeepsRadiusGeometryAndIndependentLastSeen() {
        CreaturePatrolState creature = creature();
        CreaturePatrolState copy = creature.copy();
        assertFalse(copy.checkDetection(p(2, 0))); // Радиус 1.5.
        assertFalse(copy.checkDetection(p(0, 2)));
        assertTrue(copy.checkDetection(p(1, 0)));
        assertFalse(creature.isAlerted());
        assertNull(creature.getLastSeenHunterPosition());
        assertTrue(creature.checkDetection(p(0, 1)));
        assertEquals(p(1, 0), copy.getLastSeenHunterPosition());
        assertFalse(copy.checkDetection(p(0, 1)));
        assertEquals(p(0, 1), copy.getLastSeenHunterPosition());
        CreaturePatrolState behindWall = new CreaturePatrolState(Arrays.asList(p(3, 0), p(3, 2)),
            2, 0.2, 0.5, 10, geometry);
        assertFalse(behindWall.copy().checkDetection(p(6, 0)));
    }

    @Test
    void invalidTacticalCentersNeverChangeEitherSource() {
        HunterState hunter = new HunterState(p(1, 2), 2, 0.3);
        CreaturePatrolState creature = creature();
        creature.advancePatrol(1.8);
        creature.checkDetection(creature.getPosition());
        Object[] before = snapshot(creature);
        for (WorldPosition bad : Arrays.asList(null, p(Double.NaN, 0), p(0, Double.NaN),
            p(Double.POSITIVE_INFINITY, 0), p(0, Double.NEGATIVE_INFINITY))) {
            assertThrows(IllegalArgumentException.class, () -> hunter.withTacticalPosition(bad));
            assertThrows(IllegalArgumentException.class, () -> creature.withTacticalPosition(bad));
            assertEquals(p(1, 2), hunter.getPosition());
            assertArrayEquals(before, snapshot(creature));
        }
    }

    @Test
    void independentPhasesValidateBeforeMutationEvenWhenAlerted() {
        for (boolean alerted : new boolean[] {false, true}) {
            CreaturePatrolState creature = creature();
            creature.advancePatrol(0.75);
            if (alerted) creature.checkDetection(creature.getPosition());
            Object[] before = snapshot(creature);
            for (double bad : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
                assertThrows(IllegalArgumentException.class, () -> creature.advancePatrol(bad));
                assertArrayEquals(before, snapshot(creature));
            }
            for (WorldPosition bad : Arrays.asList(null, p(Double.NaN, 0), p(0, Double.POSITIVE_INFINITY),
                p(Double.MAX_VALUE, Double.MAX_VALUE))) {
                assertThrows(IllegalArgumentException.class, () -> creature.checkDetection(bad));
                assertArrayEquals(before, snapshot(creature));
            }
            creature.advancePatrol(0);
            assertArrayEquals(before, snapshot(creature));
        }
    }

    @Test
    void separatePhasesMatchUpdateAndDetectionPrecedesPatrol() {
        CreaturePatrolState combined = creature();
        CreaturePatrolState separate = combined.copy();
        combined.update(0.75, p(20, 20));
        assertFalse(separate.checkDetection(p(20, 20)));
        separate.advancePatrol(0.75);
        assertArrayEquals(snapshot(combined), snapshot(separate));
        Object[] before = snapshot(separate);
        WorldPosition seen = separate.getPosition();
        combined.update(1, seen);
        assertTrue(separate.checkDetection(seen));
        separate.advancePatrol(1);
        assertArrayEquals(snapshot(combined), snapshot(separate));
        assertEquals(before[0], separate.getPosition());
        assertEquals(before[4], separate.getTargetIndex());
        assertEquals(before[5], separate.getDirection());
        assertEquals(before[6], separate.getWaitRemaining());
    }

    private CreaturePatrolState creature() {
        return new CreaturePatrolState(Arrays.asList(p(0, 0), p(1, 0), p(1, 2)),
            2, 0.2, 0.5, 1.5, geometry);
    }

    private Object[] metadata(CreaturePatrolState creature) {
        return new Object[] {creature.getRadius(), creature.isAlerted(), creature.getLastSeenHunterPosition(),
            creature.getTargetIndex(), creature.getDirection(), creature.getWaitRemaining()};
    }

    private Object[] snapshot(CreaturePatrolState creature) {
        return new Object[] {creature.getPosition(), creature.getRadius(), creature.isAlerted(),
            creature.getLastSeenHunterPosition(), creature.getTargetIndex(), creature.getDirection(), creature.getWaitRemaining()};
    }

    private static WorldPosition p(double x, double y) { return new WorldPosition(x, y); }
}
