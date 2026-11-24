package ru.darvell.game.hhg;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import ru.darvell.game.hhg.domain.actor.*;
import ru.darvell.game.hhg.domain.scene.*;
import ru.darvell.game.hhg.domain.tactical.*;
import ru.darvell.game.hhg.domain.world.*;

import static org.junit.jupiter.api.Assertions.*;

class SpearPlaybackTest {
    private TacticalAttackResult result(long seed, int wolfHp, boolean side) {
        WorldGeometry geometry = WorldGeometry.empty();
        WorldPosition wolf = side ? new WorldPosition(0, 3) : new WorldPosition(3, 0);
        WorldSceneState scene = new WorldSceneState(
            new HunterState(new WorldPosition(0, 0), 1, .4).withDamage(side ? 3 : 0),
            new CreaturePatrolState(Arrays.asList(wolf, wolf.translate(1, 0)),
                new CreatureBehaviorConfig(1, 1, 1, 1, 1, 2, 1.2, 10), .4, geometry).withDamage(2 - wolfHp),
            new TacticalGrid(-.5, -.5, 1), geometry, new SpearClashConfig(Facing.E, seed));
        scene.enterTactical();
        return scene.trySpearAction(TacticalAttackResult.Kind.BRACE, null);
    }

    @Test
    void approachContactAftermathAndDeathAreSeparateForEveryRoute() {
        for (long seed : new long[] {3, 1, 0}) {
            for (int hp : new int[] {1, 2}) {
                TacticalAttackResult result = result(seed, hp, false);
                TacticalAttackResult.ClashFact fact = result.getClash().getLastContact();
                assertEquals(new WorldPosition(2.5, 0), SpearPlayback.wolfPosition(result.getClash(), .27));
                assertEquals(new WorldPosition(2, 0), SpearPlayback.wolfPosition(result.getClash(), .40));
                assertEquals(hp, SpearPlayback.health(result, .27, false));
                assertTrue(SpearPlayback.health(result, .4, false) > 0);
                double start = .36 + SpearPlayback.CONTACT_SECONDS;
                for (int i = fact.getContactIndex(); i < fact.getRoute().size(); i++) {
                    double seconds = start + (i - fact.getContactIndex()) * SpearPlayback.SEGMENT_SECONDS;
                    WorldPosition actual = SpearPlayback.wolfPosition(result.getClash(), seconds);
                    assertEquals(fact.getRoute().get(i).getX(), actual.getX(), 1e-8);
                    assertEquals(fact.getRoute().get(i).getY(), actual.getY(), 1e-8);
                }
                double end = SpearPlayback.duration(result);
                assertEquals(result.getCreatureAfter(), SpearPlayback.health(result, end, false));
                assertEquals(result.getClash().getWolfAfter(), SpearPlayback.wolfPosition(result.getClash(), end));
                assertEquals(4, SpearPlayback.combatQuanta(result.getClash(), .4), 1e-8);
            }
        }
    }

    @Test
    void lethalUnprotectedContactShowsApproachBeforeHunterDeath() {
        TacticalAttackResult result = result(3, 2, true);
        assertEquals(0, result.getHunterAfter());
        assertEquals(1, SpearPlayback.health(result, .35, true));
        assertEquals(new WorldPosition(0, 1), SpearPlayback.wolfPosition(result.getClash(), .4));
        assertEquals(1, SpearPlayback.health(result, .4, true));
        assertEquals(0, SpearPlayback.health(result, SpearPlayback.duration(result), true));
    }
    private WorldSceneState v2(long seed, WorldPosition wolf, int hunterHp, int wolfHp) {
        WorldGeometry geometry = WorldGeometry.empty();
        WorldSceneState scene = new WorldSceneState(
            new HunterState(new WorldPosition(0, 0), 1, .4, 6).withDamage(6 - hunterHp),
            new CreaturePatrolState(Arrays.asList(wolf, wolf.translate(1, 0)),
                new CreatureBehaviorConfig(1, 1, 1, 1, 1, 2, 1.2, 10), .4, geometry, 4).withDamage(4 - wolfHp),
            new TacticalGrid(-.5, -.5, 1), geometry, SpearClashConfig.v2(Facing.E, seed));
        scene.enterTactical();
        return scene;
    }

    @Test
    void oddBiteShowsItsOwnContactAndNoFlightOrBodyMotion() {
        WorldSceneState scene = v2(3, new WorldPosition(1, 1), 6, 4);
        assertTrue(CombatHud.phase(scene).contains("укус"));
        assertTrue(CombatHud.phase(scene).contains("0,5"));
        TacticalAttackResult result = scene.trySpearAction(TacticalAttackResult.Kind.BRACE, null);
        assertEquals(3, result.getDurationQuanta());
        assertEquals(3 * .09 + .18 + .2, SpearPlayback.duration(result), 1e-9);
        assertEquals(6, SpearPlayback.health(result, .08, true));
        assertEquals(5, SpearPlayback.health(result, .1, true));
        assertTrue(SpearPlayback.phase(result, .1).contains("Укус"));
        for (double delta : new double[]{.013, .047, .2}) {
            for (double t = 0; t < SpearPlayback.duration(result); t += delta) {
                assertEquals(new WorldPosition(1, 1), SpearPlayback.wolfPosition(result.getClash(), t));
            }
        }
        assertEquals(3, scene.getTacticalTimeQuanta());
        assertEquals(0, scene.getSpearClashState().getRandomCursor());
    }

    @Test
    void mutualDamageIsVisibleAtContactButBothDeathsFollowMotion() {
        WorldSceneState scene = v2(1, new WorldPosition(3, 0), 1, 1);
        TacticalAttackResult result = scene.trySpearAction(TacticalAttackResult.Kind.BRACE, null);
        assertEquals(1, SpearPlayback.health(result, .35, true));
        assertEquals(1, SpearPlayback.health(result, .35, false));
        assertEquals(0, SpearPlayback.health(result, .4, true));
        assertEquals(0, SpearPlayback.health(result, .4, false));
        assertEquals(1, SpearPlayback.bodyHealth(result, .4, true));
        assertEquals(1, SpearPlayback.bodyHealth(result, .4, false));
        double end = SpearPlayback.duration(result);
        assertEquals(0, SpearPlayback.bodyHealth(result, end, true));
        assertEquals(0, SpearPlayback.bodyHealth(result, end, false));
        assertEquals(new WorldPosition(-1, 1), SpearPlayback.wolfPosition(result.getClash(), end));
        assertEquals("Охотник повержен", SpearPlayback.phase(result, end));
    }
}
