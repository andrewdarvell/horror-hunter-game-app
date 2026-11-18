package ru.darvell.game.hhg.domain.scene;

import ru.darvell.game.hhg.domain.actor.CreaturePatrolState;
import ru.darvell.game.hhg.domain.actor.CreaturePatrolState.Behavior;
import ru.darvell.game.hhg.domain.actor.HunterState;
import ru.darvell.game.hhg.domain.tactical.CellCoordinates;
import ru.darvell.game.hhg.domain.tactical.TacticalExitResult;
import ru.darvell.game.hhg.domain.tactical.TacticalAttackResult;
import ru.darvell.game.hhg.domain.tactical.TacticalAttackResult.Outcome;
import ru.darvell.game.hhg.domain.tactical.TacticalAttackResult.Kind;
import ru.darvell.game.hhg.domain.tactical.TacticalAttackResult.RejectionReason;
import ru.darvell.game.hhg.domain.tactical.TacticalAttackResult.Event;
import ru.darvell.game.hhg.domain.tactical.TacticalAttackResult.EventType;
import ru.darvell.game.hhg.domain.tactical.PrototypeBiteState;
import ru.darvell.game.hhg.domain.tactical.PrototypeBiteState.Phase;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import ru.darvell.game.hhg.domain.tactical.TacticalMoveResult;
import ru.darvell.game.hhg.domain.tactical.TacticalEntryPlanner;
import ru.darvell.game.hhg.domain.tactical.TacticalEntryResult;
import ru.darvell.game.hhg.domain.tactical.TacticalGrid;
import ru.darvell.game.hhg.domain.tactical.TacticalPlacement;
import ru.darvell.game.hhg.domain.world.WorldGeometry;
import ru.darvell.game.hhg.domain.world.WorldPosition;

/** Владеет независимыми копиями участников; наружу выдаёт только значения состояния. */
public final class WorldSceneState {
    private static final double EXPLORATION_STEP_SECONDS = 0.01;
    private static final double STEP_ROUNDING_SECONDS = 1e-12;

    private HunterState hunter;
    private CreaturePatrolState creature;
    private final TacticalGrid grid;
    private final WorldGeometry geometry;
    private final TacticalEntryPlanner planner = new TacticalEntryPlanner();
    private boolean detectionOnPreviousCheck;
    private boolean engagementOnPreviousCheck;
    private boolean initialObservationDone;
    private double explorationRemainder;
    private double pendingDirectionX;
    private double pendingDirectionY;
    private WorldPosition hunterBeforeEntry;
    private WorldPosition creatureBeforeEntry;
    private SceneMode mode = SceneMode.EXPLORATION;
    private TacticalPlacement placement;
    private long tacticalTimeUnits;
    private TacticalAttackResult lastResolution;
    private PrototypeBiteState biteState;

    public PrototypeBiteState getBiteState() { return biteState; }

    public WorldSceneState(HunterState hunter, CreaturePatrolState creature,
                           TacticalGrid grid, WorldGeometry geometry) {
        if (hunter == null || creature == null || grid == null || geometry == null) {
            throw new IllegalArgumentException("Scene components must be present");
        }
        this.hunter = hunter.copy();
        this.creature = creature.copy();
        this.grid = grid;
        this.geometry = geometry;
    }

    /** null означает отсутствие запроса в этом обновлении. */
    public TacticalEntryResult.Outcome update(double x, double y, double delta, boolean manualEntry) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(delta) || delta < 0.0) {
            throw new IllegalArgumentException("Direction and non-negative time must be finite");
        }
        if (mode == SceneMode.TACTICAL) {
            return manualEntry ? TacticalEntryResult.Outcome.ALREADY_TACTICAL : null;
        }
        double total = explorationRemainder + delta;
        double stepCount = (total + STEP_ROUNDING_SECONDS) / EXPLORATION_STEP_SECONDS;
        if (stepCount >= Long.MAX_VALUE) {
            throw new IllegalArgumentException("Exploration interval has too many steps");
        }
        // Учитываем округление на границе шага, включая суммирование частей из разных кадров.
        long steps = delta == 0.0 ? 0 : (long) stepCount;

        TacticalEntryResult.Outcome outcome = null;
        if (!initialObservationDone) {
            initialObservationDone = true;
            if (observeEngagementEdge()) {
                outcome = enterTactical();
                manualEntry = false;
                if (mode == SceneMode.TACTICAL) return outcome;
            }
        }

        for (long i = 0; i < steps; i++) {
            boolean pending = i == 0 && explorationRemainder > 0.0;
            hunter.move(pending ? pendingDirectionX : x, pending ? pendingDirectionY : y,
                EXPLORATION_STEP_SECONDS, geometry, creature.isDefeated() ? null : creature.getPosition(),
                creature.getRadius());

            if (observeEngagementEdge() || manualEntry) {
                outcome = enterTactical();
                manualEntry = false;
                if (mode == SceneMode.TACTICAL) return outcome;
            }

            creature.advanceExploration(EXPLORATION_STEP_SECONDS,
                hunter.isDefeated() ? null : hunter.getPosition(), hunter.getRadius());
            if (observeEngagementEdge()) {
                outcome = enterTactical();
                if (mode == SceneMode.TACTICAL) return outcome;
            }
            creature.finishExplorationStep();
        }
        if (manualEntry) {
            outcome = enterTactical();
            if (mode == SceneMode.TACTICAL) return outcome;
        }
        if (delta > 0.0) {
            if (steps > 0 || explorationRemainder == 0.0) {
                pendingDirectionX = x;
                pendingDirectionY = y;
            }
            double remainder = total - steps * EXPLORATION_STEP_SECONDS;
            explorationRemainder = steps > 0 && remainder <= STEP_ROUNDING_SECONDS ? 0.0 : remainder;
        }
        return outcome;
    }

    private boolean observeEngagementEdge() {
        detectionOnPreviousCheck = creature.observeHunter(hunter.getPosition(), !hunter.isDefeated());
        boolean engagement = canEngage(hunter, creature, detectionOnPreviousCheck);
        boolean newEngagement = engagement && !engagementOnPreviousCheck;
        engagementOnPreviousCheck = engagement;
        return newEngagement;
    }

    private static boolean canEngage(HunterState h, CreaturePatrolState c, boolean detected) {
        if (!detected) return false;
        WorldPosition from = c.getPosition();
        WorldPosition to = h.getPosition();
        double distance = Math.hypot(to.getX() - from.getX(), to.getY() - from.getY());
        return distance >= h.getRadius() + c.getRadius()
            && distance <= c.getBehaviorConfig().getEngagementRange();
    }

    public TacticalEntryResult.Outcome enterTactical() {
        if (mode == SceneMode.TACTICAL) return TacticalEntryResult.Outcome.ALREADY_TACTICAL;
        TacticalEntryResult result = creature.isDefeated()
            ? planner.planHunterOnly(hunter.getPosition(), hunter.getRadius(), grid, geometry)
            : planner.plan(hunter.getPosition(), hunter.getRadius(),
            creature.getPosition(), creature.getRadius(), grid, geometry);
        if (result.getOutcome() == TacticalEntryResult.Outcome.SUCCESS) {
            HunterState preparedHunter = hunter.withTacticalPosition(result.getHunterCenter());
            CreaturePatrolState preparedCreature = result.creatureCenter()
                .map(creature::withTacticalPosition).orElse(creature);
            TacticalPlacement preparedPlacement = result.getPlacement();
            PrototypeBiteState preparedBite = preparedCreature.isDefeated() ? null
                : biteState != null ? biteState : new PrototypeBiteState(canBite(preparedHunter, preparedCreature, preparedPlacement)
                    ? Phase.PREPARING : Phase.READY,
                    canBite(preparedHunter, preparedCreature, preparedPlacement) ? 1 : 0);
            // Публикация только после полной подготовки: без вызовов, проверок и callbacks.
            hunterBeforeEntry = hunter.getPosition();
            creatureBeforeEntry = creature.getPosition();
            explorationRemainder = 0.0;
            initialObservationDone = true;
            hunter = preparedHunter;
            creature = preparedCreature;
            placement = preparedPlacement;
            biteState = preparedBite;
            mode = SceneMode.TACTICAL;
        }
        return result.getOutcome();
    }

    public TacticalExitResult exitTacticalAvailability() {
        if (mode != SceneMode.TACTICAL) return TacticalExitResult.NOT_TACTICAL;
        if (hunter.isDefeated()) return TacticalExitResult.HUNTER_DEFEATED;
        if (creature.canDetect(hunter.getPosition()) || canBite(hunter, creature, placement)) {
            return TacticalExitResult.ACTIVE_THREAT;
        }
        return TacticalExitResult.SUCCESS;
    }

    public TacticalExitResult tryExitTactical() {
        TacticalExitResult result = exitTacticalAvailability();
        if (result == TacticalExitResult.SUCCESS) {
            placement = null;
            detectionOnPreviousCheck = false;
            engagementOnPreviousCheck = false;
            mode = SceneMode.EXPLORATION;
        }
        return result;
    }

    /** Проверки и подготовка предшествуют публикации в однопоточной сцене. */
    public TacticalMoveResult tryMoveHunterTo(CellCoordinates target) {
        if (mode != SceneMode.TACTICAL) return TacticalMoveResult.NOT_TACTICAL;
        if (hunter.isDefeated()) return TacticalMoveResult.HUNTER_DEFEATED;
        if (target == null) return TacticalMoveResult.INVALID_INPUT;
        CellCoordinates current = placement.getHunterCell();
        if (target.equals(current)) return TacticalMoveResult.SAME_CELL;
        long dx = (long) target.getX() - current.getX();
        long dy = (long) target.getY() - current.getY();
        if (Math.max(Math.abs(dx), Math.abs(dy)) != 1) return TacticalMoveResult.NOT_ADJACENT;
        if (placement.creatureCell().filter(target::equals).isPresent()) return TacticalMoveResult.OCCUPIED;
        WorldPosition center = grid.cellToWorldCenter(target);
        double cellX = Math.floor((center.getX() - grid.getOriginX()) / grid.getCellSize());
        double cellY = Math.floor((center.getY() - grid.getOriginY()) / grid.getCellSize());
        if (!Double.isFinite(center.getX()) || !Double.isFinite(center.getY())
            || cellX != target.getX() || cellY != target.getY()
            || !Double.isFinite(Math.hypot(center.getX() - hunter.getPosition().getX(),
                center.getY() - hunter.getPosition().getY()))) return TacticalMoveResult.INVALID_INPUT;
        if (!geometry.isCirclePositionValid(center, hunter.getRadius())) return TacticalMoveResult.BLOCKED_CENTER;
        if (!geometry.isCirclePathValid(hunter.getPosition(), center, hunter.getRadius(),
            creature.isDefeated() ? null : creature.getPosition(), creature.getRadius())) {
            return TacticalMoveResult.BLOCKED_PATH;
        }
        TacticalPlacement preparedPlacement = creature.isDefeated()
            ? TacticalPlacement.hunterOnly(target) : new TacticalPlacement(target, placement.getCreatureCell());
        resolve(false, hunter.withTacticalPosition(center), preparedPlacement, true);
        return TacticalMoveResult.SUCCESS;
    }

    public int getHunterHealth() { return hunter.getHealth(); }
    public int getCreatureHealth() { return creature.getHealth(); }
    public long getTacticalTimeUnits() { return tacticalTimeUnits; }
    public TacticalAttackResult getLastResolution() { return lastResolution; }

    public Outcome attackAvailability(CellCoordinates target) {
        if (mode != SceneMode.TACTICAL) return Outcome.NOT_TACTICAL;
        if (hunter.isDefeated()) return Outcome.HUNTER_DEFEATED;
        if (target == null) return Outcome.INVALID_TARGET;
        if (creature.isDefeated()) return Outcome.TARGET_DEFEATED;
        if (!target.equals(placement.getCreatureCell())) return Outcome.INVALID_TARGET;
        if (!adjacent(placement.getHunterCell(), target)) return Outcome.OUT_OF_RANGE;
        if (!geometry.hasLineOfSight(hunter.getPosition(), creature.getPosition())) return Outcome.BLOCKED;
        return Outcome.HIT;
    }

    public TacticalAttackResult tryAttackCreature(CellCoordinates target) {
        return attack(target, true);
    }

    /** Использует ровно тот же расчёт, но ничего не публикует. */
    public TacticalAttackResult previewAttackCreature(CellCoordinates target) {
        return attack(target, false);
    }

    private TacticalAttackResult attack(CellCoordinates target, boolean publish) {
        Outcome check = attackAvailability(target);
        if (check != Outcome.HIT) return new TacticalAttackResult(check, hunter.getHealth(), hunter.getHealth(),
            creature.getHealth(), creature.getHealth(), 0, biteState, biteState, Collections.emptyList());
        return resolve(true, hunter.copy(), placement, publish);
    }

    public TacticalAttackResult tryBreakoutTo(CellCoordinates target) { return breakout(target, true); }

    public TacticalAttackResult previewBreakoutTo(CellCoordinates target) { return breakout(target, false); }

    private RejectionReason breakoutRejection(CellCoordinates target) {
        if (mode != SceneMode.TACTICAL) return RejectionReason.NOT_TACTICAL;
        if (hunter.isDefeated()) return RejectionReason.HUNTER_DEFEATED;
        if (target == null) return RejectionReason.INVALID_TARGET;
        WorldPosition end = grid.cellToWorldCenter(target);
        if (!Double.isFinite(end.getX()) || !Double.isFinite(end.getY())
            || Math.floor((end.getX() - grid.getOriginX()) / grid.getCellSize()) != target.getX()
            || Math.floor((end.getY() - grid.getOriginY()) / grid.getCellSize()) != target.getY()
            || !Double.isFinite(Math.hypot(end.getX() - hunter.getPosition().getX(),
                end.getY() - hunter.getPosition().getY()))) return RejectionReason.INVALID_TARGET;
        long dx = (long) target.getX() - placement.getHunterCell().getX();
        long dy = (long) target.getY() - placement.getHunterCell().getY();
        if (Math.abs(dx) > 4 || Math.abs(dy) > 4 || dx * dx + dy * dy < 4
            || dx * dx + dy * dy > 16) return RejectionReason.INVALID_DISTANCE;
        if (!geometry.isCirclePositionValid(hunter.getPosition(), hunter.getRadius())) return RejectionReason.BLOCKED_START;
        if (!geometry.isCirclePositionValid(end, hunter.getRadius())) return RejectionReason.BLOCKED_CENTER;
        if (!geometry.isCirclePathValid(hunter.getPosition(), end, hunter.getRadius())) return RejectionReason.BLOCKED_PATH;
        if (!creature.isDefeated()) {
            WorldPosition from = hunter.getPosition(), c = creature.getPosition();
            double x = end.getX() - from.getX(), y = end.getY() - from.getY();
            double length = Math.hypot(x, y), ux = x / length, uy = y / length;
            double projection = Math.max(0, Math.min(length,
                (c.getX() - from.getX()) * ux + (c.getY() - from.getY()) * uy));
            if (target.equals(placement.getCreatureCell()) || Math.hypot(c.getX() - from.getX() - ux * projection,
                c.getY() - from.getY() - uy * projection) < hunter.getRadius() + creature.getRadius()) {
                return RejectionReason.OCCUPIED_PATH;
            }
        }
        return null;
    }

    private TacticalAttackResult breakout(CellCoordinates target, boolean publish) {
        RejectionReason rejection = breakoutRejection(target);
        if (rejection != null) return new TacticalAttackResult(Outcome.REJECTED,
            hunter.getHealth(), hunter.getHealth(), creature.getHealth(), creature.getHealth(), 0,
            biteState, biteState, Collections.emptyList(), Kind.BREAKOUT, rejection, 0,
            hunter.getPosition(), hunter.getPosition(), mode, mode);
        long dx = (long) target.getX() - placement.getHunterCell().getX();
        long dy = (long) target.getY() - placement.getHunterCell().getY();
        return resolve(false, hunter.copy(), placement, publish, target, dx * dx + dy * dy == 4 ? 2 : 3);
    }

    private boolean canBite(HunterState h, CreaturePatrolState c, TacticalPlacement p) {
        return !h.isDefeated() && !c.isDefeated()
            && adjacent(p.getHunterCell(), p.getCreatureCell())
            && geometry.hasLineOfSight(h.getPosition(), c.getPosition());
    }

    private static boolean adjacent(CellCoordinates a, CellCoordinates b) {
        long dx = (long) a.getX() - b.getX(), dy = (long) a.getY() - b.getY();
        return Math.max(Math.abs(dx), Math.abs(dy)) == 1;
    }

    /** Узкий цикл прототипа; все вычисления до публикации, без AI scheduler. */
    private TacticalAttackResult resolve(boolean attack, HunterState preparedHunter,
                                        TacticalPlacement preparedPlacement, boolean publish) {
        return resolve(attack, preparedHunter, preparedPlacement, publish, null, attack ? 2 : 1);
    }

    private TacticalAttackResult resolve(boolean attack, HunterState preparedHunter,
                                        TacticalPlacement preparedPlacement, boolean publish,
                                        CellCoordinates breakoutTarget, int duration) {
        boolean breakout = breakoutTarget != null;
        CreaturePatrolState preparedCreature = creature.copy();
        PrototypeBiteState preparedBite = biteState;
        List<Event> events = new ArrayList<>();
        int elapsed = 0;
        boolean hit = false;
        for (int unit = 1; unit <= duration; unit++) {
            elapsed = unit;
            if (attack && unit == duration) {
                preparedCreature = preparedCreature.withDamage(1);
                hit = true;
                events.add(new Event(EventType.HIT, unit));
            }
            if (preparedCreature.isDefeated()) {
                preparedBite = null;
            } else {
                if (preparedBite.getPhase() == Phase.PREPARING) {
                    boolean bites = canBite(preparedHunter, preparedCreature, preparedPlacement);
                    if (bites) preparedHunter = preparedHunter.withDamage(1);
                    events.add(new Event(bites ? EventType.BITE : EventType.BITE_MISS, unit));
                    preparedBite = new PrototypeBiteState(Phase.RECOVERING, 3);
                    events.add(new Event(EventType.RECOVERY_STARTED, unit));
                } else if (preparedBite.getPhase() == Phase.RECOVERING) {
                    int remaining = preparedBite.getRemainingUnits() - 1;
                    preparedBite = new PrototypeBiteState(remaining == 0 ? Phase.READY : Phase.RECOVERING, remaining);
                    if (remaining == 0) events.add(new Event(EventType.RECOVERY_ENDED, unit));
                }
            }
            if (preparedHunter.isDefeated()) {
                if (breakout) events.add(new Event(EventType.BREAKOUT_INTERRUPTED, unit));
                break;
            }
            if (breakout && unit == 1) {
                preparedHunter = preparedHunter.withTacticalPosition(grid.cellToWorldCenter(breakoutTarget));
                preparedPlacement = preparedCreature.isDefeated() ? TacticalPlacement.hunterOnly(breakoutTarget)
                    : new TacticalPlacement(breakoutTarget, preparedPlacement.getCreatureCell());
                events.add(new Event(EventType.BREAKOUT_MOVED, unit));
            }
            if (!preparedCreature.isDefeated()) {
                if (preparedBite.getPhase() == Phase.READY
                    && canBite(preparedHunter, preparedCreature, preparedPlacement)) {
                    preparedBite = new PrototypeBiteState(Phase.PREPARING, 1);
                    events.add(new Event(EventType.PREPARATION_STARTED, unit));
                }
            }
        }
        if (preparedCreature.isDefeated()) preparedPlacement = TacticalPlacement.hunterOnly(preparedPlacement.getHunterCell());
        long preparedTime = Math.addExact(tacticalTimeUnits, elapsed);
        SceneMode preparedMode = mode;
        boolean detectedAtExit = false;
        Outcome outcome = attack ? (hit ? Outcome.HIT : Outcome.INTERRUPTED) : Outcome.MOVE;
        boolean exitAfterVictory = hit && !creature.isDefeated() && preparedCreature.isDefeated();
        boolean exitAfterBreakout = false;
        if (breakout) {
            if (preparedHunter.isDefeated()) {
                outcome = Outcome.INTERRUPTED;
            } else {
                boolean endpointDetected = preparedCreature.canDetect(preparedHunter.getPosition());
                exitAfterBreakout = !canEngage(preparedHunter, preparedCreature, endpointDetected)
                    && !canBite(preparedHunter, preparedCreature, preparedPlacement);
                detectedAtExit = exitAfterBreakout && endpointDetected;
                outcome = exitAfterBreakout ? Outcome.ESCAPED : Outcome.MOVED_STILL_TACTICAL;
            }
        }
        if (exitAfterVictory || exitAfterBreakout) {
            preparedMode = SceneMode.EXPLORATION;
            preparedPlacement = null;
            events.add(new Event(EventType.TACTICAL_EXITED, elapsed));
        }
        TacticalAttackResult result = new TacticalAttackResult(outcome,
            hunter.getHealth(), preparedHunter.getHealth(), creature.getHealth(), preparedCreature.getHealth(),
            elapsed, biteState, preparedBite, events, breakout ? Kind.BREAKOUT : attack ? Kind.ATTACK : Kind.MOVE,
            null, duration, hunter.getPosition(), preparedHunter.getPosition(), mode, preparedMode);
        if (publish) {
            // Здесь нет allocation, проверок или callbacks: публикация в однопоточной сцене.
            hunter = preparedHunter;
            creature = preparedCreature;
            placement = preparedPlacement;
            biteState = preparedBite;
            tacticalTimeUnits = preparedTime;
            lastResolution = result;
            mode = preparedMode;
            if (exitAfterVictory || exitAfterBreakout) {
                detectionOnPreviousCheck = detectedAtExit;
                engagementOnPreviousCheck = false;
            }
        }
        return result;
    }

    public SceneMode getMode() { return mode; }
    public TacticalPlacement getPlacement() { return placement; }
    public WorldPosition getHunterPosition() { return hunter.getPosition(); }
    public WorldPosition getCreaturePosition() { return creature.getPosition(); }
    public double getHunterRadius() { return hunter.getRadius(); }
    public double getCreatureRadius() { return creature.getRadius(); }
    public WorldPosition getHunterBeforeEntry() { return hunterBeforeEntry; }
    public WorldPosition getCreatureBeforeEntry() { return creatureBeforeEntry; }
    public Behavior getCreatureBehavior() { return creature.getBehavior(); }
    public double getCreatureSearchRemaining() { return creature.getSearchRemaining(); }
    public boolean isCreatureReturnBlocked() { return creature.isReturnBlocked(); }
    public boolean isCreatureAlerted() { return creature.isAlerted(); }
    public WorldPosition getLastSeenHunterPosition() { return creature.getLastSeenHunterPosition(); }
}
