package ru.darvell.game.hhg.domain.tactical;

import java.util.EnumMap;
import org.junit.jupiter.api.Test;
import ru.darvell.game.hhg.domain.tactical.SpearCombatProfile.Consequence;
import ru.darvell.game.hhg.domain.tactical.SpearCombatProfile.Id;
import ru.darvell.game.hhg.domain.tactical.SpearClashState.Result;
import static org.junit.jupiter.api.Assertions.*;

class SpearCombatProfileTest {
    @Test
    void recoveryValidationDistinguishesHunterAndWolfForEveryOutcome() {
        EnumMap<Result, Consequence> values = new EnumMap<>(Result.class);
        for (Result result : Result.values()) values.put(result, new Consequence(0, 0, 0, 1));
        Consequence bite = new Consequence(0, 0, 0, 1);
        SpearCombatProfile profile = new SpearCombatProfile(Id.V2, values, bite, bite, 1, 1);
        for (Result result : Result.values()) {
            assertEquals(0, profile.consequence(result).getHunterRecovery());
            assertEquals(1, profile.consequence(result).getWolfRecovery());
            for (long duration : new long[]{0, -1}) {
                assertThrows(IllegalArgumentException.class, () -> new Consequence(0, 0, 0, duration));
            }
        }
        values.clear();
        assertEquals(1, profile.consequence(Result.BLOCKED).getWolfRecovery());
        assertThrows(IllegalArgumentException.class, () -> new Consequence(0, 0, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> new Consequence(-1, 0, 0, 1));
        assertThrows(IllegalArgumentException.class,
            () -> new SpearCombatProfile(Id.V2, values, bite, bite, 1, 1));
    }

    @Test
    void profilesAndOddTimingsAreExplicitAndValidated() {
        assertFalse(new SpearClashConfig(Facing.E, 0).getProfile().isV2());
        assertTrue(SpearClashConfig.v2(Facing.E, 0).getProfile().isV2());
        assertEquals(6, SpearCombatProfile.regression().consequence(Result.REPELLED).getWolfRecovery());
        assertEquals(4, SpearCombatProfile.v2().consequence(Result.REPELLED).getWolfRecovery());
        assertEquals(1, new SpearClashConfig(Facing.E, 0, 1, 1, 1, 0, 0, 0).getFlightQuanta());
        assertThrows(IllegalArgumentException.class,
            () -> new SpearClashConfig(Facing.E, 0, 0, 1, 1, 0, 0, 0));
    }
}
