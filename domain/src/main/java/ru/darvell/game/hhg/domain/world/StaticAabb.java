package ru.darvell.game.hhg.domain.world;

import java.util.Objects;

/** Неизменяемое статическое AABB-препятствие в мировых координатах. */
public final class StaticAabb {
    private final double x;
    private final double y;
    private final double width;
    private final double height;

    public StaticAabb(double x, double y, double width, double height) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("AABB position must be finite");
        }
        if (!Double.isFinite(width) || !Double.isFinite(height)
            || width <= 0.0 || height <= 0.0) {
            throw new IllegalArgumentException("AABB dimensions must be finite and positive");
        }
        if (!Double.isFinite(x + width) || !Double.isFinite(y + height)) {
            throw new IllegalArgumentException("AABB bounds must be finite");
        }
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return height;
    }

    public double getMaxX() {
        return x + width;
    }

    public double getMaxY() {
        return y + height;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof StaticAabb)) return false;
        StaticAabb that = (StaticAabb) other;
        return Double.compare(x, that.x) == 0
            && Double.compare(y, that.y) == 0
            && Double.compare(width, that.width) == 0
            && Double.compare(height, that.height) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, width, height);
    }

    @Override
    public String toString() {
        return "StaticAabb{" + "x=" + x + ", y=" + y
            + ", width=" + width + ", height=" + height + '}';
    }
}
