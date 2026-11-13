package ru.darvell.game.hhg;

import ru.darvell.game.hhg.domain.world.WorldPosition;

/** Только визуальный переход двух участников; не изменяет игровое состояние. */
final class TacticalEntryTransition {
    static final double DURATION_SECONDS = 0.22;

    private final WorldPosition hunterStart;
    private final WorldPosition creatureStart;
    private final WorldPosition hunterEnd;
    private final WorldPosition creatureEnd;
    private final double duration;
    private double elapsed;

    TacticalEntryTransition(WorldPosition hunterStart, WorldPosition creatureStart,
                            WorldPosition hunterEnd, WorldPosition creatureEnd) {
        this(hunterStart, creatureStart, hunterEnd, creatureEnd, DURATION_SECONDS);
    }

    static TacticalEntryTransition forHunterMove(WorldPosition start, WorldPosition end,
                                                 WorldPosition creature) {
        return new TacticalEntryTransition(start, creature, end, creature, 0.18);
    }

    private TacticalEntryTransition(WorldPosition hunterStart, WorldPosition creatureStart,
                                    WorldPosition hunterEnd, WorldPosition creatureEnd, double duration) {
        this.duration = duration;
        this.hunterStart = hunterStart;
        this.creatureStart = creatureStart;
        this.hunterEnd = hunterEnd;
        this.creatureEnd = creatureEnd;
    }

    void advance(double delta) {
        if (!Double.isFinite(delta) || delta <= 0.0 || isComplete()) return;
        double remaining = duration - elapsed;
        elapsed = delta >= remaining ? duration : elapsed + delta;
    }

    boolean isComplete() {
        return elapsed >= duration;
    }

    WorldPosition getHunterPosition() {
        return interpolate(hunterStart, hunterEnd);
    }

    WorldPosition getCreaturePosition() {
        return interpolate(creatureStart, creatureEnd);
    }

    private WorldPosition interpolate(WorldPosition start, WorldPosition end) {
        if (elapsed == 0.0) return start;
        if (isComplete()) return end;
        double u = elapsed / duration;
        double s = u * u * (3.0 - 2.0 * u);
        return new WorldPosition(start.getX() + (end.getX() - start.getX()) * s,
            start.getY() + (end.getY() - start.getY()) * s);
    }
}
