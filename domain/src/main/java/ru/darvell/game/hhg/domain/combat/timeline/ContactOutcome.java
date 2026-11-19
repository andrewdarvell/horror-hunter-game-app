package ru.darvell.game.hhg.domain.combat.timeline;

import lombok.NonNull;
import lombok.Value;

/** Заданный исход проверочного контакта и последствия для обеих сторон. */
@Value
public class ContactOutcome {
    @NonNull Consequences initiator;
    @NonNull Consequences target;

    @Value
    public static class Consequences {
        int damage;
        boolean interrupt;
        long recoveryDuration;
        boolean restriction;

        public Consequences(int damage, boolean interrupt, long recoveryDuration, boolean restriction) {
            if (damage < 0 || recoveryDuration < 0) {
                throw new IllegalArgumentException("Damage and recovery duration must be nonnegative");
            }
            this.damage = damage;
            this.interrupt = interrupt;
            this.recoveryDuration = recoveryDuration;
            this.restriction = restriction;
        }
    }
}
