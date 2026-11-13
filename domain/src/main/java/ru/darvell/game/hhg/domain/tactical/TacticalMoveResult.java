package ru.darvell.game.hhg.domain.tactical;

/** Результат одной атомарной команды клеточного движения. */
public enum TacticalMoveResult {
    SUCCESS, NOT_TACTICAL, INVALID_INPUT, SAME_CELL, NOT_ADJACENT,
    OCCUPIED, BLOCKED_CENTER, BLOCKED_PATH
}
