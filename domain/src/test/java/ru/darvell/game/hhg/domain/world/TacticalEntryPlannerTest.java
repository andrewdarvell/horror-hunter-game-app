package ru.darvell.game.hhg.domain.world;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TacticalEntryPlannerTest {
    private final TacticalGrid grid = new TacticalGrid(0, 0, 2);
    private final TacticalEntryPlanner planner = new TacticalEntryPlanner();

    @Test
    void preservesDistinctSourceCellsIncludingNegativeBoundaries() {
        for (double x : new double[] {-2.001, -2, -0.001, 0, 1.999, 2}) {
            WorldPosition h = new WorldPosition(x, 0.2);
            TacticalEntryResult result = plan(h, 0.1, new WorldPosition(9, 3), 0.4, WorldGeometry.empty());
            assertEquals(TacticalEntryResult.Outcome.SUCCESS, result.getOutcome());
            assertEquals(grid.worldToCell(h), result.getPlacement().getHunterCell());
            assertEquals(grid.cellToWorldCenter(result.getPlacement().getHunterCell()), result.getHunterCenter());
            assertEquals(new WorldPosition(9, 3), result.getCreatureCenter());
            assertNotEquals(result.getPlacement().getHunterCell(), result.getPlacement().getCreatureCell());
        }
    }

    @Test
    void rejectsBlockedCenterAndPathsAcrossThinWallEvenInsideSourceCell() {
        WorldPosition from = new WorldPosition(0.2, 1);
        WorldGeometry wall = geometry(new StaticAabb(0.5, -4, 0.01, 10));
        assertTrue(grid.isCellPassable(new CellCoordinates(0, 0), 0.1, wall));
        List<TacticalEntryPlanner.Candidate> candidates = candidates(from, 0.1, wall);
        assertFalse(candidates.stream().anyMatch(c -> c.cell.getX() >= 0));
        TacticalEntryResult result = plan(from, 0.1, new WorldPosition(-5, 1), 0.1, wall);
        assertEquals(new CellCoordinates(-1, 0), result.getPlacement().getHunterCell());

        WorldGeometry blockedCenter = geometry(new StaticAabb(0.9, 0.9, 0.2, 0.2));
        assertFalse(candidates(new WorldPosition(0.2, 0.2), 0.1, blockedCenter).stream()
            .anyMatch(c -> c.cell.equals(new CellCoordinates(0, 0))));
    }

    @Test
    void checksRadiusAlongPathAndAllowsTangencyAndZeroDistance() {
        WorldPosition from = new WorldPosition(-1, 1);
        CellCoordinates target = new CellCoordinates(0, 0);
        WorldGeometry wall = geometry(new StaticAabb(-0.1, 1.25, 0.2, 0.1));
        assertTrue(wall.hasLineOfSight(from, new WorldPosition(1, 1)));
        assertTrue(candidates(from, 0.25, wall).stream().anyMatch(c -> c.cell.equals(target)));
        assertFalse(candidates(from, 0.251, wall).stream().anyMatch(c -> c.cell.equals(target)));
        assertTrue(candidates(from, 0.25, wall).stream().anyMatch(c -> c.center.equals(from)));
    }

    @Test
    void jointlyPlacesDifferentRadiiWithoutGreedyDeadEnd() {
        List<StaticAabb> boxes = blockedCentersExcept(new CellCoordinates(0, 0), new CellCoordinates(-1, 0));
        boxes.add(new StaticAabb(-1.05, 1.3, 0.1, 0.1));
        TacticalEntryResult result = plan(new WorldPosition(1, 1), 0.1,
            new WorldPosition(1, 1), 0.4, new WorldGeometry(boxes));
        assertEquals(TacticalEntryResult.Outcome.SUCCESS, result.getOutcome());
        assertEquals(new CellCoordinates(-1, 0), result.getPlacement().getHunterCell());
        assertEquals(new CellCoordinates(0, 0), result.getPlacement().getCreatureCell());
    }

    @Test
    void keepsHunterOnEqualDisplacementAndUsesSignedCoordinateTieBreak() {
        TacticalEntryResult result = plan(new WorldPosition(1, 1), 0.1,
            new WorldPosition(1, 1), 0.1, WorldGeometry.empty());
        assertEquals(new CellCoordinates(0, 0), result.getPlacement().getHunterCell());
        assertEquals(new CellCoordinates(-1, 0), result.getPlacement().getCreatureCell());
    }

    @Test
    void selectionPrioritiesAreIndependentOfEnumeration() {
        // Проверка политики на явных расстояниях, отдельно от геометрии кандидатов.
        assertSelected(candidate(0, 0, 100, false), candidate(1, 0, 0, true),
            candidate(9, 0, 0, true), candidate(2, 0, 0, true));
        assertSelected(candidate(0, 0, 1, true), candidate(1, 0, 1, true),
            candidate(9, 0, 3, true), candidate(2, 0, 3, true));
        assertSelected(candidate(0, 0, 1, true), candidate(1, 0, 3, true),
            candidate(9, 0, 2, true), candidate(2, 0, 2, true));
        for (int axis = 0; axis < 4; axis++) {
            TacticalEntryPlanner.Candidate h = candidate(-2, -2, 1, true);
            TacticalEntryPlanner.Candidate c = candidate(2, 2, 1, true);
            TacticalEntryPlanner.Candidate bh = candidate(axis == 0 ? -1 : -2, axis == 1 ? -1 : -2, 1, true);
            TacticalEntryPlanner.Candidate bc = candidate(axis == 2 ? 3 : 2, axis == 3 ? 3 : 2, 1, true);
            assertSelected(h, c, bh, bc);
        }
    }

    private void assertSelected(TacticalEntryPlanner.Candidate h, TacticalEntryPlanner.Candidate c,
                                TacticalEntryPlanner.Candidate otherH, TacticalEntryPlanner.Candidate otherC) {
        List<TacticalEntryPlanner.Candidate> hs = new ArrayList<>(Arrays.asList(h, otherH));
        List<TacticalEntryPlanner.Candidate> cs = new ArrayList<>(Arrays.asList(c, otherC));
        assertTrue(TacticalEntryPlanner.compare(h, c, otherH, otherC) < 0);
        assertTrue(TacticalEntryPlanner.compare(otherH, otherC, h, c) > 0);
        // Общие списки допускают перекрёстные пары: минимум должен включать выбранного охотника.
        TacticalEntryResult a = TacticalEntryPlanner.select(hs, cs);
        Collections.reverse(hs);
        Collections.reverse(cs);
        TacticalEntryResult b = TacticalEntryPlanner.select(hs, cs);
        assertEquals(h.cell, a.getPlacement().getHunterCell());
        assertEquals(a.getPlacement().getHunterCell(), b.getPlacement().getHunterCell());
        assertEquals(a.getPlacement().getCreatureCell(), b.getPlacement().getCreatureCell());
    }

    @Test
    void refusesSingleSharedCellOrMissingCandidatesWithoutExpandingSearch() {
        WorldPosition p = new WorldPosition(1, 1);
        WorldGeometry single = new WorldGeometry(blockedCentersExcept(new CellCoordinates(0, 0)));
        assertEquals(TacticalEntryResult.Outcome.NO_VALID_PLACEMENT, plan(p, 0.1, p, 0.1, single).getOutcome());
        assertTrue(single.isCirclePositionValid(new WorldPosition(5, 1), 0.1));
        WorldGeometry none = new WorldGeometry(blockedCentersExcept());
        assertEquals(TacticalEntryResult.Outcome.NO_VALID_PLACEMENT,
            plan(new WorldPosition(0.2, 0.2), 0.05, new WorldPosition(9, 1), 0.1, none).getOutcome());
    }

    @Test
    void rejectsInvalidSnapshotsBeforeIntegerSaturation() {
        WorldPosition good = new WorldPosition(1, 1);
        for (WorldPosition bad : Arrays.asList(null, new WorldPosition(Double.NaN, 1),
            new WorldPosition(1, Double.POSITIVE_INFINITY), new WorldPosition(Double.MAX_VALUE, 0))) {
            assertEquals(TacticalEntryResult.Outcome.INVALID_INPUT, plan(bad, 0.1, good, 0.1, WorldGeometry.empty()).getOutcome());
            assertEquals(TacticalEntryResult.Outcome.INVALID_INPUT, plan(good, 0.1, bad, 0.1, WorldGeometry.empty()).getOutcome());
        }
        for (double radius : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertEquals(TacticalEntryResult.Outcome.INVALID_INPUT, plan(good, radius, good, 0.1, WorldGeometry.empty()).getOutcome());
        }
        assertEquals(TacticalEntryResult.Outcome.INVALID_INPUT, planner.plan(good, 0.1, good, 0.1, null, WorldGeometry.empty()).getOutcome());
        assertEquals(TacticalEntryResult.Outcome.INVALID_INPUT, plan(good, 0.1, good, 0.1, null).getOutcome());
        assertEquals(TacticalEntryResult.Outcome.INVALID_INPUT, plan(good, 0.1, good, 0.1,
            geometry(new StaticAabb(0, 0, 2, 2))).getOutcome());
    }

    @Test
    void checksBothIntegerLimitsAndUnstableCenters() {
        for (int limit : new int[] {Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            WorldPosition p = grid.cellToWorldCenter(new CellCoordinates(limit, 0));
            TacticalEntryResult result = plan(p, 0.1, new WorldPosition(1, 1), 0.1, WorldGeometry.empty());
            assertEquals(TacticalEntryResult.Outcome.SUCCESS, result.getOutcome());
            assertEquals(limit, result.getPlacement().getHunterCell().getX());
            assertTrue(candidates(p, 0.1, WorldGeometry.empty()).stream()
                .allMatch(c -> Math.abs((long) c.cell.getX() - limit) <= 1));
        }
        TacticalGrid unstable = new TacticalGrid(1e20, 1e20, 1);
        assertEquals(TacticalEntryResult.Outcome.NO_VALID_PLACEMENT, planner.plan(
            new WorldPosition(1e20, 1e20), 0.1, new WorldPosition(1e20, 1e20), 0.1,
            unstable, WorldGeometry.empty()).getOutcome());
        TacticalGrid overflowing = new TacticalGrid(0, 0, Double.MAX_VALUE);
        assertEquals(TacticalEntryResult.Outcome.NO_VALID_PLACEMENT, planner.plan(
            new WorldPosition(Double.MAX_VALUE, Double.MAX_VALUE), 0.1,
            new WorldPosition(Double.MAX_VALUE, Double.MAX_VALUE), 0.1, overflowing, WorldGeometry.empty()).getOutcome());
    }

    static List<StaticAabb> blockedCentersExcept(CellCoordinates... allowed) {
        List<StaticAabb> result = new ArrayList<>();
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) {
            if (!Arrays.asList(allowed).contains(new CellCoordinates(x, y))) {
                result.add(new StaticAabb(x * 2 + 0.9, y * 2 + 0.9, 0.2, 0.2));
            }
        }
        return result;
    }

    private TacticalEntryResult plan(WorldPosition h, double hr, WorldPosition c, double cr, WorldGeometry g) {
        return planner.plan(h, hr, c, cr, grid, g);
    }

    private List<TacticalEntryPlanner.Candidate> candidates(WorldPosition p, double radius, WorldGeometry g) {
        return TacticalEntryPlanner.candidates(p, radius, grid.worldToCell(p), grid, g);
    }

    private static WorldGeometry geometry(StaticAabb box) { return new WorldGeometry(Collections.singletonList(box)); }

    private static TacticalEntryPlanner.Candidate candidate(int x, int y, double distance, boolean moved) {
        return new TacticalEntryPlanner.Candidate(new CellCoordinates(x, y), new WorldPosition(x, y), distance, moved);
    }
}
