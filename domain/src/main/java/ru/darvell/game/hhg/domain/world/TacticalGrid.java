package ru.darvell.game.hhg.domain.world;

/** Фиксированная тактическая сетка, выраженная в мировых единицах. */
public final class TacticalGrid {
    private final double originX;
    private final double originY;
    private final double cellSize;

    public TacticalGrid(double originX, double originY, double cellSize) {
        if (!Double.isFinite(originX) || !Double.isFinite(originY)) {
            throw new IllegalArgumentException("Grid origin must be finite");
        }
        if (!Double.isFinite(cellSize) || cellSize <= 0.0) {
            throw new IllegalArgumentException("Cell size must be finite and positive");
        }
        this.originX = originX;
        this.originY = originY;
        this.cellSize = cellSize;
    }

    public double getOriginX() {
        return originX;
    }

    public double getOriginY() {
        return originY;
    }

    public double getCellSize() {
        return cellSize;
    }

    public CellCoordinates worldToCell(WorldPosition position) {
        int cellX = (int) Math.floor((position.getX() - originX) / cellSize);
        int cellY = (int) Math.floor((position.getY() - originY) / cellSize);
        return new CellCoordinates(cellX, cellY);
    }

    public WorldPosition cellToWorldCenter(CellCoordinates cell) {
        double centerX = originX + (cell.getX() + 0.5) * cellSize;
        double centerY = originY + (cell.getY() + 0.5) * cellSize;
        return new WorldPosition(centerX, centerY);
    }
}
