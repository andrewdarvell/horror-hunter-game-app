package ru.darvell.game.hhg.domain.scene;

import ru.darvell.game.hhg.domain.actor.CreaturePatrolState;
import ru.darvell.game.hhg.domain.actor.CreaturePatrolState.Behavior;
import ru.darvell.game.hhg.domain.actor.HunterState;
import ru.darvell.game.hhg.domain.combat.timeline.CombatTimeline;
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
import java.util.function.LongToDoubleFunction;
import lombok.Value;
import ru.darvell.game.hhg.domain.tactical.TacticalMoveResult;
import ru.darvell.game.hhg.domain.tactical.TacticalEntryPlanner;
import ru.darvell.game.hhg.domain.tactical.TacticalEntryResult;
import ru.darvell.game.hhg.domain.tactical.TacticalGrid;
import ru.darvell.game.hhg.domain.tactical.Facing;
import ru.darvell.game.hhg.domain.tactical.SpearClashConfig;
import ru.darvell.game.hhg.domain.tactical.SpearClashState;
import ru.darvell.game.hhg.domain.tactical.SpearClashState.WolfPhase;
import ru.darvell.game.hhg.domain.tactical.TacticalAttackResult.ClashFacts;
import ru.darvell.game.hhg.domain.tactical.TacticalPlacement;
import ru.darvell.game.hhg.domain.world.WorldGeometry;
import ru.darvell.game.hhg.domain.world.WorldPosition;

import static ru.darvell.game.hhg.domain.combat.timeline.CombatTimeline.QUANTA_PER_TICK;

/** Владеет независимыми копиями участников; наружу выдаёт только значения состояния. */
public final class WorldSceneState {
    private static final double EXPLORATION_STEP_SECONDS = 0.01;
    private static final double STEP_ROUNDING_SECONDS = 1e-12;
    private static final long BITE_PREPARATION = QUANTA_PER_TICK;
    private static final long BITE_RECOVERY = 3 * QUANTA_PER_TICK;

    private final TacticalGrid grid;
    private final WorldGeometry geometry;
    private final TacticalEntryPlanner planner = new TacticalEntryPlanner();
    private final SpearClashConfig spearConfig;
    private final SpearClash spearClash;
    private final LongToDoubleFunction clashSample;
    private SceneSnapshot state;
    private boolean initialObservationDone;
    private double explorationRemainder;
    private double pendingDirectionX;
    private double pendingDirectionY;
    private WorldPosition hunterBeforeEntry;
    private WorldPosition creatureBeforeEntry;

    public WorldSceneState(HunterState hunter, CreaturePatrolState creature,
                           TacticalGrid grid, WorldGeometry geometry) {
        this(hunter, creature, grid, geometry, null);
    }

    public WorldSceneState(HunterState hunter, CreaturePatrolState creature, TacticalGrid grid,
                           WorldGeometry geometry, SpearClashConfig spearConfig) {
        this(hunter, creature, grid, geometry, spearConfig, 0,
            spearConfig == null ? null : cursor -> SpearClash.sample(spearConfig.getSeed(), cursor));
    }

    WorldSceneState(HunterState hunter, CreaturePatrolState creature, TacticalGrid grid, WorldGeometry geometry,
                    SpearClashConfig spearConfig, long now, LongToDoubleFunction clashSample) {
        if (hunter == null || creature == null || grid == null || geometry == null) {
            throw new IllegalArgumentException("Scene components must be present");
        }
        this.grid = grid;
        this.geometry = geometry;
        this.spearConfig = spearConfig;
        this.spearClash = spearConfig == null ? null : new SpearClash(grid, geometry);
        this.clashSample = clashSample;
        ClashTiming clash = spearConfig == null ? null
            : new ClashTiming(spearConfig.getFacing(), false, 0, LeapTiming.UNUSED, 0);
        state = new SceneSnapshot(hunter.copy(), creature.copy(), SceneMode.EXPLORATION, now,
            null, null, false, false, clash);
    }

    /** null означает отсутствие запроса в этом обновлении. */
    public TacticalEntryResult.Outcome update(double x, double y, double delta, boolean manualEntry) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(delta) || delta < 0.0) {
            throw new IllegalArgumentException("Direction and non-negative time must be finite");
        }
        if (state.mode == SceneMode.TACTICAL) {
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
                if (state.mode == SceneMode.TACTICAL) return outcome;
            }
        }

        for (long i = 0; i < steps; i++) {
            boolean pending = i == 0 && explorationRemainder > 0.0;
            state.hunter.move(pending ? pendingDirectionX : x, pending ? pendingDirectionY : y,
                EXPLORATION_STEP_SECONDS, geometry, state.creature.isDefeated() ? null : state.creature.getPosition(),
                state.creature.getRadius());

            if (observeEngagementEdge() || manualEntry) {
                outcome = enterTactical();
                manualEntry = false;
                if (state.mode == SceneMode.TACTICAL) return outcome;
            }

            state.creature.advanceExploration(EXPLORATION_STEP_SECONDS,
                state.hunter.isDefeated() ? null : state.hunter.getPosition(), state.hunter.getRadius());
            if (observeEngagementEdge()) {
                outcome = enterTactical();
                if (state.mode == SceneMode.TACTICAL) return outcome;
            }
            state.creature.finishExplorationStep();
        }
        if (manualEntry) {
            outcome = enterTactical();
            if (state.mode == SceneMode.TACTICAL) return outcome;
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
        SceneSnapshot before = state;
        boolean detected = before.creature.observeHunter(before.hunter.getPosition(), !before.hunter.isDefeated());
        boolean engagement = canEngage(before.hunter, before.creature, detected);
        boolean newEngagement = engagement && !before.engaged;
        if (detected != before.detected || engagement != before.engaged) {
            state = new SceneSnapshot(before.hunter, before.creature, before.mode, before.nowQuanta,
                before.biteTiming, before.lastResolution, detected, engagement, before.clash);
        }
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
        SceneSnapshot before = state;
        if (before.mode == SceneMode.TACTICAL) return TacticalEntryResult.Outcome.ALREADY_TACTICAL;
        HunterState hunter = before.hunter;
        CreaturePatrolState creature = before.creature;
        TacticalEntryResult result = creature.isDefeated()
            ? planner.planHunterOnly(hunter.getPosition(), hunter.getRadius(), grid, geometry)
            : planner.plan(hunter.getPosition(), hunter.getRadius(),
            creature.getPosition(), creature.getRadius(), grid, geometry);
        if (result.getOutcome() == TacticalEntryResult.Outcome.SUCCESS) {
            HunterState preparedHunter = hunter.withTacticalPosition(result.getHunterCenter());
            CreaturePatrolState preparedCreature = result.creatureCenter()
                .map(creature::withTacticalPosition).orElse(creature);
            BiteTiming preparedBite = before.biteTiming;
            if (spearConfig == null && !preparedCreature.isDefeated() && preparedBite == null) {
                preparedBite = canContact(preparedHunter, preparedCreature)
                    ? new BiteTiming(Phase.PREPARING, Math.addExact(before.nowQuanta, BITE_PREPARATION))
                    : BiteTiming.READY;
            }
            ClashTiming clash = before.clash;
            if (clash != null && clash.leap == LeapTiming.UNUSED) {
                SpearClash.Plan plan = preparedCreature.isDefeated() || preparedHunter.isDefeated()
                    ? null : spearClash.plan(preparedHunter.getPosition(), preparedCreature.getPosition());
                clash = clash.withLeap(plan == null ? LeapTiming.SPENT : initialLeap(plan, before.nowQuanta));
            }
            SceneSnapshot prepared = new SceneSnapshot(preparedHunter, preparedCreature, SceneMode.TACTICAL,
                before.nowQuanta, preparedBite, before.lastResolution, before.detected, before.engaged, clash);
            // Публикация только после полной подготовки: без вызовов, проверок и callbacks.
            hunterBeforeEntry = hunter.getPosition();
            creatureBeforeEntry = creature.getPosition();
            explorationRemainder = 0.0;
            initialObservationDone = true;
            state = prepared;
        }
        return result.getOutcome();
    }

    private LeapTiming initialLeap(SpearClash.Plan plan, long now) {
        long launch = Math.addExact(now, spearConfig.getPreparationQuanta());
        return new LeapTiming(WolfPhase.PREPARING, plan, launch, Math.addExact(launch, spearConfig.getFlightQuanta()));
    }

    public TacticalExitResult exitTacticalAvailability() {
        if (state.mode != SceneMode.TACTICAL) return TacticalExitResult.NOT_TACTICAL;
        if (state.hunter.isDefeated()) return TacticalExitResult.HUNTER_DEFEATED;
        if (state.creature.canDetect(state.hunter.getPosition()) || canContact(state.hunter, state.creature)) {
            return TacticalExitResult.ACTIVE_THREAT;
        }
        return TacticalExitResult.SUCCESS;
    }

    public TacticalExitResult tryExitTactical() {
        TacticalExitResult result = exitTacticalAvailability();
        if (result == TacticalExitResult.SUCCESS) {
            state = new SceneSnapshot(state.hunter, state.creature, SceneMode.EXPLORATION, state.nowQuanta,
                state.biteTiming, state.lastResolution, false, false,
                state.clash == null ? null : state.clash.afterExit());
        }
        return result;
    }

    /** Проверки и подготовка предшествуют публикации в однопоточной сцене. */
    public TacticalMoveResult tryMoveHunterTo(CellCoordinates target) {
        if (state.mode != SceneMode.TACTICAL) return TacticalMoveResult.NOT_TACTICAL;
        HunterState hunter = state.hunter;
        CreaturePatrolState creature = state.creature;
        if (hunter.isDefeated()) return TacticalMoveResult.HUNTER_DEFEATED;
        if (target == null) return TacticalMoveResult.INVALID_INPUT;
        CellCoordinates current = state.placement.getHunterCell();
        if (target.equals(current)) return TacticalMoveResult.SAME_CELL;
        long dx = (long) target.getX() - current.getX();
        long dy = (long) target.getY() - current.getY();
        if (Math.max(Math.abs(dx), Math.abs(dy)) != 1) return TacticalMoveResult.NOT_ADJACENT;
        if (state.placement.creatureCell().filter(target::equals).isPresent()) return TacticalMoveResult.OCCUPIED;
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
        resolve(Kind.MOVE, target, QUANTA_PER_TICK, true);
        return TacticalMoveResult.SUCCESS;
    }

    public int getHunterHealth() { return state.hunter.getHealth(); }
    public int getCreatureHealth() { return state.creature.getHealth(); }
    public long getTacticalTimeQuanta() { return state.nowQuanta; }
    public long getTacticalTimeUnits() { return toTacticalUnits(state.nowQuanta); }
    public TacticalAttackResult getLastResolution() { return state.lastResolution; }
    public PrototypeBiteState getBiteState() { return state.biteView(); }
    public SpearClashState getSpearClashState() { return state.clashView(); }

    public TacticalAttackResult previewSpearAction(Kind kind, Facing direction) {
        return spearAction(kind, direction, false);
    }

    public TacticalAttackResult trySpearAction(Kind kind, Facing direction) {
        return spearAction(kind, direction, true);
    }

    private TacticalAttackResult spearAction(Kind kind, Facing direction, boolean publish) {
        long duration = spearActionQuanta(kind, direction);
        if (duration > 0) return resolve(kind, null, direction, duration, publish);
        return new TacticalAttackResult(Outcome.REJECTED, getHunterHealth(), getHunterHealth(),
            getCreatureHealth(), getCreatureHealth(), 0, getBiteState(), getBiteState(), Collections.emptyList(),
            kind, null, 0, getHunterPosition(), getHunterPosition(), state.mode, state.mode);
    }

    public long spearActionQuanta(Kind kind, Facing direction) {
        ClashTiming clash = state.clash;
        if (kind == null || clash == null || state.mode != SceneMode.TACTICAL || state.hunter.isDefeated()
            || clash.hunterRecovery > state.nowQuanta) return 0;
        switch (kind) {
            case BRACE: return clash.braced ? 0 : spearConfig.getBraceQuanta();
            case LOWER_SPEAR: return clash.braced ? QUANTA_PER_TICK : 0;
            case HOLD_BRACE: return clash.braced && clash.leap.hasContact() ? clash.leap.contact - state.nowQuanta : 0;
            case TURN: return direction == null ? 0 : QUANTA_PER_TICK * clash.facing.stepsTo(direction);
            default: return 0;
        }
    }

    public Outcome attackAvailability(CellCoordinates target) {
        if (state.mode != SceneMode.TACTICAL) return Outcome.NOT_TACTICAL;
        HunterState hunter = state.hunter;
        CreaturePatrolState creature = state.creature;
        if (hunter.isDefeated()) return Outcome.HUNTER_DEFEATED;
        if (target == null) return Outcome.INVALID_TARGET;
        if (creature.isDefeated()) return Outcome.TARGET_DEFEATED;
        if (!target.equals(state.placement.getCreatureCell())) return Outcome.INVALID_TARGET;
        if (!adjacent(state.placement.getHunterCell(), target)) return Outcome.OUT_OF_RANGE;
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
        if (check != Outcome.HIT) {
            PrototypeBiteState bite = getBiteState();
            return new TacticalAttackResult(check, getHunterHealth(), getHunterHealth(),
                getCreatureHealth(), getCreatureHealth(), 0, bite, bite, Collections.emptyList());
        }
        return resolve(Kind.ATTACK, null, 2 * QUANTA_PER_TICK, publish);
    }

    public TacticalAttackResult tryBreakoutTo(CellCoordinates target) { return breakout(target, true); }

    public TacticalAttackResult previewBreakoutTo(CellCoordinates target) { return breakout(target, false); }

    private RejectionReason breakoutRejection(CellCoordinates target, HunterState hunter, CreaturePatrolState creature) {
        if (hunter.isDefeated()) return RejectionReason.HUNTER_DEFEATED;
        if (target == null) return RejectionReason.INVALID_TARGET;
        WorldPosition end = grid.cellToWorldCenter(target);
        if (!Double.isFinite(end.getX()) || !Double.isFinite(end.getY())
            || Math.floor((end.getX() - grid.getOriginX()) / grid.getCellSize()) != target.getX()
            || Math.floor((end.getY() - grid.getOriginY()) / grid.getCellSize()) != target.getY()
            || !Double.isFinite(Math.hypot(end.getX() - hunter.getPosition().getX(),
                end.getY() - hunter.getPosition().getY()))) return RejectionReason.INVALID_TARGET;
        CellCoordinates current = grid.worldToCell(hunter.getPosition());
        long dx = (long) target.getX() - current.getX();
        long dy = (long) target.getY() - current.getY();
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
            if (target.equals(grid.worldToCell(c)) || Math.hypot(c.getX() - from.getX() - ux * projection,
                c.getY() - from.getY() - uy * projection) < hunter.getRadius() + creature.getRadius()) {
                return RejectionReason.OCCUPIED_PATH;
            }
        }
        return null;
    }

    private TacticalAttackResult breakout(CellCoordinates target, boolean publish) {
        RejectionReason rejection = state.mode == SceneMode.TACTICAL
            ? breakoutRejection(target, state.hunter, state.creature) : RejectionReason.NOT_TACTICAL;
        if (rejection != null) {
            PrototypeBiteState bite = getBiteState();
            return new TacticalAttackResult(Outcome.REJECTED,
                getHunterHealth(), getHunterHealth(), getCreatureHealth(), getCreatureHealth(), 0,
                bite, bite, Collections.emptyList(), Kind.BREAKOUT, rejection, 0,
                getHunterPosition(), getHunterPosition(), state.mode, state.mode);
        }
        long dx = (long) target.getX() - state.placement.getHunterCell().getX();
        long dy = (long) target.getY() - state.placement.getHunterCell().getY();
        long duration = (dx * dx + dy * dy == 4 ? 2 : 3) * QUANTA_PER_TICK;
        return resolve(Kind.BREAKOUT, target, duration, publish);
    }

    private boolean canContact(HunterState h, CreaturePatrolState c) {
        return !h.isDefeated() && !c.isDefeated()
            && adjacent(grid.worldToCell(h.getPosition()), grid.worldToCell(c.getPosition()))
            && geometry.hasLineOfSight(h.getPosition(), c.getPosition());
    }

    private static boolean adjacent(CellCoordinates a, CellCoordinates b) {
        long dx = (long) a.getX() - b.getX(), dy = (long) a.getY() - b.getY();
        return Math.max(Math.abs(dx), Math.abs(dy)) == 1;
    }

    private TacticalAttackResult resolve(Kind kind, CellCoordinates target, long duration, boolean publish) {
        return resolve(kind, target, null, duration, publish);
    }

    private TacticalAttackResult resolve(Kind kind, CellCoordinates target, Facing direction, long duration,
                                         boolean publish) {
        TacticalTransition transition = CombatTimeline.advanceToBoundary(
            new TacticalTransition(state, kind, target, direction, duration),
            TacticalTransition::atBoundary, TacticalTransition::nextDeadline, TacticalTransition::resolveBatch);
        SceneSnapshot after = transition.finish();
        if (publish) state = after;
        return after.lastResolution;
    }

    private static long toTacticalUnits(long quanta) {
        if (quanta % QUANTA_PER_TICK != 0) {
            throw new IllegalStateException("Expected a whole number of tactical ticks");
        }
        return quanta / QUANTA_PER_TICK;
    }

    private static int toResultUnits(long quanta) {
        return Math.toIntExact(toTacticalUnits(quanta));
    }

    /** Тактический расчёт использует относительные сроки до фактической остановки. */
    private final class TacticalTransition {
        private final SceneSnapshot before;
        private final Kind kind;
        private final CellCoordinates target;
        private final long duration;
        private final Facing direction;
        private final boolean needsFirstBoundary;
        private final List<Event> events = new ArrayList<>();
        private HunterState hunter;
        private CreaturePatrolState creature;
        private BiteTiming bite;
        private long offset;
        private Outcome strike = Outcome.INTERRUPTED;
        private Facing facing;
        private boolean braced;
        private LeapTiming leap;
        private long hunterRecovery;
        private long cursor;
        private boolean interrupted;
        private RejectionReason lateBlock;
        private SpearClash.Impact impact;
        private long contactOffset;

        private TacticalTransition(SceneSnapshot before, Kind kind, CellCoordinates target, Facing direction,
                                   long duration) {
            this.before = before;
            this.kind = kind;
            this.target = target;
            this.direction = direction;
            this.duration = duration;
            hunter = kind == Kind.MOVE ? before.hunter.withTacticalPosition(grid.cellToWorldCenter(target)) : before.hunter;
            creature = before.creature;
            bite = before.biteTiming == null ? null : before.biteTiming.rebase(-before.nowQuanta);
            needsFirstBoundary = kind == Kind.BREAKOUT || bite == BiteTiming.READY;
            if (before.clash != null) {
                facing = before.clash.facing;
                braced = kind == Kind.HOLD_BRACE && before.clash.braced;
                leap = before.clash.leap;
                cursor = before.clash.cursor;
            }
        }

        private boolean atBoundary() {
            if (before.clash == null) return offset == duration || hunter.isDefeated();
            return hunter.isDefeated() || creature.isDefeated() && (strike == Outcome.HIT || impact != null)
                || !commandActive() && hunterRecovery == 0;
        }

        private boolean commandActive() { return !interrupted && offset < duration; }

        private Long nextDeadline() {
            if (before.clash != null) {
                long next = commandActive() ? duration : hunterRecovery;
                if (leap.deadline != 0) next = Math.min(next, leap.deadline - before.nowQuanta);
                if (hunterRecovery > 0) next = Math.min(next, hunterRecovery);
                if (kind == Kind.BREAKOUT && commandActive() && offset < QUANTA_PER_TICK) {
                    next = Math.min(next, QUANTA_PER_TICK);
                }
                return next;
            }
            long next = duration;
            if (bite != null && bite.phase != Phase.READY) next = Math.min(next, bite.deadline);
            if (needsFirstBoundary && offset < QUANTA_PER_TICK) next = Math.min(next, QUANTA_PER_TICK);
            return next;
        }

        private TacticalTransition resolveBatch(long next) {
            Math.addExact(before.nowQuanta, next);
            offset = next;
            if (before.clash != null) {
                resolveClashBatch(Math.addExact(before.nowQuanta, offset));
                return this;
            }

            if (kind == Kind.ATTACK && offset == duration) resolveStrike();
            boolean biteDue = bite != null && bite.deadline == offset;
            if (biteDue) resolveBite();
            if (hunter.isDefeated()) {
                if (kind == Kind.BREAKOUT) record(EventType.BREAKOUT_INTERRUPTED);
                return this;
            }

            boolean firstBoundary = needsFirstBoundary && offset == QUANTA_PER_TICK;
            if (firstBoundary && kind == Kind.BREAKOUT) moveForBreakout();
            if (bite == BiteTiming.READY && (firstBoundary || biteDue) && canContact(hunter, creature)) {
                bite = new BiteTiming(Phase.PREPARING, offset + BITE_PREPARATION);
                record(EventType.PREPARATION_STARTED);
            }
            return this;
        }

        private void resolveClashBatch(long now) {
            if (!interrupted && offset == duration) {
                switch (kind) {
                    case ATTACK:
                        resolveStrike();
                        if (creature.isDefeated() && leap.hasContact()) {
                            leap = LeapTiming.SPENT;
                            record(EventType.LEAP_CANCELLED);
                        }
                        break;
                    case TURN: facing = direction; record(EventType.TURN_COMPLETED); break;
                    case BRACE: braced = true; record(EventType.BRACE_READY); break;
                    case LOWER_SPEAR: record(EventType.SPEAR_LOWERED); break;
                    default: break;
                }
            }
            if (creature.isDefeated() && strike == Outcome.HIT) return;
            if (leap.deadline == now) {
                if (leap.phase == WolfPhase.PREPARING) {
                    leap = new LeapTiming(WolfPhase.IN_FLIGHT, leap.plan, leap.contact, leap.contact);
                    record(EventType.LEAP_LAUNCHED);
                } else if (leap.phase == WolfPhase.IN_FLIGHT) {
                    resolveLeap(now);
                }
            }
            if (hunter.isDefeated() || creature.isDefeated() && impact != null) return;
            if (kind == Kind.BREAKOUT && offset == QUANTA_PER_TICK && !interrupted) {
                lateBlock = breakoutRejection(target, hunter, creature);
                if (lateBlock == null) {
                    applyBreakoutMove();
                } else {
                    interrupted = true;
                    record(EventType.BREAKOUT_BLOCKED);
                }
            }
            if (hunterRecovery == offset) {
                hunterRecovery = 0;
                record(EventType.HUNTER_RECOVERY_ENDED);
            }
            if (leap.phase == WolfPhase.RECOVERING && leap.deadline == now) {
                leap = LeapTiming.SPENT;
                record(EventType.WOLF_RECOVERY_ENDED);
            }
        }

        private void resolveLeap(long now) {
            impact = spearClash.resolve(leap.plan, hunter, creature, facing, braced, spearConfig.getWeights(), () -> {
                double sample = clashSample.applyAsDouble(cursor);
                cursor = Math.incrementExact(cursor);
                return sample;
            });
            contactOffset = offset;
            creature = creature.withTacticalPosition(SpearClash.last(impact.getPath())).withDamage(impact.getWolfDamage());
            hunter = hunter.withDamage(impact.getHunterDamage());
            SpearClashState.Result result = impact.getResult();
            switch (result) {
                case MISS: record(EventType.LEAP_MISSED); break;
                case BLOCKED: record(EventType.LEAP_BLOCKED); break;
                default:
                    braced = false;
                    record(EventType.LEAP_CONTACT);
                    break;
            }
            if (result == SpearClashState.Result.HIT && commandActive()) {
                interrupted = true;
                record(kind == Kind.BREAKOUT ? EventType.BREAKOUT_INTERRUPTED : EventType.ACTION_INTERRUPTED);
            }
            if (!hunter.isDefeated() && !creature.isDefeated() && impact.getHunterRecovery() > 0) {
                Math.addExact(now, impact.getHunterRecovery());
                hunterRecovery = Math.max(hunterRecovery, Math.addExact(offset, impact.getHunterRecovery()));
                record(EventType.HUNTER_RECOVERY_STARTED);
            }
            if (!creature.isDefeated()) {
                // The result retains recovery on terminal defeat; the snapshot has no future action.
                long deadline = hunter.isDefeated() ? 0 : Math.addExact(now, impact.getWolfRecovery());
                leap = deadline == 0 ? LeapTiming.SPENT : new LeapTiming(WolfPhase.RECOVERING, null, deadline, 0);
                record(EventType.WOLF_RECOVERY_STARTED);
            } else {
                leap = LeapTiming.SPENT;
            }
        }

        private void resolveStrike() {
            // Удар нацелен на единственного участника-противника, а не на исходную клетку.
            if (!canContact(hunter, creature)) {
                strike = Outcome.MISS;
                record(EventType.ATTACK_MISS);
                return;
            }
            creature = creature.withDamage(1);
            strike = Outcome.HIT;
            record(EventType.HIT);
            if (creature.isDefeated()) bite = null;
        }

        private void resolveBite() {
            if (bite.phase == Phase.PREPARING) {
                boolean hit = canContact(hunter, creature);
                if (hit) hunter = hunter.withDamage(1);
                record(hit ? EventType.BITE : EventType.BITE_MISS);
                bite = new BiteTiming(Phase.RECOVERING, offset + BITE_RECOVERY);
                record(EventType.RECOVERY_STARTED);
            } else {
                bite = BiteTiming.READY;
                record(EventType.RECOVERY_ENDED);
            }
        }

        private void moveForBreakout() {
            RejectionReason rejection = breakoutRejection(target, hunter, creature);
            if (rejection != null) {
                throw new IllegalStateException("Validated breakout path became invalid: " + rejection);
            }
            applyBreakoutMove();
        }

        private void applyBreakoutMove() {
            hunter = hunter.withTacticalPosition(grid.cellToWorldCenter(target));
            record(EventType.BREAKOUT_MOVED);
        }

        private void record(EventType type) {
            events.add(new Event(type, toResultUnits(offset)));
        }

        private SceneSnapshot finish() {
            SceneMode mode = before.mode;
            boolean detected = before.detected;
            boolean engaged = before.engaged;
            Outcome outcome = kind == Kind.ATTACK ? strike : interrupted ? Outcome.INTERRUPTED : Outcome.MOVE;
            boolean victory = creature.isDefeated() && (strike == Outcome.HIT || impact != null);
            if (kind == Kind.BREAKOUT) {
                if (hunter.isDefeated() || interrupted) {
                    outcome = Outcome.INTERRUPTED;
                } else {
                    boolean endpointDetected = creature.canDetect(hunter.getPosition());
                    boolean safe = !canEngage(hunter, creature, endpointDetected) && !canContact(hunter, creature);
                    outcome = safe ? Outcome.ESCAPED : Outcome.MOVED_STILL_TACTICAL;
                    if (safe) detected = endpointDetected;
                }
            }
            if (victory || outcome == Outcome.ESCAPED) {
                mode = SceneMode.EXPLORATION;
                if (victory) detected = false;
                engaged = false;
                if (leap != null && leap.hasContact()) record(EventType.LEAP_CANCELLED);
                record(EventType.TACTICAL_EXITED);
            }

            long now = Math.addExact(before.nowQuanta, offset);
            // При поражении охотника остаток фазы остаётся фактом результата, без будущего дедлайна.
            BiteTiming scheduled = bite == null || hunter.isDefeated() ? null : bite.rebase(before.nowQuanta);
            ClashTiming clash = before.clash == null ? null : new ClashTiming(facing, braced, 0, leap, cursor);
            if (clash != null && mode == SceneMode.EXPLORATION) clash = clash.afterExit();
            ClashFacts facts = clash == null ? null : new ClashFacts(before.clashView(), clash.view(now),
                impact == null ? null : impact.getResult(), before.creature.getPosition(), creature.getPosition(),
                impact == null ? Collections.emptyList() : impact.getPath(),
                contactOffset, impact == null ? 0 : impact.getHunterRecovery(), impact == null ? 0 : impact.getWolfRecovery());
            TacticalAttackResult result = new TacticalAttackResult(outcome,
                before.hunter.getHealth(), hunter.getHealth(), before.creature.getHealth(), creature.getHealth(),
                toResultUnits(offset), before.biteView(), bite == null ? null : bite.viewAt(offset), events,
                kind, lateBlock, toResultUnits(duration), before.hunter.getPosition(), hunter.getPosition(),
                before.mode, mode, facts);
            return new SceneSnapshot(hunter, creature, mode, now, scheduled, result, detected, engaged, clash);
        }
    }

    private static final class BiteTiming {
        private static final BiteTiming READY = new BiteTiming(Phase.READY, 0);

        private final Phase phase;
        private final long deadline;

        private BiteTiming(Phase phase, long deadline) {
            this.phase = phase;
            this.deadline = deadline;
        }

        private BiteTiming rebase(long origin) {
            return phase == Phase.READY ? this : new BiteTiming(phase, Math.addExact(origin, deadline));
        }

        private PrototypeBiteState viewAt(long now) {
            return new PrototypeBiteState(phase, phase == Phase.READY ? 0 : toResultUnits(deadline - now));
        }
    }

    @Value
    private static class LeapTiming {
        static final LeapTiming UNUSED = new LeapTiming(WolfPhase.UNUSED, null, 0, 0);
        static final LeapTiming SPENT = new LeapTiming(WolfPhase.SPENT, null, 0, 0);
        WolfPhase phase;
        SpearClash.Plan plan;
        long deadline;
        long contact;

        boolean hasContact() { return plan != null; }
    }

    @Value
    private static class ClashTiming {
        Facing facing;
        boolean braced;
        long hunterRecovery;
        LeapTiming leap;
        long cursor;

        ClashTiming withLeap(LeapTiming replacement) {
            return new ClashTiming(facing, braced, hunterRecovery, replacement, cursor);
        }

        ClashTiming afterExit() {
            return new ClashTiming(facing, false, 0, leap.hasContact() ? LeapTiming.SPENT : leap, cursor);
        }

        SpearClashState view(long now) {
            return new SpearClashState(facing, braced, Math.max(0, hunterRecovery - now), leap.phase,
                leap.deadline == 0 ? 0 : leap.deadline - now, leap.plan == null ? null : leap.plan.getTarget(), cursor);
        }
    }

    private final class SceneSnapshot {
        private final HunterState hunter;
        private final CreaturePatrolState creature;
        private final SceneMode mode;
        private final TacticalPlacement placement;
        private final long nowQuanta;
        private final BiteTiming biteTiming;
        private final TacticalAttackResult lastResolution;
        private final boolean detected;
        private final boolean engaged;
        private final ClashTiming clash;

        private SceneSnapshot(HunterState hunter, CreaturePatrolState creature, SceneMode mode,
                              long nowQuanta, BiteTiming biteTiming, TacticalAttackResult lastResolution,
                              boolean detected, boolean engaged, ClashTiming clash) {
            this.hunter = hunter;
            this.creature = creature;
            this.mode = mode;
            this.nowQuanta = nowQuanta;
            this.biteTiming = biteTiming;
            this.lastResolution = lastResolution;
            this.detected = detected;
            this.engaged = engaged;
            this.clash = clash;
            this.placement = mode == SceneMode.TACTICAL ? placementFromPositions() : null;
        }

        private TacticalPlacement placementFromPositions() {
            CellCoordinates hunterCell = grid.worldToCell(hunter.getPosition());
            return creature.isDefeated() ? TacticalPlacement.hunterOnly(hunterCell)
                : new TacticalPlacement(hunterCell, grid.worldToCell(creature.getPosition()));
        }

        private SpearClashState clashView() { return clash == null ? null : clash.view(nowQuanta); }

        private PrototypeBiteState biteView() {
            if (biteTiming != null) return biteTiming.viewAt(nowQuanta);
            return hunter.isDefeated() && lastResolution != null ? lastResolution.getBiteAfter() : null;
        }
    }

    public SceneMode getMode() { return state.mode; }
    public TacticalPlacement getPlacement() { return state.placement; }
    public WorldPosition getHunterPosition() { return state.hunter.getPosition(); }
    public WorldPosition getCreaturePosition() { return state.creature.getPosition(); }
    public double getHunterRadius() { return state.hunter.getRadius(); }
    public double getCreatureRadius() { return state.creature.getRadius(); }
    public WorldPosition getHunterBeforeEntry() { return hunterBeforeEntry; }
    public WorldPosition getCreatureBeforeEntry() { return creatureBeforeEntry; }
    public Behavior getCreatureBehavior() { return state.creature.getBehavior(); }
    public double getCreatureSearchRemaining() { return state.creature.getSearchRemaining(); }
    public boolean isCreatureReturnBlocked() { return state.creature.isReturnBlocked(); }
    public boolean isCreatureAlerted() { return state.creature.isAlerted(); }
    public WorldPosition getLastSeenHunterPosition() { return state.creature.getLastSeenHunterPosition(); }
}
