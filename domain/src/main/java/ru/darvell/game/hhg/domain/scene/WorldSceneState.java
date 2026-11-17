package ru.darvell.game.hhg.domain.scene;

import ru.darvell.game.hhg.domain.actor.CreaturePatrolState;
import ru.darvell.game.hhg.domain.actor.HunterState;
import ru.darvell.game.hhg.domain.tactical.CellCoordinates;
import ru.darvell.game.hhg.domain.tactical.TacticalExitResult;
import ru.darvell.game.hhg.domain.tactical.TacticalAttackResult;
import ru.darvell.game.hhg.domain.tactical.TacticalAttackResult.Outcome;
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
    private HunterState hunter;
    private CreaturePatrolState creature;
    private final TacticalGrid grid;
    private final WorldGeometry geometry;
    private final TacticalEntryPlanner planner = new TacticalEntryPlanner();
    private boolean contactOnPreviousCheck;
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
        hunter.move(x, y, delta, geometry);
        boolean contactNow = creature.canDetect(hunter.getPosition());
        boolean newContact = contactNow && !contactOnPreviousCheck;
        creature.checkDetection(hunter.getPosition());
        contactOnPreviousCheck = contactNow;
        TacticalEntryResult.Outcome outcome = null;
        if (newContact || manualEntry) outcome = enterTactical();
        if (mode == SceneMode.EXPLORATION) creature.advancePatrol(delta);
        return outcome;
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
            contactOnPreviousCheck = false;
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
        if (!geometry.isCirclePathValid(hunter.getPosition(), center, hunter.getRadius())) {
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
        CreaturePatrolState preparedCreature = creature.copy();
        PrototypeBiteState preparedBite = biteState;
        List<Event> events = new ArrayList<>();
        int duration = attack ? 2 : 1;
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
                if (preparedHunter.isDefeated()) break;
                if (preparedBite.getPhase() == Phase.READY
                    && canBite(preparedHunter, preparedCreature, preparedPlacement)) {
                    preparedBite = new PrototypeBiteState(Phase.PREPARING, 1);
                    events.add(new Event(EventType.PREPARATION_STARTED, unit));
                }
            }
        }
        if (preparedCreature.isDefeated()) preparedPlacement = TacticalPlacement.hunterOnly(preparedPlacement.getHunterCell());
        long preparedTime = Math.addExact(tacticalTimeUnits, elapsed);
        TacticalAttackResult result = new TacticalAttackResult(attack ? (hit ? Outcome.HIT : Outcome.INTERRUPTED) : Outcome.MOVE,
            hunter.getHealth(), preparedHunter.getHealth(), creature.getHealth(), preparedCreature.getHealth(),
            elapsed, biteState, preparedBite, events);
        if (publish) {
            // Здесь нет allocation, проверок или callbacks: публикация в однопоточной сцене.
            hunter = preparedHunter;
            creature = preparedCreature;
            placement = preparedPlacement;
            biteState = preparedBite;
            tacticalTimeUnits = preparedTime;
            lastResolution = result;
        }
        return result;
    }

    public SceneMode getMode() { return mode; }
    public TacticalPlacement getPlacement() { return placement; }
    public WorldPosition getHunterPosition() { return hunter.getPosition(); }
    public WorldPosition getCreaturePosition() { return creature.getPosition(); }
    public double getHunterRadius() { return hunter.getRadius(); }
    public double getCreatureRadius() { return creature.getRadius(); }
    public boolean isCreatureAlerted() { return creature.isAlerted(); }
    public WorldPosition getLastSeenHunterPosition() { return creature.getLastSeenHunterPosition(); }
}
