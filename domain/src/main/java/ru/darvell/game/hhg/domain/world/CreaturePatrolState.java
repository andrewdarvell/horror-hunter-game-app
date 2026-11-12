package ru.darvell.game.hhg.domain.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Патруль по неизменяемому двустороннему маршруту с паузами в waypoint. */
public final class CreaturePatrolState {
    private final List<WorldPosition> route;
    private final WorldGeometry geometry;
    private final double movementSpeed;
    private final double radius;
    private final double pauseSeconds;
    private final double detectionRadius;
    private boolean alerted;
    private WorldPosition lastSeenHunterPosition;
    private WorldPosition position;
    private int targetIndex = 1;
    private int direction = 1;
    private double waitRemaining;

    public CreaturePatrolState(List<WorldPosition> route, double movementSpeed, double radius,
                               double pauseSeconds, double detectionRadius, WorldGeometry geometry) {
        if (route == null || route.size() < 2 || route.size() > 3 || geometry == null) {
            throw new IllegalArgumentException("Patrol needs two or three waypoints and world geometry");
        }
        requirePositiveFinite(movementSpeed, "Movement speed");
        requirePositiveFinite(radius, "Radius");
        requirePositiveFinite(pauseSeconds, "Pause duration");
        requirePositiveFinite(detectionRadius, "Detection radius");
        this.detectionRadius = detectionRadius;
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
            requirePositiveFinite(length / movementSpeed, "Segment duration");
            if (!geometry.isCirclePathValid(from, to, radius)) {
                throw new IllegalArgumentException("Patrol segment must be valid in world geometry");
            }
        }
        this.route = Collections.unmodifiableList(copy);
        this.geometry = geometry;
        this.movementSpeed = movementSpeed;
        this.radius = radius;
        this.pauseSeconds = pauseSeconds;
        position = copy.get(0);
        waitRemaining = pauseSeconds;
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

    /** Последняя замеченная позиция; null до первого обнаружения. */
    public WorldPosition getLastSeenHunterPosition() {
        return lastSeenHunterPosition;
    }

    /** Проверяет обнаружение до шага патруля, в том числе при нулевом интервале. */
    public void update(double deltaSeconds, WorldPosition hunterPosition) {
        if (!Double.isFinite(deltaSeconds) || deltaSeconds < 0.0) {
            throw new IllegalArgumentException("Delta time must be finite and non-negative");
        }
        checkDetection(hunterPosition);
        advancePatrol(deltaSeconds);
    }

    void applyTacticalPosition(WorldPosition center) {
        position = center;
    }

    boolean checkDetection(WorldPosition hunterPosition) {
        if (hunterPosition == null || !Double.isFinite(hunterPosition.getX())
            || !Double.isFinite(hunterPosition.getY())) {
            throw new IllegalArgumentException("Hunter position must be present and finite");
        }
        double distance = Math.hypot(hunterPosition.getX() - position.getX(),
            hunterPosition.getY() - position.getY());
        if (!Double.isFinite(distance)) {
            throw new IllegalArgumentException("Hunter distance must be finite");
        }
        boolean previouslyAlerted = alerted;
        if (distance <= detectionRadius && geometry.hasLineOfSight(position, hunterPosition)) {
            alerted = true;
            lastSeenHunterPosition = hunterPosition;
        }
        return !previouslyAlerted && alerted;
    }

    void advancePatrol(double deltaSeconds) {
        if (alerted) return;

        double remaining = deltaSeconds;
        while (remaining > 0.0) {
            if (waitRemaining > 0.0) {
                if (finishesPhase(remaining, waitRemaining, pauseSeconds)) {
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
                double arrivalSeconds = Math.hypot(dx, dy) / movementSpeed;
                WorldPosition origin = route.get(targetIndex - direction);
                double segmentSeconds = Math.hypot(target.getX() - origin.getX(),
                    target.getY() - origin.getY()) / movementSpeed;
                if (!finishesPhase(remaining, arrivalSeconds, segmentSeconds)) {
                    double fraction = remaining / arrivalSeconds;
                    position = position.translate(dx * fraction, dy * fraction);
                    return;
                }
                // Присваивание waypoint исключает накопление overshoot при прибытии.
                position = target;
                remaining = Math.max(0.0, remaining - arrivalSeconds);
                if (targetIndex == route.size() - 1) direction = -1;
                if (targetIndex == 0) direction = 1;
                targetIndex += direction;
                waitRemaining = pauseSeconds;
            }
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
