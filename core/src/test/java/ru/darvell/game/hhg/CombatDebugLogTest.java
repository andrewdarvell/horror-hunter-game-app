package ru.darvell.game.hhg;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import ru.darvell.game.hhg.domain.actor.CreatureBehaviorConfig;
import ru.darvell.game.hhg.domain.actor.CreaturePatrolState;
import ru.darvell.game.hhg.domain.actor.HunterState;
import ru.darvell.game.hhg.domain.scene.WorldSceneState;
import ru.darvell.game.hhg.domain.tactical.*;
import ru.darvell.game.hhg.domain.world.*;
import static org.junit.jupiter.api.Assertions.*;

class CombatDebugLogTest {
    private WorldSceneState scene(int hunterHp, int wolfHp) {
        WorldPosition hunter = new WorldPosition(0, 0), wolf = new WorldPosition(1, 0);
        WorldGeometry geometry = WorldGeometry.empty();
        WorldSceneState scene = new WorldSceneState(new HunterState(hunter, 1, .4, 6).withDamage(6 - hunterHp),
            new CreaturePatrolState(Arrays.asList(wolf, wolf.translate(1, 0)),
                new CreatureBehaviorConfig(1, 1, 1, 1, 1, 2, 1.2, 10), .4, geometry, 4).withDamage(4 - wolfHp),
            new TacticalGrid(-.5, -.5, 1), geometry, SpearClashConfig.v2(Facing.E, 0));
        scene.enterTactical();
        return scene;
    }

    @Test
    void logPreservesEveryFactOnceIncludingSharedDeadlines() {
        WorldSceneState scene = scene(6, 4);
        TacticalAttackResult result = scene.tryAttackCreature(new CellCoordinates(1, 0));
        String log = CombatDebugLog.format("V2", 0, result);
        assertTrue(log.startsWith("COMBAT profile=V2 seed=0 q=0 command=ATTACK planned=4q\n"));
        int index = 0;
        for (TacticalAttackResult.ClashFact fact : result.getClash().getTimeline()) {
            String marker = "  q=" + (result.getStartQuanta() + fact.getOffset()) + " " + fact.getType() + " HP";
            int found = log.indexOf(marker, index);
            assertTrue(found >= index, marker);
            assertEquals(found, log.lastIndexOf(marker), marker);
            index = found + marker.length();
        }
        assertTrue(log.indexOf("q=4 HIT") < log.indexOf("q=4 BITE"));
        assertTrue(log.contains("consequenceRecovery=2/2q"));
        assertTrue(log.contains("HP hunter=6->4 wolf=4->3 damage=2/1"));
        assertTrue(log.contains("wolfRemaining=1"));
        assertTrue(log.contains("stop=HUNTER_READY control=AFTER_PLAYBACK"));
        assertTrue(log.contains("battle=ONGOING"));
        assertSame(result, scene.getLastResolution());
        assertEquals(6, scene.getTacticalTimeQuanta());
    }

    @Test
    void logShowsDefeatInterruptionAndVictoryWithoutReturningTerminalControl() {
        WorldSceneState defeat = scene(1, 4);
        String log = CombatDebugLog.format("V2", 0, defeat.tryAttackCreature(new CellCoordinates(1, 0)));
        assertTrue(log.contains("q=1 BITE"));
        assertTrue(log.contains("q=1 ACTION_INTERRUPTED"));
        assertTrue(log.contains("stop=TERMINAL control=DISABLED_TERMINAL"));
        assertTrue(log.contains("battle=DEFEAT"));
        WorldSceneState victory = scene(6, 1);
        log = CombatDebugLog.format("V2", 0, victory.tryAttackCreature(new CellCoordinates(1, 0)));
        assertTrue(log.contains("q=4 HIT"));
        assertFalse(log.contains("q=4 BITE"));
        assertTrue(log.contains("battle=VICTORY"));
        assertTrue(log.contains("control=EXPLORATION_AFTER_PLAYBACK"));
        assertThrows(IllegalArgumentException.class, () -> CombatDebugLog.format("V2", 0,
            defeat.tryAttackCreature(new CellCoordinates(1, 0))));
    }
}
