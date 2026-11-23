package ru.darvell.game.hhg.domain.tactical;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Facing {
    E(1, 0), NE(1, 1), N(0, 1), NW(-1, 1),
    W(-1, 0), SW(-1, -1), S(0, -1), SE(1, -1);

    private final int dx;
    private final int dy;

    public int stepsTo(Facing target) {
        int difference = Math.abs(ordinal() - target.ordinal());
        return Math.min(difference, 8 - difference);
    }

    public static Facing along(int dx, int dy) {
        for (Facing facing : values()) {
            if (facing.dx == dx && facing.dy == dy) return facing;
        }
        throw new IllegalArgumentException("Invalid facing vector");
    }
}
