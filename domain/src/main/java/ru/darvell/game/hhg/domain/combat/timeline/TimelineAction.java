package ru.darvell.game.hhg.domain.combat.timeline;

import lombok.Value;
import ru.darvell.game.hhg.domain.tactical.CellCoordinates;

import java.util.Objects;

/** Команда с длительностью в квантах тактического времени. */
@Value
public class TimelineAction {
    Kind kind;
    long duration;
    CellCoordinates cell;
    ContactOutcome outcome;

    private TimelineAction(Kind kind, long duration, CellCoordinates cell, ContactOutcome outcome) {
        if (duration <= 0) {
            throw new IllegalArgumentException("Action duration must be positive");
        }
        this.kind = kind;
        this.duration = duration;
        this.cell = cell;
        this.outcome = outcome;
    }

    public static TimelineAction waitFor(long duration) {
        return new TimelineAction(Kind.WAIT, duration, null, null);
    }

    public static TimelineAction move(long duration, CellCoordinates destination) {
        return new TimelineAction(Kind.MOVE, duration, Objects.requireNonNull(destination, "Destination"), null);
    }

    public static TimelineAction contact(long duration, CellCoordinates targetCell, ContactOutcome outcome) {
        return new TimelineAction(Kind.CONTACT, duration,
            Objects.requireNonNull(targetCell, "Contact cell"), Objects.requireNonNull(outcome, "Contact outcome"));
    }

    public enum Kind { WAIT, MOVE, CONTACT }
}
