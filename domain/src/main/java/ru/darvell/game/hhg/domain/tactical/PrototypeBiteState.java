package ru.darvell.game.hhg.domain.tactical;

import lombok.EqualsAndHashCode;

/** Read-only проекция фазы и остатка в целых тактических единицах. */
@EqualsAndHashCode
public final class PrototypeBiteState {
    public enum Phase { READY, PREPARING, RECOVERING }
    private final Phase phase;
    private final int remainingUnits;

    public PrototypeBiteState(Phase phase, int remainingUnits) {
        if (phase == null || (phase == Phase.READY ? remainingUnits != 0
            : phase == Phase.PREPARING ? remainingUnits != 1 : remainingUnits < 1 || remainingUnits > 3)) {
            throw new IllegalArgumentException("Invalid prototype bite phase duration");
        }
        this.phase = phase;
        this.remainingUnits = remainingUnits;
    }
    public Phase getPhase() { return phase; }
    public int getRemainingUnits() { return remainingUnits; }
}
