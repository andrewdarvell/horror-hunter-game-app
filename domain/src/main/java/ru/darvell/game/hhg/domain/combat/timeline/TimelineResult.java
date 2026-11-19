package ru.darvell.game.hhg.domain.combat.timeline;

import lombok.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Value
public class TimelineResult {
    TimelineState state;
    Stop stop;
    List<TimelineEvent> events;
    Set<TimelineAction> availableCommands;

    TimelineResult(TimelineState state, Stop stop, List<TimelineEvent> events, Set<TimelineAction> availableCommands) {
        this.state = state;
        this.stop = stop;
        this.events = Collections.unmodifiableList(new ArrayList<>(events));
        this.availableCommands = availableCommands;
    }

    public enum Stop { DECISION, TERMINAL, NO_PROGRESS }
}
