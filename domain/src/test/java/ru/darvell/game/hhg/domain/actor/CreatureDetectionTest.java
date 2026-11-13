package ru.darvell.game.hhg.domain.actor;

import ru.darvell.game.hhg.domain.world.StaticAabb;
import ru.darvell.game.hhg.domain.world.WorldGeometry;
import ru.darvell.game.hhg.domain.world.WorldPosition;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CreatureDetectionTest {
    private static final WorldPosition FAR = p(100, 100);

    @Test
    void detectsOnFirstUpdateIncludingZeroAtRadiusFromEverySide() {
        for (WorldPosition hunter : Arrays.asList(p(3, 0), p(-3, 0), p(0, 3), p(0, -3),
            p(1, 1), p(0, 0))) {
            for (double delta : new double[] {0, 0.1, 10}) {
                CreaturePatrolState creature = creature(WorldGeometry.empty(), 3);
                assertFalse(creature.isAlerted());
                assertNull(creature.getLastSeenHunterPosition());
                creature.update(delta, hunter);
                assertTrue(creature.isAlerted());
                assertEquals(hunter, creature.getLastSeenHunterPosition());
                assertEquals(p(0, 0), creature.getPosition());
                assertEquals(0.5, creature.getWaitRemaining());
            }
        }
        CreaturePatrolState outside = creature(WorldGeometry.empty(), 3);
        outside.update(0, p(Math.nextUp(3.0), 0));
        assertFalse(outside.isAlerted());
        assertNull(outside.getLastSeenHunterPosition());
    }

    @Test
    void blockedHunterDoesNotStopPatrolButEmergenceStopsBeforeNextStep() {
        WorldGeometry wall = new WorldGeometry(Arrays.asList(new StaticAabb(0.5, -1, 0.1, 2)));
        CreaturePatrolState creature = creature(wall, 3);
        creature.update(0.75, p(1, 0));
        assertFalse(creature.isAlerted());
        assertEquals(p(0, 0.5), creature.getPosition());
        creature.update(1, p(-1, 0));
        assertTrue(creature.isAlerted());
        assertEquals(p(0, 0.5), creature.getPosition());
    }

    @Test
    void freezesWholePatrolStateDuringMovementAndPause() {
        for (double before : new double[] {0.25, 0.75, 1.5}) {
            CreaturePatrolState creature = creature(WorldGeometry.empty(), 3);
            creature.update(before, FAR);
            WorldPosition position = creature.getPosition();
            int target = creature.getTargetIndex();
            int direction = creature.getDirection();
            double wait = creature.getWaitRemaining();
            creature.update(10, position.translate(1, 0));
            creature.update(100, FAR);
            assertTrue(creature.isAlerted());
            assertEquals(position, creature.getPosition());
            assertEquals(target, creature.getTargetIndex());
            assertEquals(direction, creature.getDirection());
            assertEquals(wait, creature.getWaitRemaining());
        }
    }

    @Test
    void lastSeenChangesOnlyWhenBothRangeAndSightPermitAndNewCreatureResets() {
        WorldGeometry wall = new WorldGeometry(Arrays.asList(new StaticAabb(0.5, -1, 0.1, 2)));
        CreaturePatrolState creature = creature(wall, 3);
        creature.update(0, p(-1, 0));
        creature.update(1, p(1, 0));
        assertEquals(p(-1, 0), creature.getLastSeenHunterPosition());
        creature.update(1, FAR);
        assertEquals(p(-1, 0), creature.getLastSeenHunterPosition());
        creature.update(0, p(-2, 0));
        assertEquals(p(-2, 0), creature.getLastSeenHunterPosition());
        assertEquals(p(0, 0), creature.getPosition());
        assertTrue(creature.isAlerted());
        assertFalse(creature(wall, 3).isAlerted());
        assertNull(creature(wall, 3).getLastSeenHunterPosition());
    }

    @Test
    void invalidInputsLeaveAllStateIntactBeforeAndAfterAlert() {
        for (double radius : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> creature(WorldGeometry.empty(), radius));
        }
        for (boolean alert : new boolean[] {false, true}) {
            CreaturePatrolState creature = creature(WorldGeometry.empty(), 3);
            creature.update(0.75, FAR);
            if (alert) creature.update(0, p(-1, 0));
            WorldPosition position = creature.getPosition();
            WorldPosition seen = creature.getLastSeenHunterPosition();
            double wait = creature.getWaitRemaining();
            int target = creature.getTargetIndex();
            int direction = creature.getDirection();
            for (double delta : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY}) {
                assertThrows(IllegalArgumentException.class, () -> creature.update(delta, p(0, 0)));
            }
            for (WorldPosition invalid : Arrays.asList(null, p(Double.NaN, 0), p(0, Double.NaN),
                p(Double.POSITIVE_INFINITY, 0), p(0, Double.NEGATIVE_INFINITY),
                p(Double.MAX_VALUE, Double.MAX_VALUE))) {
                assertThrows(IllegalArgumentException.class, () -> creature.update(1, invalid));
            }
            assertEquals(position, creature.getPosition());
            assertEquals(seen, creature.getLastSeenHunterPosition());
            assertEquals(alert, creature.isAlerted());
            assertEquals(wait, creature.getWaitRemaining());
            assertEquals(target, creature.getTargetIndex());
            assertEquals(direction, creature.getDirection());
        }
    }

    @Test
    void discreteDetectionUsesPositionBeforePatrolAndMayDifferWithTimePartition() {
        CreaturePatrolState single = creature(WorldGeometry.empty(), 0.5);
        single.update(1.75, p(0, 2));
        assertFalse(single.isAlerted()); // Прибыл к охотнику, но проверка была до шага.
        assertEquals(p(0, 2), single.getPosition());
        single.update(0, p(0, 2));
        assertTrue(single.isAlerted());
        CreaturePatrolState split = creature(WorldGeometry.empty(), 0.5);
        for (int i = 0; i < 6; i++) split.update(0.25, p(0, 2));
        split.update(0.25, p(0, 2));
        assertTrue(split.isAlerted());
        assertEquals(p(0, 1.5), split.getPosition());
    }

    @Test
    void prototypeStartsSafelyAndSupportsHiddenAndOpenApproachesAcrossUpdates() {
        WorldGeometry geometry = new WorldGeometry(Arrays.asList(new StaticAabb(3, -3, 1, 6),
            new StaticAabb(-5, 2, 4, 1), new StaticAabb(-3, -4, 2, 2)));
        CreaturePatrolState creature = new CreaturePatrolState(Arrays.asList(p(5, 0), p(5, 1)),
            2, 0.3, 0.5, 3, geometry);
        creature.update(0, p(0, 0));
        assertFalse(creature.isAlerted());
        for (int i = 0; i < 100; i++) {
            creature.update(0.1, p(2.5, 0));
            assertFalse(creature.isAlerted());
            assertTrue(geometry.isCirclePositionValid(creature.getPosition(), creature.getRadius()));
        }
        WorldPosition before = creature.getPosition();
        creature.update(0.1, p(5, -1));
        assertTrue(creature.isAlerted());
        assertEquals(before, creature.getPosition());
    }

    private static CreaturePatrolState creature(WorldGeometry geometry, double detectionRadius) {
        return new CreaturePatrolState(Arrays.asList(p(0, 0), p(0, 2)), 2, 0.3, 0.5,
            detectionRadius, geometry);
    }

    private static WorldPosition p(double x, double y) {
        return new WorldPosition(x, y);
    }
}
