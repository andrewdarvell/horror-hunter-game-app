package ru.darvell.game.hhg.domain.world;

import java.util.ArrayList;
import java.util.List;

/** Ограниченный чистый расчёт: не более 18 кандидатов и 81 пары. */
public final class TacticalEntryPlanner {
    public TacticalEntryResult plan(WorldPosition hunter, double hunterRadius,
                                    WorldPosition creature, double creatureRadius,
                                    TacticalGrid grid, WorldGeometry geometry) {
        if (grid == null || geometry == null || !valid(hunter, hunterRadius, geometry)
            || !valid(creature, creatureRadius, geometry)) {
            return TacticalEntryResult.failure(TacticalEntryResult.Outcome.INVALID_INPUT);
        }
        CellCoordinates hunterSource = checkedCell(hunter, grid);
        CellCoordinates creatureSource = checkedCell(creature, grid);
        if (hunterSource == null || creatureSource == null) {
            return TacticalEntryResult.failure(TacticalEntryResult.Outcome.INVALID_INPUT);
        }
        return select(candidates(hunter, hunterRadius, hunterSource, grid, geometry),
            candidates(creature, creatureRadius, creatureSource, grid, geometry));
    }

    private static boolean valid(WorldPosition p, double radius, WorldGeometry geometry) {
        return p != null && Double.isFinite(p.getX()) && Double.isFinite(p.getY())
            && Double.isFinite(radius) && radius > 0.0 && geometry.isCirclePositionValid(p, radius);
    }

    private static CellCoordinates checkedCell(WorldPosition p, TacticalGrid grid) {
        double x = Math.floor((p.getX() - grid.getOriginX()) / grid.getCellSize());
        double y = Math.floor((p.getY() - grid.getOriginY()) / grid.getCellSize());
        if (!inRange(x) || !inRange(y)) return null;
        CellCoordinates cell = grid.worldToCell(p);
        return cell.getX() == x && cell.getY() == y ? cell : null;
    }

    private static boolean inRange(double value) {
        return Double.isFinite(value) && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE;
    }

    static List<Candidate> candidates(WorldPosition from, double radius, CellCoordinates source,
                                      TacticalGrid grid, WorldGeometry geometry) {
        List<Candidate> result = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                long x = (long) source.getX() + dx;
                long y = (long) source.getY() + dy;
                if (!inRange(x) || !inRange(y)) continue;
                CellCoordinates cell = new CellCoordinates((int) x, (int) y);
                WorldPosition center = grid.cellToWorldCenter(cell);
                if (!cell.equals(checkedCell(center, grid))) continue;
                double distance = Math.hypot(center.getX() - from.getX(), center.getY() - from.getY());
                double scaledDistance = distance / grid.getCellSize();
                if (!Double.isFinite(distance) || !Double.isFinite(scaledDistance)) continue;
                if (grid.isCellPassable(cell, radius, geometry)
                    && geometry.isCirclePathValid(from, center, radius)) {
                    result.add(new Candidate(cell, center, scaledDistance, !cell.equals(source)));
                }
            }
        }
        return result;
    }

    static TacticalEntryResult select(List<Candidate> hunters, List<Candidate> creatures) {
        Candidate bestHunter = null;
        Candidate bestCreature = null;
        for (Candidate hunter : hunters) {
            for (Candidate creature : creatures) {
                if (hunter.cell.equals(creature.cell)
                    || !Double.isFinite(hunter.distance + creature.distance)) continue;
                if (bestHunter == null || compare(hunter, creature, bestHunter, bestCreature) < 0) {
                    bestHunter = hunter;
                    bestCreature = creature;
                }
            }
        }
        if (bestHunter == null) {
            return TacticalEntryResult.failure(TacticalEntryResult.Outcome.NO_VALID_PLACEMENT);
        }
        return TacticalEntryResult.success(new TacticalPlacement(bestHunter.cell, bestCreature.cell),
            bestHunter.center, bestCreature.center);
    }

    static int compare(Candidate h, Candidate c, Candidate bh, Candidate bc) {
        int result = Integer.compare(h.moved + c.moved, bh.moved + bc.moved);
        if (result == 0) result = Double.compare(h.distance + c.distance, bh.distance + bc.distance);
        if (result == 0) result = Double.compare(h.distance, bh.distance);
        if (result == 0) result = Integer.compare(h.cell.getX(), bh.cell.getX());
        if (result == 0) result = Integer.compare(h.cell.getY(), bh.cell.getY());
        if (result == 0) result = Integer.compare(c.cell.getX(), bc.cell.getX());
        if (result == 0) result = Integer.compare(c.cell.getY(), bc.cell.getY());
        return result;
    }

    static final class Candidate {
        final CellCoordinates cell;
        final WorldPosition center;
        final double distance;
        final int moved;

        Candidate(CellCoordinates cell, WorldPosition center, double distance, boolean moved) {
            this.cell = cell;
            this.center = center;
            this.distance = distance;
            this.moved = moved ? 1 : 0;
        }
    }
}
