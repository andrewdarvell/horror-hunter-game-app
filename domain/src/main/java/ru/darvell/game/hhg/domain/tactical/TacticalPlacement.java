package ru.darvell.game.hhg.domain.tactical;

/** Полная неизменяемая занятость двух клеток. */
public final class TacticalPlacement {
    private final CellCoordinates hunterCell;
    private final CellCoordinates creatureCell;

    public TacticalPlacement(CellCoordinates hunterCell, CellCoordinates creatureCell) {
        if (hunterCell == null || creatureCell == null || hunterCell.equals(creatureCell)) {
            throw new IllegalArgumentException("Placement requires two distinct cells");
        }
        this.hunterCell = hunterCell;
        this.creatureCell = creatureCell;
    }

    public CellCoordinates getHunterCell() { return hunterCell; }
    public CellCoordinates getCreatureCell() { return creatureCell; }
}
