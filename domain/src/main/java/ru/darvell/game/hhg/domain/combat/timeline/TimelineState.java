package ru.darvell.game.hhg.domain.combat.timeline;

import lombok.Value;
import ru.darvell.game.hhg.domain.tactical.CellCoordinates;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Неизменяемый снимок между пакетами событий. Участники упорядочены по ID. */
@Value
public class TimelineState {
    long now;
    List<Participant> participants;
    Set<TimelineAction> commands;

    public TimelineState(long now, Participant first, Participant second, Set<TimelineAction> commands) {
        if (now < 0) {
            throw new IllegalArgumentException("Timeline time must be nonnegative");
        }
        Objects.requireNonNull(first, "First participant");
        Objects.requireNonNull(second, "Second participant");
        if (first.id == second.id || first.role == second.role) {
            throw new IllegalArgumentException("Participants must have distinct IDs and roles");
        }
        if (first.health > 0 && second.health > 0 && first.cell.equals(second.cell)) {
            throw new IllegalArgumentException("Living participants must occupy distinct cells");
        }
        first.validateAt(now);
        second.validateAt(now);
        Set<TimelineAction> commandCopy = new HashSet<>(commands);
        if (commandCopy.contains(null)) {
            throw new IllegalArgumentException("Commands must be present");
        }
        this.now = now;
        this.participants = Collections.unmodifiableList(first.id < second.id
            ? Arrays.asList(first, second) : Arrays.asList(second, first));
        this.commands = Collections.unmodifiableSet(commandCopy);
    }

    public Participant getHunter() {
        return participants.get(participants.get(0).role == Role.HUNTER ? 0 : 1);
    }

    public Participant getOpponent() {
        return participants.get(participants.get(0).role == Role.OPPONENT ? 0 : 1);
    }

    public enum Role { HUNTER, OPPONENT }

    @Value
    public static class Participant {
        int id;
        Role role;
        CellCoordinates cell;
        int health;
        ActiveAction action;
        /** Абсолютные сроки. null означает отсутствие таймера. */
        Long recoveryUntil;
        Long restrictionUntil;

        public Participant(int id, Role role, CellCoordinates cell, int health, ActiveAction action) {
            this(id, role, cell, health, action, null, null);
        }

        public Participant(int id, Role role, CellCoordinates cell, int health, ActiveAction action,
                           Long recoveryUntil, Long restrictionUntil) {
            if (health < 0 || (health == 0 && (action != null || recoveryUntil != null || restrictionUntil != null))) {
                throw new IllegalArgumentException("Invalid health or activity for defeated participant");
            }
            if (action != null && recoveryUntil != null) {
                throw new IllegalArgumentException("Action and recovery cannot overlap");
            }
            this.id = id;
            this.role = Objects.requireNonNull(role, "Participant role");
            this.cell = Objects.requireNonNull(cell, "Participant cell");
            this.health = health;
            this.action = action;
            this.recoveryUntil = recoveryUntil;
            this.restrictionUntil = restrictionUntil;
        }

        Participant withAction(ActiveAction action) {
            return new Participant(id, role, cell, health, action, recoveryUntil, restrictionUntil);
        }

        Participant withCell(CellCoordinates cell) {
            return new Participant(id, role, cell, health, action, recoveryUntil, restrictionUntil);
        }

        private void validateAt(long now) {
            if (action != null && (action.start > now || action.finish <= now)) {
                throw new IllegalArgumentException("Active action must span current time");
            }
            if ((recoveryUntil != null && recoveryUntil <= now) || (restrictionUntil != null && restrictionUntil <= now)) {
                throw new IllegalArgumentException("Active timers must have future deadlines");
            }
            if (restrictionUntil != null && restrictionUntil - now > CombatTimeline.QUANTA_PER_TICK) {
                throw new IllegalArgumentException("Restriction cannot have more than one tick remaining");
            }
        }

        Long nextDeadline() {
            Long busyUntil = action == null ? recoveryUntil : Long.valueOf(action.finish);
            if (restrictionUntil == null || (busyUntil != null && busyUntil <= restrictionUntil)) {
                return busyUntil;
            }
            return restrictionUntil;
        }
    }

    @Value
    public static class ActiveAction {
        TimelineAction command;
        long start;
        long finish;

        public ActiveAction(TimelineAction command, long start) {
            if (start < 0) {
                throw new IllegalArgumentException("Action start must be nonnegative");
            }
            this.command = Objects.requireNonNull(command, "Active command");
            this.start = start;
            this.finish = Math.addExact(start, command.getDuration());
        }
    }
}
