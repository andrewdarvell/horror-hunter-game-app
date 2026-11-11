package ru.darvell.game.hhg.domain.world;

import java.util.Objects;

/** Непрерывная позиция в единой системе мировых координат. */
public final class WorldPosition {
    private final double x;
    private final double y;

    public WorldPosition(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public WorldPosition translate(double deltaX, double deltaY) {
        return new WorldPosition(x + deltaX, y + deltaY);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof WorldPosition)) return false;
        WorldPosition that = (WorldPosition) other;
        return Double.compare(x, that.x) == 0 && Double.compare(y, that.y) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public String toString() {
        return "WorldPosition{" + "x=" + x + ", y=" + y + '}';
    }
}
