package ru.darvell.game.hhg.domain.world;

import java.util.Objects;

/** Устойчивые целочисленные координаты клетки фиксированной тактической сетки. */
public final class CellCoordinates {
    private final int x;
    private final int y;

    public CellCoordinates(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CellCoordinates)) return false;
        CellCoordinates that = (CellCoordinates) other;
        return x == that.x && y == that.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public String toString() {
        return "CellCoordinates{" + "x=" + x + ", y=" + y + '}';
    }
}
