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
            if (penetrates(center, radius, obstacle)) {
                return false;
            }
        }
        return true;
    }

    /** Проверяет весь прямолинейный путь круга; касание без проникновения допустимо. */
    public boolean isCirclePathValid(WorldPosition from, WorldPosition to, double radius) {
        // Проверяем оба аргумента даже тогда, когда первая позиция заблокирована.
        boolean startValid = isCirclePositionValid(from, radius);
        boolean endValid = isCirclePositionValid(to, radius);
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double length = Math.hypot(dx, dy);
        if (!Double.isFinite(length)) {
            throw new IllegalArgumentException("Path length must be finite");
        }
        if (!startValid || !endValid) return false;
        if (length == 0.0) return true;

        for (StaticAabb obstacle : obstacles) {
            if (intersectsSegment(from, dx, dy, obstacle)
                || pointSegmentDistance(obstacle.getX(), obstacle.getY(), from, dx, dy, length) < radius
                || pointSegmentDistance(obstacle.getMaxX(), obstacle.getY(), from, dx, dy, length) < radius
                || pointSegmentDistance(obstacle.getX(), obstacle.getMaxY(), from, dx, dy, length) < radius
                || pointSegmentDistance(obstacle.getMaxX(), obstacle.getMaxY(), from, dx, dy, length) < radius) {
                return false;
            }
        }
        return true;
    }

    private static boolean intersectsSegment(WorldPosition from, double dx, double dy, StaticAabb box) {
        double enter = 0.0;
        double exit = 1.0;
        if (dx == 0.0) {
            if (from.getX() < box.getX() || from.getX() > box.getMaxX()) return false;
        } else {
            double a = (box.getX() - from.getX()) / dx;
            double b = (box.getMaxX() - from.getX()) / dx;
            enter = Math.max(enter, Math.min(a, b));
            exit = Math.min(exit, Math.max(a, b));
        }
        if (dy == 0.0) {
            if (from.getY() < box.getY() || from.getY() > box.getMaxY()) return false;
        } else {
            double a = (box.getY() - from.getY()) / dy;
            double b = (box.getMaxY() - from.getY()) / dy;
            enter = Math.max(enter, Math.min(a, b));
            exit = Math.min(exit, Math.max(a, b));
        }
        return enter <= exit;
    }

    private static double pointSegmentDistance(
        double x, double y, WorldPosition from, double dx, double dy, double length
    ) {
        double unitX = dx / length;
        double unitY = dy / length;
        double relativeX = x - from.getX();
        double relativeY = y - from.getY();
        double projection = relativeX * unitX + relativeY * unitY;
        if (projection <= 0.0) return Math.hypot(relativeX, relativeY);
        if (projection >= length) return Math.hypot(x - (from.getX() + dx), y - (from.getY() + dy));
        return Math.abs(relativeX * unitY - relativeY * unitX);
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
        if (desiredX != 0.0 && desiredY != 0.0 && resolvedPosition.equals(currentPosition)) {
            return resolveCornerSlide(currentPosition, radius, desiredX, desiredY);
        }
        return resolvedPosition;
    }

    /** Сохраняет касательную составляющую, которую обе мировые оси могли заблокировать. */
    private WorldPosition resolveCornerSlide(
        WorldPosition position, double radius, double desiredX, double desiredY
    ) {
        WorldPosition bestPosition = position;
        double bestProgress = 0.0;
        double minimumSlide = Math.hypot(desiredX, desiredY) * 1.0e-12;
        WorldPosition xCandidate = position.translate(desiredX, 0.0);
        WorldPosition yCandidate = position.translate(0.0, desiredY);
        for (StaticAabb obstacle : obstacles) {
            if (!penetrates(xCandidate, radius, obstacle)
                && !penetrates(yCandidate, radius, obstacle)) {
                continue;
            }
            double normalX = position.getX()
                - clamp(position.getX(), obstacle.getX(), obstacle.getMaxX());
            double normalY = position.getY()
                - clamp(position.getY(), obstacle.getY(), obstacle.getMaxY());
            double normalLength = Math.hypot(normalX, normalY);
            if (normalLength == 0.0) continue;
            normalX /= normalLength;
            normalY /= normalLength;
            if (desiredX * normalX + desiredY * normalY >= 0.0) continue;

            // t = (-normalY, normalX); проекция без нормализации скорости скольжения.
            double tangentDistance = -desiredX * normalY + desiredY * normalX;
            if (Math.abs(tangentDistance) <= minimumSlide) continue;
            double slideX = -normalY * tangentDistance;
            double slideY = normalX * tangentDistance;
            double progress = slideX * desiredX + slideY * desiredY;
            WorldPosition candidate = position.translate(slideX, slideY);
            if (progress > bestProgress && isCirclePositionValid(candidate, radius)) {
                bestPosition = candidate;
                bestProgress = progress;
            }
        }
        return bestPosition;
    }

    private static boolean penetrates(WorldPosition center, double radius, StaticAabb obstacle) {
        double deltaX = center.getX() - clamp(center.getX(), obstacle.getX(), obstacle.getMaxX());
        double deltaY = center.getY() - clamp(center.getY(), obstacle.getY(), obstacle.getMaxY());
        return deltaX * deltaX + deltaY * deltaY < radius * radius;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }
}
