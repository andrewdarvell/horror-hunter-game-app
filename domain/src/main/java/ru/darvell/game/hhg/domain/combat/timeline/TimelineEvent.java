package ru.darvell.game.hhg.domain.combat.timeline;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import ru.darvell.game.hhg.domain.combat.timeline.TimelineState.ActiveAction;
import ru.darvell.game.hhg.domain.tactical.CellCoordinates;

/** Наблюдаемый факт перехода. Поля данных заполняются по типу факта. */
@Value
@Builder(access = AccessLevel.PACKAGE)
public class TimelineEvent {
    Type type;
    long time;
    int participantId;
    ActiveAction action;
    CellCoordinates cell;
    Integer targetId;
    Integer health;
    Long deadline;

    public enum Type {
        ACTION_STARTED, POSITION_CHANGED, MOVE_BLOCKED, CONTACT, HEALTH_CHANGED, DEFEATED,
        ACTION_COMPLETED, ACTION_INTERRUPTED, RECOVERY_STARTED, RECOVERY_EXTENDED,
        RESTRICTION_APPLIED, RECOVERY_ENDED, RESTRICTION_EXPIRED
    }
}
