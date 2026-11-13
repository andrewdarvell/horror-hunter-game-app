package ru.darvell.game.hhg.domain.actor;

import ru.darvell.game.hhg.domain.world.StaticAabb;
import ru.darvell.game.hhg.domain.world.WorldGeometry;
import ru.darvell.game.hhg.domain.world.WorldPosition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CreaturePatrolStateTest {
    private static final WorldPosition A = new WorldPosition(0, 0);
    private static final WorldPosition B = new WorldPosition(2, 0);
    private static final WorldPosition C = new WorldPosition(2, 2);
    private static final WorldPosition DISTANT_HUNTER = new WorldPosition(1000, 1000);
    private static final double EPSILON = 1e-9;

    @Test
    void startsAtFirstWaypointWithFullPauseAndNextTarget() {
        CreaturePatrolState patrol = patrol(A, B, C);
        assertEquals(A, patrol.getPosition());
        assertEquals(0.5, patrol.getWaitRemaining());
        assertEquals(1, patrol.getTargetIndex());
        assertEquals(1, patrol.getDirection());
        assertEquals(0.3, patrol.getRadius());
        patrol.update(0, DISTANT_HUNTER);
        assertEquals(A, patrol.getPosition());
        assertEquals(0.5, patrol.getWaitRemaining());
    }

    @Test
    void visitsWaypointsInBothDirectionsAndRepeatsWithPauses() {
        CreaturePatrolState patrol = patrol(A, B, C);
        WorldPosition[] visits = {B, C, B, A, B, C, B, A};
        int[] targets = {2, 1, 0, 1, 2, 1, 0, 1};
        int[] directions = {1, -1, -1, 1, 1, -1, -1, 1};
        for (int i = 0; i < visits.length; i++) {
            patrol.update(1.5, DISTANT_HUNTER);
            assertEquals(visits[i], patrol.getPosition());
            assertEquals(0.5, patrol.getWaitRemaining());
            assertEquals(targets[i], patrol.getTargetIndex());
            assertEquals(directions[i], patrol.getDirection());

        }
    }

    @Test
    void twoPointRouteReversesWithoutOvershoot() {
        CreaturePatrolState patrol = patrol(A, B);
        patrol.update(1.5, DISTANT_HUNTER);
        assertEquals(B, patrol.getPosition());
        assertEquals(0, patrol.getTargetIndex());
        assertEquals(-1, patrol.getDirection());
        patrol.update(0.5, DISTANT_HUNTER);
        assertEquals(0, patrol.getWaitRemaining());
        assertEquals(B, patrol.getPosition());
        patrol.update(0.25, DISTANT_HUNTER);
        assertPosition(1.5, 0, patrol);
        patrol.update(0.75, DISTANT_HUNTER);
        assertEquals(A, patrol.getPosition());
        assertEquals(1, patrol.getDirection());
    }

    @Test
    void movesAtSameSpeedOnAxisAndDiagonalWithoutGridSnapping() {
        CreaturePatrolState axis = patrol(A, B);
        CreaturePatrolState diagonal = patrol(A, new WorldPosition(3, 4));
        axis.update(0.625, DISTANT_HUNTER);
        diagonal.update(0.625, DISTANT_HUNTER);
        assertPosition(0.25, 0, axis);
        assertPosition(0.15, 0.2, diagonal);
        assertEquals(0.25, Math.hypot(diagonal.getPosition().getX(), diagonal.getPosition().getY()), EPSILON);
    }

    @Test
    void consumesRemainderOnArrivalAndPauseExactly() {
        CreaturePatrolState patrol = patrol(A, B);
        patrol.update(1.25, DISTANT_HUNTER); // Начальная пауза и 0.75 s движения: до B ещё 0.25 s.
        patrol.update(0.4, DISTANT_HUNTER);
        assertEquals(B, patrol.getPosition());
        assertEquals(0.35, patrol.getWaitRemaining(), EPSILON);

        CreaturePatrolState continuing = patrol(A, B);
        continuing.update(1.25, DISTANT_HUNTER);
        continuing.update(1, DISTANT_HUNTER);
        assertPosition(1.5, 0, continuing);
        assertEquals(0, continuing.getWaitRemaining());
    }

    @Test
    void timePartitionsPreserveEntireStateAcrossTransitionsAndCycles() {
        for (double total : new double[] {0, 0.5, 1.5, 2, 3, 4.5, 6, 12, 12.25, 17.375}) {
            CreaturePatrolState single = patrol(A, B, C);
            CreaturePatrolState uniform = patrol(A, B, C);
            CreaturePatrolState uneven = patrol(A, B, C);
            single.update(total, DISTANT_HUNTER);
            // Точное двоичное разбиение отдельно проверяет семантику границ.
            int steps = (int) (total / 0.125);
            for (int i = 0; i < steps; i++) uniform.update(0.125, DISTANT_HUNTER);
            uniform.update(total - steps * 0.125, DISTANT_HUNTER);
            double remaining = total;
            double[] chunks = {0.0625, 0.375, 0.125, 0.75};
            int index = 0;
            while (remaining > 0) {
                double step = Math.min(remaining, chunks[index++ % chunks.length]);
                uneven.update(step, DISTANT_HUNTER);
                remaining -= step;
            }
            assertSameState(single, uniform);
            assertSameState(single, uneven);
        }
    }

    @Test
    void decimalTimeStepsDoNotDelayPhaseTransitionsByOneFrame() {
        for (int steps : new int[] {5, 15, 20, 30, 60, 120, 150}) {
            CreaturePatrolState single = patrol(A, B, C);
            CreaturePatrolState split = patrol(A, B, C);
            single.update(steps * 0.1, DISTANT_HUNTER);
            for (int i = 0; i < steps; i++) split.update(0.1, DISTANT_HUNTER);
            assertSameState(single, split);
        }
    }

    @Test
    void rejectsInvalidConfiguration() {
        List<WorldPosition> valid = Arrays.asList(A, B);
        assertThrows(IllegalArgumentException.class, () -> create(null, 2, 0.3, 0.5, WorldGeometry.empty()));
        assertThrows(IllegalArgumentException.class, () -> create(valid, 2, 0.3, 0.5, null));
        for (List<WorldPosition> invalid : Arrays.asList(Collections.<WorldPosition>emptyList(),
            Collections.singletonList(A), Arrays.asList(A, B, C, A), Arrays.asList(A, null),
            Arrays.asList(A, A), Arrays.asList(A, new WorldPosition(-0.0, 0)),
            Arrays.asList(A, new WorldPosition(Double.NaN, 0)),
            Arrays.asList(A, new WorldPosition(0, Double.POSITIVE_INFINITY)))) {
            assertThrows(IllegalArgumentException.class, () -> create(invalid, 2, 0.3, 0.5, WorldGeometry.empty()));
        }
        for (double invalid : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> create(valid, invalid, 0.3, 0.5, WorldGeometry.empty()));
            assertThrows(IllegalArgumentException.class, () -> create(valid, 2, invalid, 0.5, WorldGeometry.empty()));
            assertThrows(IllegalArgumentException.class, () -> create(valid, 2, 0.3, invalid, WorldGeometry.empty()));
        }
        assertThrows(IllegalArgumentException.class, () -> patrol(new WorldPosition(-Double.MAX_VALUE, 0),
            new WorldPosition(Double.MAX_VALUE, 0)));
        assertThrows(IllegalArgumentException.class, () -> create(valid, Double.MIN_VALUE, 0.3, 0.5, WorldGeometry.empty()));
        assertThrows(IllegalArgumentException.class, () -> create(Arrays.asList(A, new WorldPosition(Double.MIN_VALUE, 0)),
            Double.MAX_VALUE, 0.3, 0.5, WorldGeometry.empty()));
        // A и C могут совпадать, если соседние участки ненулевые.
        assertDoesNotThrow(() -> patrol(A, B, A));
    }

    @Test
    void rejectsUnsafeWaypointAndSegment() {
        WorldGeometry geometry = new WorldGeometry(Collections.singletonList(new StaticAabb(0, 0, 2, 2)));
        assertThrows(IllegalArgumentException.class, () -> create(Arrays.asList(new WorldPosition(1, 1), B), 2, 0.3, 0.5, geometry));
        assertThrows(IllegalArgumentException.class, () -> create(Arrays.asList(new WorldPosition(-2, 1),
            new WorldPosition(4, 1)), 2, 0.3, 0.5, geometry));
        assertThrows(IllegalArgumentException.class, () -> create(Arrays.asList(new WorldPosition(-2, -0.2),
            new WorldPosition(4, -0.2)), 2, 0.3, 0.5, geometry));
    }

    @Test
    void routeIsDefensivelyCopiedAndInvalidUpdateLeavesStateUnchanged() {
        List<WorldPosition> route = new ArrayList<>(Arrays.asList(A, B));
        CreaturePatrolState patrol = create(route, 2, 0.3, 0.5, WorldGeometry.empty());
        route.clear();
        patrol.update(1.5, DISTANT_HUNTER);
        assertEquals(B, patrol.getPosition());
        for (double invalid : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            CreaturePatrolState expected = patrol(A, B);
            expected.update(1.5, DISTANT_HUNTER);
            assertThrows(IllegalArgumentException.class, () -> patrol.update(invalid, DISTANT_HUNTER));
            assertSameState(expected, patrol);
        }
    }

    @Test
    void safeRouteNearObstacleStaysSafeWithLargeAndSmallSteps() {
        WorldGeometry geometry = new WorldGeometry(Collections.singletonList(new StaticAabb(0, 0, 2, 2)));
        List<WorldPosition> route = Arrays.asList(new WorldPosition(-2, -0.5), new WorldPosition(4, -0.5));
        CreaturePatrolState single = create(route, 2, 0.5, 0.5, geometry);
        CreaturePatrolState small = create(route, 2, 0.5, 0.5, geometry);
        single.update(23.25, DISTANT_HUNTER);
        for (int i = 0; i < 186; i++) {
            small.update(0.125, DISTANT_HUNTER);
            assertTrue(geometry.isCirclePositionValid(small.getPosition(), small.getRadius()));
            assertEquals(-0.5, small.getPosition().getY());
        }
        assertSameState(single, small);
    }

    @Test
    void prototypeRouteIsSafeOnExistingSceneGeometry() {
        WorldGeometry geometry = new WorldGeometry(Arrays.asList(
            new StaticAabb(3, -3, 1, 6), new StaticAabb(-5, 2, 4, 1), new StaticAabb(-3, -4, 2, 2)));
        List<WorldPosition> route = Arrays.asList(new WorldPosition(-4, 0), new WorldPosition(-2, 0), new WorldPosition(0, 1));
        CreaturePatrolState single = create(route, 2, 0.3, 0.5, geometry);
        CreaturePatrolState small = create(route, 2, 0.3, 0.5, geometry);
        single.update(30.125, DISTANT_HUNTER);
        for (int i = 0; i < 241; i++) {
            small.update(0.125, DISTANT_HUNTER);
            assertTrue(geometry.isCirclePositionValid(small.getPosition(), small.getRadius()));
        }
        assertSameState(single, small);
    }

    private static CreaturePatrolState patrol(WorldPosition... route) {
        return create(Arrays.asList(route), 2, 0.3, 0.5, WorldGeometry.empty());
    }

    private static CreaturePatrolState create(List<WorldPosition> route, double speed, double radius,
                                             double pause, WorldGeometry geometry) {
        return new CreaturePatrolState(route, speed, radius, pause, 3.0, geometry);
    }

    private static void assertPosition(double x, double y, CreaturePatrolState patrol) {
        assertEquals(x, patrol.getPosition().getX(), EPSILON);
        assertEquals(y, patrol.getPosition().getY(), EPSILON);
    }

    private static void assertSameState(CreaturePatrolState expected, CreaturePatrolState actual) {
        assertPosition(expected.getPosition().getX(), expected.getPosition().getY(), actual);
        assertEquals(expected.getWaitRemaining(), actual.getWaitRemaining(), EPSILON);
        assertEquals(expected.getWaitRemaining() > 0, actual.getWaitRemaining() > 0);
        assertEquals(expected.getTargetIndex(), actual.getTargetIndex());
        assertEquals(expected.getDirection(), actual.getDirection());
    }
}
