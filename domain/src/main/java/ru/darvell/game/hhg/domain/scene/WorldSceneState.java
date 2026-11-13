package ru.darvell.game.hhg.domain.scene;

import ru.darvell.game.hhg.domain.actor.CreaturePatrolState;
import ru.darvell.game.hhg.domain.actor.HunterState;
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
    private SceneMode mode = SceneMode.EXPLORATION;
    private TacticalPlacement placement;

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
        boolean firstDetection = creature.checkDetection(hunter.getPosition());
        TacticalEntryResult.Outcome outcome = null;
        if (firstDetection || manualEntry) outcome = enterTactical();
        if (mode == SceneMode.EXPLORATION) creature.advancePatrol(delta);
        return outcome;
    }

    public TacticalEntryResult.Outcome enterTactical() {
        if (mode == SceneMode.TACTICAL) return TacticalEntryResult.Outcome.ALREADY_TACTICAL;
        TacticalEntryResult result = planner.plan(hunter.getPosition(), hunter.getRadius(),
            creature.getPosition(), creature.getRadius(), grid, geometry);
        if (result.getOutcome() == TacticalEntryResult.Outcome.SUCCESS) {
            HunterState preparedHunter = hunter.withTacticalPosition(result.getHunterCenter());
            CreaturePatrolState preparedCreature = creature.withTacticalPosition(result.getCreatureCenter());
            TacticalPlacement preparedPlacement = result.getPlacement();
            // Публикация только после полной подготовки: без вызовов, проверок и callbacks.
            hunter = preparedHunter;
            creature = preparedCreature;
            placement = preparedPlacement;
            mode = SceneMode.TACTICAL;
        }
        return result.getOutcome();
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
