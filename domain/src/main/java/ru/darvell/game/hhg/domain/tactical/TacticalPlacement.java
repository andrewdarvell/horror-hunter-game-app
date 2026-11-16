package ru.darvell.game.hhg.domain.tactical;

/** Неизменяемая занятость охотника и, пока оно живо, существа. */
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

    private TacticalPlacement(CellCoordinates hunterCell) {
        if (hunterCell == null) throw new IllegalArgumentException("Hunter cell required");
        this.hunterCell = hunterCell;
        this.creatureCell = null;
    }

    public static TacticalPlacement hunterOnly(CellCoordinates hunterCell) {
        return new TacticalPlacement(hunterCell);
    }

    public java.util.Optional<CellCoordinates> creatureCell() {
        return java.util.Optional.ofNullable(creatureCell);
    }

    public CellCoordinates getHunterCell() { return hunterCell; }
    public CellCoordinates getCreatureCell() {
        return creatureCell().orElseThrow(() -> new IllegalStateException("No creature occupant"));
    }
}
