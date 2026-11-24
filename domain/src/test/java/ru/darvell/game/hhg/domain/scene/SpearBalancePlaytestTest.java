package ru.darvell.game.hhg.domain.scene;

import java.util.Arrays;
import java.util.EnumMap;
import org.junit.jupiter.api.Test;
import ru.darvell.game.hhg.domain.actor.*;
import ru.darvell.game.hhg.domain.tactical.*;
import ru.darvell.game.hhg.domain.world.*;
import static org.junit.jupiter.api.Assertions.*;

/** Fixed seed set chosen before comparison: 0..999; open map, H0/W3, E, initial cursor0. */
class SpearBalancePlaytestTest {
    private WorldSceneState scene(long seed, boolean v2) {
        WorldPosition wolf = new WorldPosition(3, 0);
        WorldSceneState scene = new WorldSceneState(new HunterState(new WorldPosition(0, 0), 1, .4, v2 ? 6 : 4),
            new CreaturePatrolState(Arrays.asList(wolf, wolf.translate(1, 0)),
                new CreatureBehaviorConfig(1, 1, 1, 1, 1, 2, 1.2, 10), .4, WorldGeometry.empty(), v2 ? 4 : 2),
            new TacticalGrid(-.5, -.5, 1), WorldGeometry.empty(),
            v2 ? SpearClashConfig.v2(Facing.E, seed) : new SpearClashConfig(Facing.E, seed));
        scene.enterTactical();
        return scene;
    }

    @Test
    void fixedSeedsCompareFirstContactWithoutConflatingConditionalWeightsAndFrequency() {
        for (boolean v2 : new boolean[]{false, true}) {
            EnumMap<SpearClashState.Result, Integer> counts = new EnumMap<>(SpearClashState.Result.class);
            for (long seed = 0; seed < 1000; seed++) {
                WorldSceneState scene = scene(seed, v2);
                TacticalAttackResult result = scene.trySpearAction(TacticalAttackResult.Kind.BRACE, null);
                SpearClashState.Result outcome = result.getClash().getLastContact().getContact();
                counts.put(outcome, counts.getOrDefault(outcome, 0) + 1);
                assertEquals(1, scene.getSpearClashState().getRandomCursor());
                WorldSceneState replay = scene(seed, v2);
                assertEquals(result.getClash(), replay.trySpearAction(TacticalAttackResult.Kind.BRACE, null).getClash());
            }
            assertEquals(1000, counts.values().stream().mapToInt(Integer::intValue).sum());
            assertEquals(3, counts.size());
            System.out.println("FIXED_SEEDS_0_999 " + (v2 ? "V2" : "REGRESSION") + " " + counts);
        }
    }

    @Test
    void repeatedPaidDecisionsEndBattlesAndRepelDoesNotGiveFreeFinishingAttack() {
        int longest = 0;
        for (long seed = 0; seed < 64; seed++) {
            WorldSceneState scene = scene(seed, true);
            int commands = 0;
            while (scene.getMode() == SceneMode.TACTICAL && scene.getHunterHealth() > 0) {
                assertTrue(commands++ < 128, "Finite battle seed=" + seed);
                CellCoordinates target = new TacticalGrid(-.5, -.5, 1).worldToCell(scene.getCreaturePosition());
                long before = scene.getTacticalTimeQuanta();
                if (scene.attackAvailability(target) == TacticalAttackResult.Outcome.HIT) scene.tryAttackCreature(target);
                else scene.trySpearAction(scene.getSpearClashState().isBraced()
                    ? TacticalAttackResult.Kind.LOWER_SPEAR : TacticalAttackResult.Kind.BRACE, null);
                assertTrue(scene.getTacticalTimeQuanta() > before);
            }
            longest = Math.max(longest, commands);
        }
        System.out.println("LONG_BATTLES_0_63 MAX_COMMANDS=" + longest);
        WorldSceneState scene = scene(3, true);
        scene.trySpearAction(TacticalAttackResult.Kind.BRACE, null);
        assertEquals(6, scene.getTacticalTimeQuanta());
        assertEquals(3, scene.getCreatureHealth());
        assertEquals(2, scene.getSpearClashState().getWolfRemaining());
        assertEquals(TacticalAttackResult.Outcome.OUT_OF_RANGE, scene.attackAvailability(new CellCoordinates(3, 0)));
        assertEquals(TacticalMoveResult.SUCCESS, scene.tryMoveHunterTo(new CellCoordinates(1, 0)));
        assertEquals(8, scene.getTacticalTimeQuanta());
        assertEquals(SpearClashState.WolfPhase.PREPARING, scene.getSpearClashState().getWolfPhase());
        assertEquals(TacticalAttackResult.Outcome.OUT_OF_RANGE, scene.attackAvailability(new CellCoordinates(3, 0)));
        System.out.println("REPEL_TRACE q4 HP6/3 W3; q6 hunterReady wolfRecovery2 attackOutOfRange; q8 H1 wolfPreparing attackOutOfRange");
    }
}
