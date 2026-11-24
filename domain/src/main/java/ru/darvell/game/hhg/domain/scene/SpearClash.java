package ru.darvell.game.hhg.domain.scene;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.function.DoubleSupplier;
import lombok.Value;
import ru.darvell.game.hhg.domain.actor.CreaturePatrolState;
import ru.darvell.game.hhg.domain.actor.HunterState;
import ru.darvell.game.hhg.domain.combat.outcome.OutcomeTable;
import ru.darvell.game.hhg.domain.combat.outcome.WeightedOutcomeResolver;
import ru.darvell.game.hhg.domain.tactical.CellCoordinates;
import ru.darvell.game.hhg.domain.tactical.Facing;
import ru.darvell.game.hhg.domain.tactical.SpearClashState.Result;
import ru.darvell.game.hhg.domain.tactical.TacticalGrid;
import ru.darvell.game.hhg.domain.world.WorldGeometry;
import ru.darvell.game.hhg.domain.world.WorldPosition;

import static ru.darvell.game.hhg.domain.tactical.SpearClashConfig.HALF_ANGLE_DEGREES;
import static ru.darvell.game.hhg.domain.tactical.SpearClashConfig.MAX_REACH_CELLS;
import static ru.darvell.game.hhg.domain.tactical.SpearClashConfig.MIN_REACH_CELLS;
import static ru.darvell.game.hhg.domain.tactical.SpearClashState.Result.*;

/** Geometry and consequences of a wolf leap. The scene owns the clock and the commit. */
final class SpearClash {
    @Value
    static class Plan {
        WorldPosition start;
        WorldPosition target;
        CellCoordinates targetCell;
        CellCoordinates contact;
        Facing lane;
        boolean lunge;
    }

    @Value
    static class Impact {
        Result result;
        List<WorldPosition> path;
        int contactIndex;
        int hunterDamage;
        int wolfDamage;
        long hunterRecovery;
        long wolfRecovery;
    }

    private final TacticalGrid grid;
    private final WorldGeometry geometry;

    SpearClash(TacticalGrid grid, WorldGeometry geometry) {
        this.grid = grid;
        this.geometry = geometry;
    }

    Plan plan(WorldPosition hunter, WorldPosition wolf) {
        CellCoordinates home = grid.worldToCell(hunter);
        CellCoordinates start = grid.worldToCell(wolf);
        long dx = (long) start.getX() - home.getX();
        long dy = (long) start.getY() - home.getY();
        long steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps < 1 || steps > 3 || (dx != 0 && dy != 0 && Math.abs(dx) != Math.abs(dy))
            || !hunter.equals(grid.cellToWorldCenter(home)) || !wolf.equals(grid.cellToWorldCenter(start))) {
            return null;
        }
        Facing lane = Facing.along(Long.signum(dx), Long.signum(dy));
        if (steps == 1) return new Plan(wolf, hunter, home, start, lane, true);
        double laneLength = Math.hypot(lane.getDx(), lane.getDy());
        int contactSteps = Math.min((int) steps - 1, (int) (MAX_REACH_CELLS / laneLength));
        if (contactSteps == 0) return null;
        CellCoordinates contact = shift(home, lane.getDx() * contactSteps, lane.getDy() * contactSteps);
        return new Plan(wolf, hunter, home, contact, lane, false);
    }

    boolean canLaunch(Plan plan, HunterState hunter, CreaturePatrolState wolf, Facing facing, boolean braced) {
        if (plan == null || !plan.start.equals(wolf.getPosition()) || !plan.target.equals(hunter.getPosition())) return false;
        List<WorldPosition> approach = route(Collections.singletonList(plan.start), hunter, wolf, plan.contact);
        if (approach == null || !geometry.hasLineOfSight(plan.target, last(approach))) return false;
        if (braced && !plan.lunge && covers(facing, plan.lane)) return true;
        List<WorldPosition> close = route(approach, hunter, wolf, near(plan));
        return close != null && geometry.hasLineOfSight(plan.target, last(close));
    }

    WorldPosition stepTarget(HunterState hunter, CreaturePatrolState wolf, Facing facing, boolean flank) {
        WorldPosition start = wolf.getPosition();
        WorldPosition target = hunter.getPosition();
        if (flank && !facesPosition(facing, target, start)) return null;
        CellCoordinates from = grid.worldToCell(start);
        WorldPosition chosen = null;
        double best = distance(start, target);
        for (Facing direction : Facing.values()) {
            CellCoordinates cell = shift(from, direction.getDx(), direction.getDy());
            List<WorldPosition> path = route(Collections.singletonList(start), hunter, wolf, cell);
            if (path == null) continue;
            WorldPosition end = last(path);
            if (flank) {
                Plan leap = plan(target, end);
                if (leap != null && !leap.lunge && !covers(facing, leap.lane)
                    && canLaunch(leap, hunter, wolf.withTacticalPosition(end), facing, false)) return end;
            } else {
                double distance = distance(end, target);
                if (distance < best) {
                    best = distance;
                    chosen = end;
                }
            }
        }
        return chosen;
    }

    boolean canStep(WorldPosition from, WorldPosition target, HunterState hunter, CreaturePatrolState wolf) {
        return from.equals(wolf.getPosition()) && route(Collections.singletonList(from), hunter, wolf,
            grid.worldToCell(target)) != null;
    }

    static boolean covers(Facing facing, Facing lane) {
        return facing.stepsTo(lane) * 45 <= HALF_ANGLE_DEGREES;
    }

    private static boolean facesPosition(Facing facing, WorldPosition from, WorldPosition to) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dot = facing.getDx() * dx + facing.getDy() * dy;
        return dot > 0 && Math.abs(facing.getDx() * dy - facing.getDy() * dx) <= dot;
    }

    private static double distance(WorldPosition a, WorldPosition b) {
        return Math.hypot(a.getX() - b.getX(), a.getY() - b.getY());
    }

    private static CellCoordinates near(Plan plan) {
        return shift(plan.targetCell, plan.lane.getDx(), plan.lane.getDy());
    }

    Impact resolve(Plan plan, HunterState hunter, CreaturePatrolState wolf, Facing facing, boolean braced,
                   OutcomeTable weights, DoubleSupplier random) {
        WorldPosition start = wolf.getPosition();
        if (!start.equals(plan.start)) return stopped(BLOCKED, start, 0);
        if (plan.lunge) {
            boolean hit = hunter.getPosition().equals(plan.target) && geometry.hasLineOfSight(start, plan.target);
            return unprotected(hit, Collections.singletonList(start));
        }
        List<WorldPosition> approach = route(Collections.singletonList(start), hunter, wolf, plan.contact);
        if (approach == null) return stopped(BLOCKED, start, 0);

        Facing lane = plan.lane;
        CellCoordinates near = near(plan);
        List<WorldPosition> close = route(approach, hunter, wolf, near);
        boolean targetPresent = hunter.getPosition().equals(plan.target);
        WorldPosition contact = last(approach);
        double reach = Math.hypot((long) plan.contact.getX() - plan.targetCell.getX(),
            (long) plan.contact.getY() - plan.targetCell.getY());
        if (!braced || !targetPresent || !covers(facing, lane)
            || reach < MIN_REACH_CELLS || !geometry.hasLineOfSight(plan.target, contact)) {
            if (close == null) return stopped(BLOCKED, start, 0);
            boolean hit = targetPresent && geometry.hasLineOfSight(last(close), hunter.getPosition());
            return unprotected(hit, close);
        }

        EnumMap<Result, Impact> legal = new EnumMap<>(Result.class);
        List<WorldPosition> rebound = route(approach, hunter, wolf,
            shift(plan.contact, lane.getDx(), lane.getDy()));
        if (rebound != null) legal.put(REPELLED, new Impact(REPELLED, rebound, approach.size() - 1, 0, 1, 2, 6));
        if (close != null) {
            legal.put(BROKE_THROUGH, new Impact(BROKE_THROUGH, close, approach.size() - 1, 0, 0, 6, 2));
            List<WorldPosition> bypass = bypass(plan, near, facing, close, hunter, wolf, 1);
            if (bypass == null) bypass = bypass(plan, near, facing, close, hunter, wolf, -1);
            if (bypass != null) legal.put(GRAZED_PAST, new Impact(GRAZED_PAST, bypass, approach.size() - 1, 0, 1, 4, 4));
        }

        OutcomeTable allowed = weights.filter(id -> legal.containsKey(Result.valueOf(id)));
        if (allowed.getOutcomes().stream().noneMatch(outcome -> outcome.getWeight() > 0)) {
            return new Impact(STOPPED, approach, approach.size() - 1, 0, 0, 2, 4);
        }
        return legal.get(Result.valueOf(WeightedOutcomeResolver.resolve(allowed, random)));
    }

    private List<WorldPosition> bypass(Plan plan, CellCoordinates near, Facing facing,
            List<WorldPosition> close, HunterState hunter, CreaturePatrolState wolf, int side) {
        int dx = plan.lane.getDx();
        int dy = plan.lane.getDy();
        int px = -dy * side;
        int py = dx * side;
        int endX = -dx + px;
        int endY = -dy + py;
        if (endX * facing.getDx() + endY * facing.getDy() >= 0) return null;
        return route(close, hunter, wolf, shift(near, px, py), shift(plan.targetCell, px, py),
            shift(plan.targetCell, endX, endY));
    }

    private List<WorldPosition> route(List<WorldPosition> prefix, HunterState hunter, CreaturePatrolState wolf,
                                      CellCoordinates... cells) {
        ArrayList<WorldPosition> path = new ArrayList<>(prefix);
        WorldPosition body = hunter.getPosition();
        CellCoordinates occupied = grid.worldToCell(body);
        // Nodes are cell centers. The only non-axis segments are diagonal lattice steps or
        // collinear multiples of those steps, so occupied interiors occur on that same line.
        for (CellCoordinates cell : cells) {
            if (cell == null) return null;
            WorldPosition next = grid.cellToWorldCenter(cell);
            if (!Double.isFinite(next.getX()) || !Double.isFinite(next.getY())
                || !cell.equals(grid.worldToCell(next))) return null;
            WorldPosition previous = last(path);
            if (!Double.isFinite(Math.hypot(next.getX() - previous.getX(), next.getY() - previous.getY()))
                || throughCell(grid.worldToCell(previous), cell, occupied)
                || !geometry.isCirclePathValid(previous, next, wolf.getRadius(), body, hunter.getRadius())) {
                return null;
            }
            if (!next.equals(previous)) path.add(next);
        }
        return Collections.unmodifiableList(path);
    }

    private static boolean throughCell(CellCoordinates from, CellCoordinates to, CellCoordinates occupied) {
        long dx = (long) to.getX() - from.getX();
        long dy = (long) to.getY() - from.getY();
        long ox = (long) occupied.getX() - from.getX();
        long oy = (long) occupied.getY() - from.getY();
        return ox >= Math.min(0, dx) && ox <= Math.max(0, dx)
            && oy >= Math.min(0, dy) && oy <= Math.max(0, dy) && dx * oy == dy * ox;
    }

    static CellCoordinates shift(CellCoordinates cell, int dx, int dy) {
        long x = (long) cell.getX() + dx;
        long y = (long) cell.getY() + dy;
        return x < Integer.MIN_VALUE || x > Integer.MAX_VALUE || y < Integer.MIN_VALUE || y > Integer.MAX_VALUE
            ? null : new CellCoordinates((int) x, (int) y);
    }

    static WorldPosition last(List<WorldPosition> path) { return path.get(path.size() - 1); }

    private static Impact unprotected(boolean hit, List<WorldPosition> path) {
        return hit ? new Impact(HIT, path, path.size() - 1, 1, 0, 2, 4) : new Impact(MISS, path, path.size() - 1, 0, 0, 0, 4);
    }

    private static Impact stopped(Result result, WorldPosition at, long hunterRecovery) {
        return new Impact(result, Collections.singletonList(at), 0, 0, 0, hunterRecovery, 4);
    }

    static double sample(long seed, long cursor) {
        long bits = seed + 0x9e3779b97f4a7c15L * (cursor + 1);
        bits = (bits ^ (bits >>> 30)) * 0xbf58476d1ce4e5b9L;
        bits = (bits ^ (bits >>> 27)) * 0x94d049bb133111ebL;
        bits ^= bits >>> 31;
        return (bits >>> 11) * 0x1.0p-53;
    }
}
