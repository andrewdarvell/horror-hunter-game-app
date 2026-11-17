package ru.darvell.game.hhg.domain.actor;

import ru.darvell.game.hhg.domain.world.WorldGeometry;
import ru.darvell.game.hhg.domain.world.WorldPosition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Патруль по неизменяемому двустороннему маршруту с паузами в waypoint. */
public final class CreaturePatrolState {
    public enum Behavior { PATROL, CHASE, SEARCH, RETURN }

    private final List<WorldPosition> route;
    private final WorldGeometry geometry;
    private final CreatureBehaviorConfig config;
    private final double radius;
    private boolean alerted;
    private WorldPosition lastSeenHunterPosition;
    private int health = 2;
    private WorldPosition position;
    private int targetIndex = 1;
    private int direction = 1;
    private double waitRemaining;
    private Behavior behavior = Behavior.PATROL;
    private WorldPosition patrolPosition;
    private double searchRemaining;
    private boolean returnBlocked;

    public CreaturePatrolState(List<WorldPosition> route, CreatureBehaviorConfig config, double radius,
                               WorldGeometry geometry) {
        if (route == null || route.size() < 2 || route.size() > 3 || geometry == null) {
            throw new IllegalArgumentException("Patrol needs two or three waypoints and world geometry");
        }
        if (config == null) throw new IllegalArgumentException("Creature behavior config must be present");
        requirePositiveFinite(radius, "Radius");
        List<WorldPosition> copy = new ArrayList<>(route);
        for (WorldPosition waypoint : copy) {
            if (!geometry.isCirclePositionValid(waypoint, radius)) {
                throw new IllegalArgumentException("Patrol waypoint must be valid in world geometry");
            }
        }
        for (int i = 1; i < copy.size(); i++) {
            WorldPosition from = copy.get(i - 1);
            WorldPosition to = copy.get(i);
            double length = Math.hypot(to.getX() - from.getX(), to.getY() - from.getY());
            requirePositiveFinite(length, "Segment length");
            requirePositiveFinite(length / config.getPatrolSpeed(), "Segment duration");
            if (!geometry.isCirclePathValid(from, to, radius)) {
                throw new IllegalArgumentException("Patrol segment must be valid in world geometry");
            }
        }
        this.route = Collections.unmodifiableList(copy);
        this.geometry = geometry;
        this.config = config;
        this.radius = radius;
        position = copy.get(0);
        patrolPosition = position;
        waitRemaining = config.getPauseSeconds();
    }

    public int getHealth() { return health; }
    public boolean isDefeated() { return health == 0; }

    public CreaturePatrolState withDamage(int damage) {
        if (damage < 0) throw new IllegalArgumentException("Negative damage");
        CreaturePatrolState result = copy();
        result.health = Math.max(0, health - damage);
        return result;
    }

    public WorldPosition getPosition() {
        return position;
    }

    public double getRadius() {
        return radius;
    }

    public boolean isAlerted() {
        return alerted;
    }

    public CreatureBehaviorConfig getBehaviorConfig() { return config; }
    public Behavior getBehavior() { return behavior; }
    public double getSearchRemaining() { return searchRemaining; }
    public boolean isReturnBlocked() { return returnBlocked; }

    /** Последняя замеченная позиция; null до первого обнаружения. */
    public WorldPosition getLastSeenHunterPosition() {
        return lastSeenHunterPosition;
    }

    /** Проверяет обнаружение до шага патруля, в том числе при нулевом интервале. */
    public void update(double deltaSeconds, WorldPosition hunterPosition) {
        validateDelta(deltaSeconds);
        checkDetection(hunterPosition);
        advancePatrol(deltaSeconds);
    }

    /** Независимая копия текущего состояния, без повторной инициализации. */
    public CreaturePatrolState copy() {
        return new CreaturePatrolState(this);
    }

    /** Готовит отдельное состояние; допустимость размещения сцены проверяет planner. */
    public CreaturePatrolState withTacticalPosition(WorldPosition center) {
        if (center == null || !Double.isFinite(center.getX()) || !Double.isFinite(center.getY())) {
            throw new IllegalArgumentException("Tactical center must be present and finite");
        }
        CreaturePatrolState copy = copy();
        copy.position = center;
        return copy;
    }

    private CreaturePatrolState(CreaturePatrolState source) {
        health = source.health;
        route = source.route;
        geometry = source.geometry;
        config = source.config;
        radius = source.radius;
        alerted = source.alerted;
        lastSeenHunterPosition = source.lastSeenHunterPosition;
        position = source.position;
        targetIndex = source.targetIndex;
        direction = source.direction;
        waitRemaining = source.waitRemaining;
        behavior = source.behavior;
        patrolPosition = source.patrolPosition;
        searchRemaining = source.searchRemaining;
        returnBlocked = source.returnBlocked;
    }

    /** Чистая проверка текущего контакта, независимо от истории alerted. */
    public boolean canDetect(WorldPosition hunterPosition) {
        if (hunterPosition == null || !Double.isFinite(hunterPosition.getX())
            || !Double.isFinite(hunterPosition.getY())) {
            throw new IllegalArgumentException("Hunter position must be present and finite");
        }
        double distance = Math.hypot(hunterPosition.getX() - position.getX(),
            hunterPosition.getY() - position.getY());
        if (!Double.isFinite(distance)) throw new IllegalArgumentException("Hunter distance must be finite");
        return !isDefeated() && distance <= config.getDetectionRadius()
            && geometry.hasLineOfSight(position, hunterPosition);
    }

    public boolean checkDetection(WorldPosition hunterPosition) {
        boolean previouslyAlerted = alerted;
        observeHunter(hunterPosition);
        return !previouslyAlerted && alerted;
    }

    /** Запоминает только подтверждённую зрением позицию. */
    public boolean observeHunter(WorldPosition hunterPosition) {
        if (canDetect(hunterPosition)) {
            alerted = true;
            lastSeenHunterPosition = hunterPosition;
            behavior = Behavior.CHASE;
            searchRemaining = 0.0;
            returnBlocked = false;
            return true;
        }
        if (!isDefeated() && behavior == Behavior.CHASE) {
            searchRemaining = config.getSearchSeconds();
            behavior = searchRemaining == 0.0 ? Behavior.RETURN : Behavior.SEARCH;
        }
        return false;
    }

    /** Один ход после замера видимости. Позиция тела нужна только для столкновений. */
    public void advanceExploration(double seconds, WorldPosition body, double bodyRadius) {
        validateDelta(seconds);
        if (isDefeated() || seconds == 0.0) return;

        if (behavior == Behavior.PATROL && !position.equals(patrolPosition)) {
            behavior = Behavior.RETURN;
        }
        switch (behavior) {
            case PATROL:
                advancePatrol(seconds, body, bodyRadius);
                patrolPosition = position;
                break;
            case CHASE:
                moveTowardLastSeen(seconds * config.getChaseSpeed(), body, bodyRadius);
                break;
            case SEARCH:
                boolean finished = finishesPhase(seconds, searchRemaining, config.getSearchSeconds());
                moveTowardLastSeen(Math.min(seconds, searchRemaining) * config.getSearchSpeed(), body, bodyRadius);
                searchRemaining = finished ? 0.0 : searchRemaining - seconds;
                break;
            case RETURN:
                returnToPatrol(seconds * config.getReturnSpeed(), body, bodyRadius);
                break;
        }
    }

    /** Истечение поиска применяется после проверки, чтобы новый контакт имел приоритет. */
    public void finishExplorationStep() {
        if (!isDefeated() && behavior == Behavior.SEARCH && searchRemaining == 0.0) {
            behavior = Behavior.RETURN;
        }
    }

    private void moveTowardLastSeen(double distance, WorldPosition body, double bodyRadius) {
        WorldPosition end = toward(lastSeenHunterPosition, distance);
        position = geometry.resolveStraightMovement(position, end, radius, body, bodyRadius);
    }

    private WorldPosition toward(WorldPosition target, double distance) {
        double dx = target.getX() - position.getX();
        double dy = target.getY() - position.getY();
        double length = Math.hypot(dx, dy);
        if (length <= distance) return target;
        double fraction = distance / length;
        return position.translate(dx * fraction, dy * fraction);
    }

    private void returnToPatrol(double distance, WorldPosition body, double bodyRadius) {
        WorldPosition origin = route.get(targetIndex - direction);
        WorldPosition target = route.get(targetIndex);
        if (tryReturnTo(patrolPosition, distance, body, bodyRadius)
            || tryReturnTo(origin, distance, body, bodyRadius)) return;
        if (tryReturnTo(target, distance, body, bodyRadius)) {
            if (behavior == Behavior.PATROL) arriveAtWaypoint();
            return;
        }
        returnBlocked = true;
    }

    private boolean tryReturnTo(WorldPosition target, double distance, WorldPosition body, double bodyRadius) {
        if (!geometry.isCirclePathValid(position, target, radius, body, bodyRadius)) return false;
        position = toward(target, distance);
        returnBlocked = false;
        if (position.equals(target)) {
            patrolPosition = position;
            behavior = Behavior.PATROL;
        }
        return true;
    }

    /** Продвигает только патруль, без полного хода exploration. */
    public void advancePatrol(double deltaSeconds) {
        validateDelta(deltaSeconds);
        if (isDefeated() || behavior != Behavior.PATROL) return;
        if (!geometry.isCirclePathValid(position, route.get(targetIndex), radius)) return;
        advancePatrol(deltaSeconds, null, 0.0);
        patrolPosition = position;
    }

    private void advancePatrol(double deltaSeconds, WorldPosition body, double bodyRadius) {
        double remaining = deltaSeconds;
        while (remaining > 0.0) {
            if (waitRemaining > 0.0) {
                if (finishesPhase(remaining, waitRemaining, config.getPauseSeconds())) {
                    remaining = Math.max(0.0, remaining - waitRemaining);
                    waitRemaining = 0.0;
                } else {
                    waitRemaining -= remaining;
                    return;
                }
            } else {
                WorldPosition target = route.get(targetIndex);
                double dx = target.getX() - position.getX();
                double dy = target.getY() - position.getY();
                double arrivalSeconds = Math.hypot(dx, dy) / config.getPatrolSpeed();
                WorldPosition origin = route.get(targetIndex - direction);
                double segmentSeconds = Math.hypot(target.getX() - origin.getX(),
                    target.getY() - origin.getY()) / config.getPatrolSpeed();
                if (!finishesPhase(remaining, arrivalSeconds, segmentSeconds)) {
                    double fraction = remaining / arrivalSeconds;
                    WorldPosition end = position.translate(dx * fraction, dy * fraction);
                    if (geometry.isCirclePathValid(position, end, radius, body, bodyRadius)) position = end;
                    return;
                }
                if (!geometry.isCirclePathValid(position, target, radius, body, bodyRadius)) return;
                position = target;
                remaining = Math.max(0.0, remaining - arrivalSeconds);
                arriveAtWaypoint();
            }
        }
    }

    private void arriveAtWaypoint() {
        if (targetIndex == route.size() - 1) direction = -1;
        if (targetIndex == 0) direction = 1;
        targetIndex += direction;
        waitRemaining = config.getPauseSeconds();
    }

    private static void validateDelta(double deltaSeconds) {
        if (!Double.isFinite(deltaSeconds) || deltaSeconds < 0.0) {
            throw new IllegalArgumentException("Delta time must be finite and non-negative");
        }
    }

    private static boolean finishesPhase(double available, double needed, double phaseSeconds) {
        // Допуск только на округление времени, в единицах точности полной фазы.
        // Пространственный epsilon сокращал бы путь независимо от этой точности.
        return available >= needed || needed - available <= 8.0 * Math.ulp(phaseSeconds);
    }

    int getTargetIndex() {
        return targetIndex;
    }

    int getDirection() {
        return direction;
    }

    double getWaitRemaining() {
        return waitRemaining;
    }

    private static void requirePositiveFinite(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }
}
