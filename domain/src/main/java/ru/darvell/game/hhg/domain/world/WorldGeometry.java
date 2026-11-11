package ru.darvell.game.hhg.domain.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Статическая геометрия текущего мира. */
public final class WorldGeometry {
    private static final WorldGeometry EMPTY = new WorldGeometry(Collections.emptyList());

    private final List<StaticAabb> obstacles;

    public WorldGeometry(List<StaticAabb> obstacles) {
        if (obstacles == null) {
            throw new IllegalArgumentException("Obstacles must not be null");
        }
        ArrayList<StaticAabb> obstacleCopy = new ArrayList<>(obstacles.size());
        for (StaticAabb obstacle : obstacles) {
            if (obstacle == null) {
                throw new IllegalArgumentException("Obstacle must not be null");
            }
            obstacleCopy.add(obstacle);
        }
        this.obstacles = Collections.unmodifiableList(obstacleCopy);
    }

    public List<StaticAabb> getObstacles() {
        return obstacles;
    }

    public static WorldGeometry empty() {
        return EMPTY;
    }

    public boolean isCirclePositionValid(WorldPosition center, double radius) {
        if (center == null) {
            throw new IllegalArgumentException("Circle center must not be null");
        }
        if (!Double.isFinite(center.getX()) || !Double.isFinite(center.getY())) {
            throw new IllegalArgumentException("Circle center must be finite");
        }
        if (!Double.isFinite(radius) || radius <= 0.0) {
            throw new IllegalArgumentException("Circle radius must be finite and positive");
        }

        for (StaticAabb obstacle : obstacles) {
            double closestX = clamp(center.getX(), obstacle.getX(), obstacle.getMaxX());
            double closestY = clamp(center.getY(), obstacle.getY(), obstacle.getMaxY());
            double deltaX = center.getX() - closestX;
            double deltaY = center.getY() - closestY;
            if (deltaX * deltaX + deltaY * deltaY < radius * radius) {
                return false;
            }
        }
        return true;
    }

    public WorldPosition resolveMovement(
        WorldPosition currentPosition,
        double radius,
        double desiredX,
        double desiredY
    ) {
        if (!Double.isFinite(desiredX) || !Double.isFinite(desiredY)) {
            throw new IllegalArgumentException("Desired movement must be finite");
        }

        WorldPosition resolvedPosition = currentPosition;
        WorldPosition xCandidate = resolvedPosition.translate(desiredX, 0.0);
        if (isCirclePositionValid(xCandidate, radius)) {
            resolvedPosition = xCandidate;
        }

        WorldPosition yCandidate = resolvedPosition.translate(0.0, desiredY);
        if (isCirclePositionValid(yCandidate, radius)) {
            resolvedPosition = yCandidate;
        }
        return resolvedPosition;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }
}
