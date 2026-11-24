package ru.darvell.game.hhg.domain.scene;

import java.util.Arrays;
import java.util.List;
import java.util.function.LongToDoubleFunction;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import ru.darvell.game.hhg.domain.actor.CreatureBehaviorConfig;
import ru.darvell.game.hhg.domain.actor.CreaturePatrolState;
import ru.darvell.game.hhg.domain.actor.HunterState;
import ru.darvell.game.hhg.domain.tactical.CellCoordinates;
import ru.darvell.game.hhg.domain.tactical.Facing;
import ru.darvell.game.hhg.domain.tactical.SpearClashConfig;
import ru.darvell.game.hhg.domain.tactical.SpearClashState.Result;
import ru.darvell.game.hhg.domain.tactical.SpearClashState.StopReason;
import ru.darvell.game.hhg.domain.tactical.SpearClashState.WolfPhase;
import ru.darvell.game.hhg.domain.tactical.TacticalAttackResult;
import ru.darvell.game.hhg.domain.tactical.TacticalAttackResult.ClashFact;
import ru.darvell.game.hhg.domain.tactical.TacticalAttackResult.EventType;
import ru.darvell.game.hhg.domain.tactical.TacticalAttackResult.Kind;
import ru.darvell.game.hhg.domain.tactical.TacticalAttackResult.Outcome;
import ru.darvell.game.hhg.domain.tactical.TacticalEntryResult;
import ru.darvell.game.hhg.domain.tactical.TacticalExitResult;
import ru.darvell.game.hhg.domain.tactical.TacticalGrid;
import ru.darvell.game.hhg.domain.tactical.TacticalMoveResult;
import ru.darvell.game.hhg.domain.world.StaticAabb;
import ru.darvell.game.hhg.domain.world.WorldGeometry;
import ru.darvell.game.hhg.domain.world.WorldPosition;

import static org.junit.jupiter.api.Assertions.*;
import static ru.darvell.game.hhg.domain.tactical.TacticalAttackResult.EventType.*;

class BoarSpearWolfClashTest {
    private static final TacticalGrid GRID = new TacticalGrid(-.5, -.5, 1);
    private static final CreatureBehaviorConfig BEHAVIOR = new CreatureBehaviorConfig(1, 1, 1, 1, 1, 2, 1.2, 10);
    private WorldSceneState scene;

    @Test
    void v2MeleeChainLetsLivingHunterStrikeDespiteRepeatedNonlethalBites() {
        begin(SpearClashConfig.v2(Facing.E, 0), at(0, 0), at(1, 0), 6, 4,
            0, null, WolfTurn.ready(false, false));
        List<TacticalAttackResult> chain = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) chain.add(scene.tryAttackCreature(cell(1, 0)));
        // До исправления: INTERRUPTED x3, q3/6/9, HP 3/4, ни одного HIT.
        assertEquals(Arrays.asList(Outcome.HIT, Outcome.HIT, Outcome.HIT),
            chain.stream().map(TacticalAttackResult::getOutcome).collect(Collectors.toList()));
        assertEquals(Arrays.asList(6L, 12L, 16L),
            chain.stream().map(TacticalAttackResult::getEndQuanta).collect(Collectors.toList()));
        assertEquals(0, scene.getHunterHealth());
        assertEquals(1, scene.getCreatureHealth());
        assertEquals(SceneMode.TACTICAL, scene.getMode());
    }

    @Test
    void v2StrikeWaitsForCommandAndRepeatedBiteRecoveryWithExactCausalOrder() {
        begin(SpearClashConfig.v2(Facing.E, 0), at(0, 0), at(1, 0), 6, 4,
            11, null, WolfTurn.ready(false, false));
        for (int i = 0; i < 3; i++) {
            scene.previewAttackCreature(cell(1, 0));
            scene.update(0, 0, 100, false);
            assertEquals(11, scene.getTacticalTimeQuanta());
            assertEquals(6, scene.getHunterHealth());
            assertNull(scene.getLastResolution());
        }
        TacticalAttackResult preview = scene.previewAttackCreature(cell(1, 0));
        TacticalAttackResult result = scene.tryAttackCreature(cell(1, 0));
        assertEquals(preview.getClash(), result.getClash());
        assertEquals(preview.getEndQuanta(), result.getEndQuanta());
        assertEquals(4, result.getPlannedDurationQuanta());
        assertEquals(17, result.getEndQuanta());
        assertEquals(Arrays.asList(3L, 6L), times(result, BITE_PREPARATION_STARTED));
        assertEquals(Arrays.asList(1L, 4L), times(result, BITE));
        assertEquals(Arrays.asList(1L, 4L), times(result, HUNTER_RECOVERY_STARTED));
        assertEquals(Arrays.asList(3L, 6L), times(result, HUNTER_RECOVERY_ENDED));
        assertEquals(Arrays.asList(4L), times(result, HIT));
        List<ClashFact> facts = result.getClash().getTimeline();
        assertTrue(facts.indexOf(facts(result, HIT).get(0)) < facts.indexOf(facts(result, BITE).get(1)));
        assertTrue(times(result, ACTION_INTERRUPTED).isEmpty());
        assertEquals(4, result.getHunterAfter());
        assertEquals(3, result.getCreatureAfter());
        assertEquals(0, scene.getSpearClashState().getHunterRecovery());
        assertEquals(1, scene.getSpearClashState().getWolfRemaining());
        assertEquals(StopReason.HUNTER_READY, result.getClash().getStopReason());
    }

    @Test
    void v2DeathBeforeStrikeExplicitlyCancelsItAndDisablesFurtherCommands() {
        begin(SpearClashConfig.v2(Facing.E, 0), at(0, 0), at(1, 0), 1, 4,
            0, null, WolfTurn.ready(false, false));
        TacticalAttackResult preview = scene.previewAttackCreature(cell(1, 0));
        TacticalAttackResult result = scene.tryAttackCreature(cell(1, 0));
        assertEquals(preview.getClash(), result.getClash());
        assertEquals(Outcome.INTERRUPTED, result.getOutcome());
        assertEquals(1, result.getDurationQuanta());
        assertEquals(Arrays.asList(1L), times(result, ACTION_INTERRUPTED));
        assertTrue(times(result, HIT).isEmpty());
        assertTrue(times(result, HUNTER_RECOVERY_STARTED).isEmpty());
        assertEquals(0, scene.getSpearClashState().getHunterRecovery());
        assertEquals(0, scene.getSpearClashState().getWolfRemaining());
        assertEquals(StopReason.TERMINAL, result.getClash().getStopReason());
        assertEquals(Outcome.HUNTER_DEFEATED, scene.tryAttackCreature(cell(1, 0)).getOutcome());
        assertSame(result, scene.getLastResolution());
        assertEquals(1, scene.getTacticalTimeQuanta());
    }

    @Test
    void v2SharedStrikeAndBiteDeadlinePreservesCompletedHitOrCancelsDeadWolfBite() {
        for (int wolfHp : new int[] {1, 2}) {
            begin(SpearClashConfig.v2(Facing.E, 0), at(0, 0), at(1, 0), 2, wolfHp,
                0, null, WolfTurn.ready(false, false));
            TacticalAttackResult result = scene.tryAttackCreature(cell(1, 0));
            assertEquals(Outcome.HIT, result.getOutcome());
            assertEquals(4, result.getEndQuanta());
            assertEquals(Arrays.asList(4L), times(result, HIT));
            assertEquals(wolfHp == 1 ? Arrays.asList(1L) : Arrays.asList(1L, 4L), times(result, BITE));
            assertTrue(times(result, ACTION_INTERRUPTED).isEmpty());
            assertEquals(wolfHp == 1 ? 1 : 0, result.getHunterAfter());
            assertEquals(wolfHp - 1, result.getCreatureAfter());
            assertEquals(wolfHp == 1 ? SceneMode.EXPLORATION : SceneMode.TACTICAL, scene.getMode());
            assertEquals(StopReason.TERMINAL, result.getClash().getStopReason());
        }
    }

    @Test
    void v2EarlyRecoveryDoesNotReturnBeforeStrikeAndBiteStillInterruptsBraceAndTurn() {
        begin(SpearClashConfig.v2(Facing.E, 0), at(0, 0), at(1, 0), 6, 4,
            0, null, WolfTurn.recovering(1, false));
        TacticalAttackResult result = scene.tryAttackCreature(cell(1, 0));
        assertEquals(Arrays.asList(2L), times(result, BITE));
        assertEquals(Arrays.asList(4L), times(result, HIT));
        assertEquals(Arrays.asList(4L), times(result, HUNTER_RECOVERY_ENDED));
        assertEquals(4, result.getEndQuanta());
        assertEquals(5, result.getHunterAfter());
        assertEquals(3, result.getCreatureAfter());
        assertEquals(1, scene.getSpearClashState().getWolfRemaining());
        for (Kind kind : Arrays.asList(Kind.BRACE, Kind.TURN)) {
            begin(SpearClashConfig.v2(Facing.E, 0), at(0, 0), at(1, 0), 6, 4,
                0, null, WolfTurn.ready(false, false));
            TacticalAttackResult action = scene.trySpearAction(kind, Facing.W);
            assertEquals(Outcome.INTERRUPTED, action.getOutcome());
            assertEquals(Arrays.asList(1L), times(action, ACTION_INTERRUPTED));
            assertTrue(times(action, BRACE_READY).isEmpty());
            assertTrue(times(action, TURN_COMPLETED).isEmpty());
            assertEquals(3, action.getEndQuanta());
        }
    }

    @Test
    void contactNodeAndHealthAreExactForAllOutcomesIncludingDeath() {
        long[] seeds = {3, 1, 0};
        Result[] outcomes = {Result.REPELLED, Result.GRAZED_PAST, Result.BROKE_THROUGH};
        for (int i = 0; i < seeds.length; i++) {
            begin(new SpearClashConfig(Facing.E, seeds[i]), at(0, 0), at(3, 0), 4, 1,
                0, null, WolfTurn.ready(false, false));
            TacticalAttackResult preview = scene.previewSpearAction(Kind.BRACE, null);
            TacticalAttackResult result = brace();
            assertEquals(preview.getClash(), result.getClash());
            ClashFact fact = result.getClash().getLastContact();
            assertEquals(outcomes[i], fact.getContact());
            assertEquals(4, fact.getOffset());
            assertEquals(2, fact.getMotionStart());
            assertEquals(at(2, 0), fact.getRoute().get(fact.getContactIndex()));
            assertEquals(4, fact.getHunterHealth());
            assertEquals(i == 2 ? 1 : 0, fact.getWolfHealth());
            assertEquals(scene.getCreaturePosition(), SpearClash.last(fact.getRoute()));
        }
        begin(new SpearClashConfig(Facing.E, 3), at(0, 0), at(0, 3), 1, 2,
            0, null, WolfTurn.ready(false, false));
        ClashFact lethal = brace().getClash().getLastContact();
        assertEquals(0, lethal.getHunterHealth());
        assertEquals(at(0, 1), lethal.getRoute().get(lethal.getContactIndex()));
    }

    @Test
    void oddQuantaSurvivePublicationAndLegacyConversionIsExplicit() {
        begin(new SpearClashConfig(Facing.E, 3, 1, 2, 2, 50, 30, 20), at(0, 0), at(12, 0), 4, 2,
            0, null, WolfTurn.recovering(1, false));
        TacticalAttackResult preview = scene.previewSpearAction(Kind.BRACE, null);
        assertEquals(0, scene.getTacticalTimeQuanta());
        TacticalAttackResult applied = brace();
        assertEquals(1, applied.getPlannedDurationQuanta());
        assertEquals(9, applied.getDurationQuanta());
        assertEquals(0, applied.getStartQuanta());
        assertEquals(9, applied.getEndQuanta());
        assertEquals(9, scene.getTacticalTimeQuanta());
        assertEquals(2, scene.getSpearClashState().getWolfRemaining());
        assertEquals(preview.getClash(), applied.getClash());
        assertEquals(preview.getDurationQuanta(), applied.getDurationQuanta());
        assertThrows(IllegalStateException.class, applied::getPlannedDuration);
        assertThrows(IllegalStateException.class, applied::getDuration);
        assertThrows(IllegalStateException.class, scene::getTacticalTimeUnits);
        TacticalAttackResult.Event ready = applied.getEvents().stream()
            .filter(e -> e.getType() == BRACE_READY).findFirst().get();
        assertEquals(1, ready.getOffsetQuanta());
        assertThrows(IllegalStateException.class, ready::getOffset);
        assertEquals(Arrays.asList(3L, 5L, 7L, 9L), facts(applied, WOLF_MOVED).stream().map(ClashFact::getOffset).collect(Collectors.toList()));
    }

    @Test
    void oddAbsoluteOriginAndDeadlineOverflowRemainAtomic() {
        begin(new SpearClashConfig(Facing.E, 3), at(0, 0), at(3, 0), 4, 2,
            1, null, WolfTurn.ready(false, false));
        TacticalAttackResult result = brace();
        assertEquals(1, result.getStartQuanta());
        assertEquals(7, result.getEndQuanta());
        assertEquals(6, result.getDurationQuanta());
        assertEquals(3, result.getDuration());
        begin(new SpearClashConfig(Facing.E, 3, 1, 2, 2, 50, 30, 20), at(0, 0), at(3, 0), 4, 2,
            Long.MAX_VALUE - 7, null, WolfTurn.ready(false, false));
        long before = scene.getTacticalTimeQuanta();
        assertThrows(ArithmeticException.class, () -> scene.previewSpearAction(Kind.BRACE, null));
        assertThrows(ArithmeticException.class, () -> scene.trySpearAction(Kind.BRACE, null));
        assertEquals(before, scene.getTacticalTimeQuanta());
        assertEquals(4, scene.getHunterHealth());
        assertEquals(2, scene.getCreatureHealth());
        assertEquals(0, scene.getSpearClashState().getRandomCursor());
        assertNull(scene.getLastResolution());
    }

    @Test
    void v2BiteWinsOverPhysicallyAvailableFlankAndHasNoFlight() {
        HunterState hunter = new HunterState(at(0, 0), 1, .4);
        CreaturePatrolState wolf = new CreaturePatrolState(Arrays.asList(at(1, 1), at(2, 1)),
            BEHAVIOR, .4, WorldGeometry.empty());
        assertEquals(at(0, 2), new SpearClash(GRID, WorldGeometry.empty()).stepTarget(hunter, wolf, Facing.E, true));
        begin(SpearClashConfig.v2(Facing.E, 3), at(0, 0), at(1, 1), 6, 4,
            0, null, WolfTurn.ready(false, false));
        TacticalAttackResult result = brace();
        assertEquals(at(1, 1), scene.getCreaturePosition());
        assertEquals(5, scene.getHunterHealth());
        assertEquals(3, scene.getTacticalTimeQuanta());
        assertEquals(1, scene.getSpearClashState().getWolfRemaining());
        assertTrue(scene.getSpearClashState().isBite());
        assertEquals(0, scene.getSpearClashState().getRandomCursor());
        assertEquals(Arrays.asList(1L), times(result, BITE));
        assertTrue(facts(result, LEAP_LAUNCHED).isEmpty());
        assertTrue(facts(result, WOLF_FLANK_STARTED).isEmpty());
    }

    @Test
    void v2BreakthroughPreparesDuringRecoveryButContactWaitsForDecision() {
        begin(SpearClashConfig.v2(Facing.E, 0), at(0, 0), at(3, 0), 6, 4,
            0, null, WolfTurn.ready(false, false));
        TacticalAttackResult result = brace();
        assertEquals(Result.BROKE_THROUGH, result.getClash().getLastContact().getContact());
        assertEquals(8, scene.getTacticalTimeQuanta());
        assertEquals(5, scene.getHunterHealth());
        assertEquals(Arrays.asList(5L), times(result, BITE_PREPARATION_STARTED));
        assertEquals(1, scene.getSpearClashState().getWolfRemaining());
        assertTrue(scene.getSpearClashState().isBite());
        TacticalAttackResult preview = scene.previewAttackCreature(cell(1, 0));
        assertEquals(8, scene.getTacticalTimeQuanta());
        TacticalAttackResult applied = scene.tryAttackCreature(cell(1, 0));
        assertEquals(preview.getClash(), applied.getClash());
        assertEquals(14, scene.getTacticalTimeQuanta());
        assertEquals(3, scene.getHunterHealth());
        assertEquals(3, scene.getCreatureHealth());
        assertEquals(Arrays.asList(1L, 4L), times(applied, BITE));
        assertEquals(Arrays.asList(4L), times(applied, HIT));
        assertTrue(times(applied, ACTION_INTERRUPTED).isEmpty());
        assertEquals(1, scene.getSpearClashState().getWolfRemaining());
    }

    @Test
    void v2MutualLethalContactIsDefeatAndKeepsBothDamageAndBody() {
        begin(SpearClashConfig.v2(Facing.E, 1), at(0, 0), at(3, 0), 1, 1,
            0, null, WolfTurn.ready(false, false));
        TacticalAttackResult result = brace();
        assertEquals(Result.GRAZED_PAST, result.getClash().getLastContact().getContact());
        assertEquals(0, result.getHunterAfter());
        assertEquals(0, result.getCreatureAfter());
        assertEquals(at(-1, 1), scene.getCreaturePosition());
        assertEquals(SceneMode.TACTICAL, scene.getMode());
        assertEquals(4, scene.getTacticalTimeQuanta());
        assertEquals(0, scene.getSpearClashState().getWolfRemaining());
    }

    @Test
    void v2BiteMissKeepsHunterActionAndNeverMovesWolfOrConsumesRandom() {
        begin(SpearClashConfig.v2(Facing.E, 3), at(0, 0), at(1, 1), 6, 4,
            0, cursor -> { throw new AssertionError("Bite must not sample RNG"); }, WolfTurn.ready(false, false));
        TacticalAttackResult result = move(-1, 0);
        assertEquals(at(-1, 0), scene.getHunterPosition());
        assertEquals(at(1, 1), scene.getCreaturePosition());
        assertEquals(6, result.getHunterAfter());
        assertEquals(Arrays.asList(1L), times(result, BITE_MISS));
        assertTrue(facts(result, ACTION_INTERRUPTED).isEmpty());
        assertEquals(1, scene.getSpearClashState().getWolfRemaining());
    }

    @Test
    void v2AllOutcomeConsequencesAreOneContactAndExact() {
        double[] samples = {.1, .5, .9};
        Result[] expected = {Result.REPELLED, Result.GRAZED_PAST, Result.BROKE_THROUGH};
        long[] hunterRecovery = {2, 4, 4}, wolfRecovery = {4, 2, 1};
        for (int n = 0; n < samples.length; n++) {
            double sample = samples[n];
            begin(SpearClashConfig.v2(Facing.E, 0), at(0, 0), at(3, 0), 6, 4,
                0, cursor -> sample, WolfTurn.ready(false, false));
            TacticalAttackResult preview = scene.previewSpearAction(Kind.BRACE, null);
            TacticalAttackResult result = brace();
            assertEquals(preview.getClash(), result.getClash());
            ClashFact contact = result.getClash().getLastContact();
            assertEquals(expected[n], contact.getContact());
            assertEquals(n == 0 ? 6 : 5, contact.getHunterHealth());
            assertEquals(n == 2 ? 4 : 3, contact.getWolfHealth());
            assertEquals(hunterRecovery[n], contact.getHunterRecovery());
            assertEquals(wolfRecovery[n], contact.getWolfRecovery());
            assertEquals(1, scene.getSpearClashState().getRandomCursor());
            assertTrue(facts(result, BITE).isEmpty());
        }
    }

    @Test
    void v2LongLeapPreparesBeforeHunterReadyAndLaunchesWithPositiveFlight() {
        ru.darvell.game.hhg.domain.tactical.SpearCombatProfile defaults =
            ru.darvell.game.hhg.domain.tactical.SpearCombatProfile.v2();
        java.util.EnumMap<Result, ru.darvell.game.hhg.domain.tactical.SpearCombatProfile.Consequence> values =
            new java.util.EnumMap<>(Result.class);
        for (Result result : Result.values()) values.put(result, defaults.consequence(result));
        values.put(Result.REPELLED, new ru.darvell.game.hhg.domain.tactical.SpearCombatProfile.Consequence(0, 1, 8, 1));
        ru.darvell.game.hhg.domain.tactical.SpearCombatProfile profile =
            new ru.darvell.game.hhg.domain.tactical.SpearCombatProfile(
                ru.darvell.game.hhg.domain.tactical.SpearCombatProfile.Id.V2, values,
                defaults.bite(true), defaults.bite(false), 1, 1);
        begin(new SpearClashConfig(Facing.E, 3, 2, 2, 2, 2, 35, 35, 30, profile),
            at(0, 0), at(3, 0), 6, 4, 0, null, WolfTurn.ready(false, false));
        TacticalAttackResult result = brace();
        assertEquals(Arrays.asList(5L), times(result, LEAP_PREPARATION_STARTED));
        assertEquals(Arrays.asList(2L, 11L), times(result, LEAP_LAUNCHED));
        assertEquals(12, scene.getTacticalTimeQuanta());
        assertEquals(WolfPhase.IN_FLIGHT, scene.getSpearClashState().getWolfPhase());
        assertEquals(1, scene.getSpearClashState().getWolfRemaining());
    }

    @Test
    void v2ExitChecksBiteThreatAndPreservesRecoveryAndHealthOnReentry() {
        begin(SpearClashConfig.v2(Facing.E, 3), at(0, 0), at(1, 1), 6, 4,
            0, null, WolfTurn.ready(false, false));
        assertEquals(TacticalExitResult.ACTIVE_THREAT, scene.tryExitTactical());
        assertEquals(0, scene.getTacticalTimeQuanta());
        move(-1, 0);
        assertEquals(TacticalExitResult.SUCCESS, scene.tryExitTactical());
        assertEquals(2, scene.getTacticalTimeQuanta());
        assertEquals(1, scene.getSpearClashState().getWolfRemaining());
        assertEquals(TacticalEntryResult.Outcome.SUCCESS, scene.enterTactical());
        assertEquals(2, scene.getTacticalTimeQuanta());
        assertEquals(1, scene.getSpearClashState().getWolfRemaining());
        assertEquals(6, scene.getHunterMaxHealth());
        assertEquals(4, scene.getCreatureMaxHealth());
        assertEquals(6, scene.getHunterHealth());
        assertEquals(0, scene.getSpearClashState().getRandomCursor());
    }

    @Test
    void v2BiteInterruptsBreakoutAndNewDamageStillRollsBackOnOverflow() {
        begin(SpearClashConfig.v2(Facing.E, 3), at(0, 0), at(1, 1), 6, 4,
            0, null, WolfTurn.ready(false, false));
        TacticalAttackResult preview = scene.previewBreakoutTo(cell(0, -2));
        TacticalAttackResult result = scene.tryBreakoutTo(cell(0, -2));
        assertTrue(result.isAccepted());
        assertEquals(preview.getClash(), result.getClash());
        assertEquals(at(0, 0), scene.getHunterPosition());
        assertEquals(5, scene.getHunterHealth());
        assertEquals(Arrays.asList(1L), times(result, BREAKOUT_INTERRUPTED));
        assertTrue(facts(result, BREAKOUT_MOVED).isEmpty());
        begin(SpearClashConfig.v2(Facing.E, 1), at(0, 0), at(3, 0), 6, 4,
            Long.MAX_VALUE - 5, null, WolfTurn.ready(false, false));
        assertThrows(ArithmeticException.class, () -> scene.previewSpearAction(Kind.BRACE, null));
        assertThrows(ArithmeticException.class, () -> scene.trySpearAction(Kind.BRACE, null));
        assertEquals(6, scene.getHunterHealth());
        assertEquals(4, scene.getCreatureHealth());
        assertEquals(at(3, 0), scene.getCreaturePosition());
        assertEquals(0, scene.getSpearClashState().getRandomCursor());
        assertEquals(Long.MAX_VALUE - 5, scene.getTacticalTimeQuanta());
        assertNull(scene.getLastResolution());
    }

    private void begin(long seed, int wx, int wy, StaticAabb... obstacles) {
        begin(new SpearClashConfig(Facing.E, seed), at(0, 0), at(wx, wy), 4, 2,
            0, null, WolfTurn.ready(false, false), obstacles);
    }

    private void begin(SpearClashConfig config, WorldPosition hunter, WorldPosition wolf, int hunterHp, int wolfHp,
                       long now, LongToDoubleFunction random, WolfTurn turn, StaticAabb... obstacles) {
        WorldGeometry geometry = new WorldGeometry(Arrays.asList(obstacles));
        scene = new WorldSceneState(new HunterState(hunter, 1, .4, config.getProfile().isV2() ? 6 : 4)
                .withDamage((config.getProfile().isV2() ? 6 : 4) - hunterHp),
            new CreaturePatrolState(Arrays.asList(wolf, wolf.translate(1, 0)), BEHAVIOR, .4, geometry, config.getProfile().isV2() ? 4 : 2)
                .withDamage((config.getProfile().isV2() ? 4 : 2) - wolfHp), GRID, geometry, config, now, random, turn);
        assertEquals(TacticalEntryResult.Outcome.SUCCESS, scene.enterTactical());
    }

    private static WorldPosition at(int x, int y) { return new WorldPosition(x, y); }
    private static CellCoordinates cell(int x, int y) { return new CellCoordinates(x, y); }

    private WolfTurn flight(int wx, int wy) {
        SpearClash.Plan plan = new SpearClash(GRID, WorldGeometry.empty()).plan(at(0, 0), at(wx, wy));
        return WolfTurn.preparing(plan, 2, 4, false);
    }

    private TacticalAttackResult brace() {
        TacticalAttackResult result = scene.trySpearAction(Kind.BRACE, null);
        assertTrue(result.isAccepted());
        assertSame(result, scene.getLastResolution());
        return result;
    }

    private TacticalAttackResult move(int x, int y) {
        assertEquals(TacticalMoveResult.SUCCESS, scene.tryMoveHunterTo(cell(x, y)));
        return scene.getLastResolution();
    }

    private static List<ClashFact> facts(TacticalAttackResult result, EventType type) {
        return result.getClash().getTimeline().stream().filter(f -> f.getType() == type).collect(Collectors.toList());
    }

    private static List<Long> times(TacticalAttackResult result, EventType type) {
        return facts(result, type).stream().map(ClashFact::getOffset).collect(Collectors.toList());
    }

    private static void contact(TacticalAttackResult result, Result contact, long offset, long hunter, long wolf) {
        ClashFact fact = result.getClash().getLastContact();
        assertNotNull(fact);
        assertEquals(contact, fact.getContact());
        assertEquals(offset, fact.getOffset());
        assertEquals(hunter, fact.getHunterRecovery());
        assertEquals(wolf, fact.getWolfRecovery());
        assertTrue(result.getEvents().stream().noneMatch(event -> event.getType() == BITE || event.getType() == BITE_MISS));
    }

    private void state(long tick, int h, int w, int x, int y, WolfPhase phase, long remaining, long cursor) {
        assertEquals(tick, scene.getTacticalTimeQuanta());
        assertEquals(h, scene.getHunterHealth());
        assertEquals(w, scene.getCreatureHealth());
        assertEquals(at(x, y), scene.getCreaturePosition());
        assertEquals(phase, scene.getSpearClashState().getWolfPhase());
        assertEquals(remaining, scene.getSpearClashState().getWolfRemaining());
        assertEquals(cursor, scene.getSpearClashState().getRandomCursor());
        assertNull(scene.getBiteState());
        if (scene.getPlacement() != null && w > 0) assertEquals(cell(x, y), scene.getPlacement().getCreatureCell());
    }

    @Test
    void r01OneBraceRunsAllThreeContactsAndRecoveries() {
        begin(3, 3, 0);
        TacticalAttackResult repel = brace();
        assertEquals(Arrays.asList(2L), times(repel, BRACE_READY));
        assertEquals(Arrays.asList(2L), times(repel, LEAP_LAUNCHED));
        contact(repel, Result.REPELLED, 4, 2, 6);
        state(6, 4, 1, 3, 0, WolfPhase.RECOVERING, 4, 1);
        begin(1, 3, 0);
        TacticalAttackResult pass = brace();
        contact(pass, Result.GRAZED_PAST, 4, 4, 4);
        assertEquals(Arrays.asList(at(3, 0), at(2, 0), at(1, 0), at(1, 1), at(0, 1), at(-1, 1)),
            pass.getClash().getLastContact().getRoute());
        state(8, 4, 1, -1, 1, WolfPhase.PREPARING, 2, 1);
        begin(0, 3, 0);
        contact(brace(), Result.BROKE_THROUGH, 4, 6, 2);
        state(10, 4, 2, 1, 0, WolfPhase.PREPARING, 2, 1);
        assertEquals(at(0, 0), scene.getHunterPosition());
        assertEquals(Facing.E, scene.getSpearClashState().getFacing());
        assertFalse(scene.getSpearClashState().isBraced());
    }

    @Test
    void r02ApproachMovesOnDeadlinesBeforeTheSameBraceContacts() {
        begin(3, 5, 0);
        assertEquals(at(5, 0), scene.getCreaturePosition());
        assertEquals(cell(5, 0), scene.getPlacement().getCreatureCell());
        TacticalAttackResult result = brace();
        List<ClashFact> moves = facts(result, WOLF_MOVED);
        assertEquals(Arrays.asList(2L, 4L), times(result, WOLF_MOVED));
        assertEquals(Arrays.asList(at(5, 0), at(4, 0)), moves.get(0).getRoute());
        assertEquals(Arrays.asList(at(4, 0), at(3, 0)), moves.get(1).getRoute());
        assertEquals(Arrays.asList(4L), times(result, LEAP_PREPARATION_STARTED));
        assertEquals(Arrays.asList(6L), times(result, LEAP_LAUNCHED));
        contact(result, Result.REPELLED, 8, 2, 6);
        state(10, 4, 1, 3, 0, WolfPhase.RECOVERING, 4, 1);
    }

    @Test
    void r03FlankExposesOneResponseWhileStanceAndTelegraphStayFrozen() {
        begin(new SpearClashConfig(Facing.E, 3), at(0, 0), at(1, 1), 4, 2, 0, null,
            WolfTurn.recovering(2, false));
        TacticalAttackResult result = brace();
        assertEquals(StopReason.SIDE_THREAT, result.getClash().getStopReason());
        assertEquals(Arrays.asList(2L), times(result, WOLF_FLANK_STARTED));
        assertEquals(Arrays.asList(4L), times(result, WOLF_MOVED));
        state(4, 4, 2, 0, 2, WolfPhase.PREPARING, 2, 0);
        assertTrue(scene.getSpearClashState().isBraced());
        assertTrue(scene.getSpearClashState().isFlankUsed());
        scene.update(0, 0, 20, false);
        assertFalse(scene.previewSpearAction(Kind.BRACE, null).isAccepted());
        assertSame(result, scene.getLastResolution());
        move(-1, 0);
        TacticalAttackResult missed = move(-2, 0);
        contact(missed, Result.MISS, 2, 0, 4);
        state(8, 4, 2, 0, 1, WolfPhase.RECOVERING, 4, 0);
    }

    @Test
    void r04BlockedRouteProducesOneFactAndNoSyntheticDeadline() {
        begin(3, 4, 0, new StaticAabb(2.35, -3, 1.2, 6));
        TacticalAttackResult result = brace();
        assertEquals(StopReason.NO_ROUTE, result.getClash().getStopReason());
        assertEquals(Arrays.asList(0L), times(result, WOLF_NO_ROUTE));
        state(2, 4, 2, 4, 0, WolfPhase.READY, 0, 0);
        assertTrue(scene.getSpearClashState().isBraced());
        scene.update(0, 0, 200, false);
        assertSame(result, scene.getLastResolution());
        TacticalAttackResult lower = scene.trySpearAction(Kind.LOWER_SPEAR, null);
        assertTrue(times(lower, WOLF_NO_ROUTE).isEmpty());
    }

    @Test
    void r05GuardProcessesTheWholeLimitPacketAndNeverInsertsADeadline() {
        for (int step : new int[]{2, 4}) {
            SpearClashConfig config = new SpearClashConfig(Facing.E, 3, 2, 2, 2, step, 50, 30, 20);
            begin(config, at(0, 0), at(12, 0), 4, 2, 0, null, WolfTurn.recovering(step, false));
            TacticalAttackResult result = brace();
            assertEquals(StopReason.GUARD_LIMIT, result.getClash().getStopReason());
            assertTrue(scene.getSpearClashState().isBraced());
            if (step == 2) {
                assertEquals(Arrays.asList(4L, 6L, 8L, 10L), times(result, WOLF_MOVED));
                assertEquals(Arrays.asList(at(9, 0), at(8, 0)), facts(result, WOLF_MOVED).get(3).getRoute());
                state(10, 4, 2, 8, 0, WolfPhase.STEPPING, 2, 0);
                assertEquals(at(7, 0), scene.getSpearClashState().getStepTarget());
                assertEquals(Arrays.asList(2L, 4L, 6L, 8L, 10L), times(result, WOLF_STEP_STARTED));
            } else {
                assertEquals(Arrays.asList(8L), times(result, WOLF_MOVED));
                state(8, 4, 2, 11, 0, WolfPhase.STEPPING, 4, 0);
            }
            scene.update(0, 0, 100, false);
            assertSame(result, scene.getLastResolution());
        }
    }

    @Test
    void r06MissResolvesAgainstTheLockedTargetAndKeepsGuard() {
        begin(new SpearClashConfig(Facing.E, 3), at(0, 2), at(3, 0), 4, 2, 0, null, flight(3, 0));
        TacticalAttackResult result = brace();
        contact(result, Result.MISS, 4, 0, 4);
        state(4, 4, 2, 1, 0, WolfPhase.RECOVERING, 4, 0);
        assertEquals(StopReason.LEAP_RESOLVED, result.getClash().getStopReason());
        assertTrue(scene.getSpearClashState().isBraced());
    }

    @Test
    void r07AnInvalidMandatoryDeadlineCannotPublishAPartialTransition() {
        begin(new SpearClashConfig(Facing.E, 3), at(0, 0), at(3, 0), 4, 2, 0, null,
            WolfTurn.recovering(-2, false));
        assertThrows(IllegalStateException.class, () -> scene.trySpearAction(Kind.BRACE, null));
        assertNull(scene.getLastResolution());
        assertEquals(0, scene.getTacticalTimeQuanta());
        assertEquals(at(3, 0), scene.getCreaturePosition());
    }

    @Test
    void r08WolfWaitsForAHunterDecisionBeforeItPreparesItsNextLunge() {
        begin(0, 3, 0);
        TacticalAttackResult result = brace();
        assertEquals(Arrays.asList(6L), times(result, WOLF_RECOVERY_ENDED));
        assertEquals(Arrays.asList(10L), times(result, HUNTER_RECOVERY_ENDED));
        assertEquals(Arrays.asList(10L), times(result, LEAP_PREPARATION_STARTED));
        state(10, 4, 2, 1, 0, WolfPhase.PREPARING, 2, 1);
        TacticalAttackResult attack = scene.tryAttackCreature(cell(1, 0));
        assertEquals(Arrays.asList(2L), times(attack, LEAP_LAUNCHED));
        assertTrue(attack.getClash().getTimeline().indexOf(facts(attack, HIT).get(0))
            < attack.getClash().getTimeline().indexOf(facts(attack, LEAP_CONTACT).get(0)));
        contact(attack, Result.HIT, 4, 2, 4);
        state(16, 3, 1, 1, 0, WolfPhase.RECOVERING, 2, 1);
    }

    @Test
    void r09OneHitInterruptsAnIncompleteTurnOrPreparation() {
        for (Kind kind : Arrays.asList(Kind.TURN, Kind.BRACE)) {
            begin(new SpearClashConfig(Facing.E, 3, 6, 2, 2, 50, 30, 20),
                at(0, 0), at(3, 0), 4, 2, 0, null, flight(3, 0));
            TacticalAttackResult result = scene.trySpearAction(kind, Facing.W);
            assertEquals(Outcome.INTERRUPTED, result.getOutcome());
            contact(result, Result.HIT, 4, 2, 4);
            assertEquals(Arrays.asList(4L), times(result, ACTION_INTERRUPTED));
            assertTrue(times(result, TURN_COMPLETED).isEmpty());
            assertTrue(times(result, BRACE_READY).isEmpty());
            assertEquals(Facing.E, scene.getSpearClashState().getFacing());
            assertFalse(scene.getSpearClashState().isBraced());
            state(6, 3, 2, 1, 0, WolfPhase.RECOVERING, 2, 0);
        }
    }

    @Test
    void r10LateStepBlockKeepsOriginAndDoesNotRetryOnTheSameMark() {
        begin(new SpearClashConfig(Facing.E, 3), at(1, 0), at(3, 0), 4, 2, 0, null,
            WolfTurn.stepping(new WolfTurn.Step(at(3, 0), at(2, 0), 0, true), 2, false));
        TacticalAttackResult blocked = move(2, 0);
        assertEquals(Arrays.asList(2L), times(blocked, WOLF_STEP_BLOCKED));
        assertEquals(at(2, 0), scene.getHunterPosition());
        assertTrue(scene.getSpearClashState().isFlankUsed());
        state(2, 4, 2, 3, 0, WolfPhase.READY, 0, 0);
        assertEquals(Arrays.asList(0L), times(move(1, 0), LEAP_PREPARATION_STARTED));
    }

    @Test
    void r11StrikePrecedesLeapAndWolfStepPrecedesBreakoutTranslation() {
        SpearClashConfig delayed = new SpearClashConfig(Facing.E, 3, 2, 4, 2, 50, 30, 20);
        begin(delayed, at(0, 0), at(2, 0), 4, 1, 0, null, WolfTurn.ready(false, false));
        move(1, 0);
        TacticalAttackResult strike = scene.tryAttackCreature(cell(2, 0));
        assertEquals(Arrays.asList(4L), times(strike, LEAP_CANCELLED));
        assertNull(strike.getClash().getLastContact());
        assertEquals(SceneMode.EXPLORATION, scene.getMode());
        state(6, 4, 0, 2, 0, WolfPhase.READY, 0, 0);

        begin(new SpearClashConfig(Facing.E, 3), at(0, 0), at(1, 1), 4, 2, 0, null,
            WolfTurn.stepping(new WolfTurn.Step(at(1, 1), at(1, 0), 0, false), 2, false));
        TacticalAttackResult breakout = scene.tryBreakoutTo(cell(2, 0));
        assertEquals(Outcome.INTERRUPTED, breakout.getOutcome());
        assertEquals(Arrays.asList(2L), times(breakout, WOLF_MOVED));
        assertEquals(Arrays.asList(2L), times(breakout, BREAKOUT_BLOCKED));
        assertEquals(at(0, 0), scene.getHunterPosition());
        assertEquals(at(1, 0), scene.getCreaturePosition());
    }

    @Test
    void r12ForecastAndApplyShareBothDrawsAndRejectStaleGeometry() {
        begin(3, 3, 0);
        TacticalAttackResult first = scene.previewSpearAction(Kind.BRACE, null);
        assertEquals(first.getClash(), scene.previewSpearAction(Kind.BRACE, null).getClash());
        assertNull(scene.getLastResolution());
        state(0, 4, 2, 3, 0, WolfPhase.PREPARING, 2, 0);
        assertEquals(first.getClash(), brace().getClash());
        TacticalAttackResult second = scene.previewSpearAction(Kind.BRACE, null);
        assertEquals(second.getClash(), scene.previewSpearAction(Kind.BRACE, null).getClash());
        state(6, 4, 1, 3, 0, WolfPhase.RECOVERING, 4, 1);
        TacticalAttackResult applied = brace();
        assertEquals(second.getClash(), applied.getClash());
        contact(applied, Result.GRAZED_PAST, 8, 4, 4);
        assertEquals(SceneMode.EXPLORATION, scene.getMode());
        state(14, 4, 0, -1, 1, WolfPhase.READY, 0, 2);

        begin(1, 3, 0);
        assertEquals(Result.GRAZED_PAST, scene.previewSpearAction(Kind.BRACE, null).getClash().getLastContact().getContact());
        move(0, 1);
        TacticalAttackResult updated = scene.previewSpearAction(Kind.BRACE, null);
        TacticalAttackResult fresh = brace();
        assertEquals(updated.getClash(), fresh.getClash());
        contact(fresh, Result.MISS, 2, 0, 4);
        state(4, 4, 2, 1, 0, WolfPhase.RECOVERING, 4, 0);
    }

    @Test
    void r13ExitBlocksLiveThreatAndCarriesRecoveryThroughReentry() {
        begin(3, 3, 0);
        assertEquals(TacticalExitResult.ACTIVE_THREAT, scene.tryExitTactical());
        state(0, 4, 2, 3, 0, WolfPhase.PREPARING, 2, 0);
        TacticalAttackResult repelled = brace();
        assertEquals(TacticalExitResult.SUCCESS, scene.tryExitTactical());
        scene.update(0, 0, .1, false);
        assertEquals(TacticalEntryResult.Outcome.SUCCESS, scene.enterTactical());
        assertSame(repelled, scene.getLastResolution());
        state(6, 4, 1, 3, 0, WolfPhase.RECOVERING, 4, 1);
        move(0, 1);
        assertEquals(WolfPhase.RECOVERING, scene.getSpearClashState().getWolfPhase());
        move(0, 2);
        assertEquals(WolfPhase.STEPPING, scene.getSpearClashState().getWolfPhase());

        begin(3, 3, 0);
        move(0, 1);
        assertEquals(TacticalExitResult.SUCCESS, scene.tryExitTactical());
        assertEquals(TacticalEntryResult.Outcome.SUCCESS, scene.enterTactical());
        assertEquals(WolfPhase.STEPPING, scene.getSpearClashState().getWolfPhase());
        assertNull(scene.getSpearClashState().getJumpTarget());
    }

    @Test
    void r14PaidActionsBreakStanceAndCoincidentReadyIsFirst() {
        for (Facing facing : Arrays.asList(Facing.E, Facing.NE, Facing.N, Facing.W)) {
            quietStance();
            TacticalAttackResult previous = scene.getLastResolution();
            TacticalAttackResult turned = scene.trySpearAction(Kind.TURN, facing);
            assertEquals(2 + 2 * Facing.E.stepsTo(facing), scene.getTacticalTimeQuanta());
            assertEquals(facing, scene.getSpearClashState().getFacing());
            assertEquals(facing != Facing.E, turned.isAccepted());
            if (facing == Facing.E) assertSame(previous, scene.getLastResolution());
            else assertEquals(Arrays.asList(2L * Facing.E.stepsTo(facing)), times(turned, TURN_COMPLETED));
        }
        quietStance();
        assertFalse(scene.trySpearAction(Kind.BRACE, null).isAccepted());
        assertTrue(scene.getSpearClashState().isBraced());
        assertEquals(1, scene.trySpearAction(Kind.LOWER_SPEAR, null).getDuration());
        assertFalse(scene.getSpearClashState().isBraced());
        quietStance();
        move(0, 1);
        assertFalse(scene.getSpearClashState().isBraced());
        assertEquals(Facing.E, scene.getSpearClashState().getFacing());
        quietStance();
        assertTrue(scene.tryBreakoutTo(cell(0, -2)).isAccepted());
        assertFalse(scene.getSpearClashState().isBraced());
        assertEquals(Facing.E, scene.getSpearClashState().getFacing());

        begin(new SpearClashConfig(Facing.E, 3), at(0, 0), at(1, 0), 4, 2, 0, null,
            WolfTurn.recovering(100, false));
        TacticalAttackResult beforeAttack = brace();
        assertEquals(TacticalMoveResult.OCCUPIED, scene.tryMoveHunterTo(cell(1, 0)));
        assertTrue(scene.getSpearClashState().isBraced());
        assertSame(beforeAttack, scene.getLastResolution());
        TacticalAttackResult attack = scene.tryAttackCreature(cell(1, 0));
        assertEquals(Arrays.asList(0L), times(attack, STANCE_RELEASED));
        assertFalse(scene.getSpearClashState().isBraced());

        begin(new SpearClashConfig(Facing.E, 3, 4, 2, 2, 50, 30, 20), at(0, 0), at(3, 0),
            4, 2, 0, null, flight(3, 0));
        TacticalAttackResult coincident = brace();
        contact(coincident, Result.REPELLED, 4, 2, 6);
        assertTrue(coincident.getClash().getTimeline().indexOf(facts(coincident, BRACE_READY).get(0))
            < coincident.getClash().getTimeline().indexOf(facts(coincident, LEAP_CONTACT).get(0)));
    }

    private void quietStance() {
        begin(3, 4, 0, new StaticAabb(2.35, -3, 1.2, 6));
        brace();
        assertTrue(scene.getSpearClashState().isBraced());
    }

    @Test
    void r15InvalidBypassesAreExcludedBeforeSampling() {
        StaticAabb wall = new StaticAabb(-1.5, -1.5, 1, 3);
        begin(1, 3, 0, wall);
        contact(brace(), Result.REPELLED, 4, 2, 6);
        assertEquals(1, scene.getSpearClashState().getRandomCursor());
        for (int close : new int[]{20, 0}) {
            begin(new SpearClashConfig(Facing.E, 1, 2, 2, 2, 0, 30, close),
                at(0, 0), at(3, 0), 4, 2, 0, cursor -> { throw new AssertionError("Unexpected draw"); },
                WolfTurn.ready(false, false), wall);
            TacticalAttackResult result = brace();
            if (close == 20) contact(result, Result.BROKE_THROUGH, 4, 6, 2);
            else {
                contact(result, Result.STOPPED, 4, 2, 4);
                state(6, 4, 2, 2, 0, WolfPhase.RECOVERING, 2, 0);
            }
            assertEquals(0, scene.getSpearClashState().getRandomCursor());
        }
        for (StaticAabb north : Arrays.asList(new StaticAabb(-1.5, .5, 1, 1), new StaticAabb(.45, .95, .1, .1))) {
            begin(1, 3, 0, north);
            contact(brace(), Result.GRAZED_PAST, 4, 4, 4);
            assertEquals(at(-1, -1), scene.getCreaturePosition());
        }
    }

    @Test
    void r16LeapChecksSweptPathAndUsesOnlyAnIncomingVisibleSector() {
        SpearClashConfig config = new SpearClashConfig(Facing.E, 3);
        begin(config, at(0, 0), at(3, 0), 4, 2, 0, null, flight(3, 0),
            new StaticAabb(2.45, -.5, .1, 1));
        contact(brace(), Result.BLOCKED, 4, 0, 4);
        assertEquals(at(3, 0), scene.getCreaturePosition());
        begin(3, 3, 0);
        move(1, 0);
        contact(move(2, 0), Result.BLOCKED, 2, 0, 4);
        assertEquals(at(3, 0), scene.getCreaturePosition());

        for (int side : new int[]{2, -2}) {
            begin(3, 2, side);
            contact(brace(), Result.REPELLED, 4, 2, 6);
        }
        begin(config, at(0, 0), at(2, 2), 4, 2, 0, null, flight(2, 2), new StaticAabb(.25, .5, .25, .25));
        contact(brace(), Result.MISS, 4, 0, 4);
        assertEquals(0, scene.getSpearClashState().getRandomCursor());
        begin(3, 3, 2);
        assertEquals(WolfPhase.STEPPING, scene.getSpearClashState().getWolfPhase());
        begin(3, 0, 3);
        contact(brace(), Result.HIT, 4, 2, 4);
        state(6, 3, 2, 0, 1, WolfPhase.RECOVERING, 2, 0);
        begin(config, at(0, 0), at(0, 3), 1, 2, 0, null, WolfTurn.ready(false, false));
        contact(brace(), Result.HIT, 4, 2, 4);
        state(4, 0, 2, 0, 1, WolfPhase.READY, 0, 0);
        assertFalse(scene.trySpearAction(Kind.BRACE, null).isAccepted());
    }

    @Test
    void r17FailedRandomAndRecoveryOverflowAreAtomic() {
        SpearClashConfig config = new SpearClashConfig(Facing.E, 3);
        int[] calls = {0};
        begin(config, at(0, 0), at(3, 0), 4, 2, 0, i -> { calls[0]++; return Double.NaN; },
            WolfTurn.ready(false, false));
        assertThrows(IllegalArgumentException.class, () -> scene.previewSpearAction(Kind.BRACE, null));
        assertThrows(IllegalArgumentException.class, () -> scene.trySpearAction(Kind.BRACE, null));
        assertEquals(2, calls[0]);
        assertNull(scene.getLastResolution());
        state(0, 4, 2, 3, 0, WolfPhase.PREPARING, 2, 0);

        RuntimeException failure = new IllegalStateException("Fixture failure");
        begin(config, at(0, 0), at(3, 0), 4, 2, 0, i -> { throw failure; }, WolfTurn.ready(false, false));
        assertSame(failure, assertThrows(RuntimeException.class, () -> scene.trySpearAction(Kind.BRACE, null)));
        state(0, 4, 2, 3, 0, WolfPhase.PREPARING, 2, 0);

        long now = Long.MAX_VALUE - 7;
        begin(config, at(0, 0), at(3, 0), 4, 2, now, null, WolfTurn.ready(false, false));
        assertThrows(ArithmeticException.class, () -> scene.previewSpearAction(Kind.BRACE, null));
        assertThrows(ArithmeticException.class, () -> scene.trySpearAction(Kind.BRACE, null));
        assertNull(scene.getLastResolution());
        state(now, 4, 2, 3, 0, WolfPhase.PREPARING, 2, 0);
    }

    @Test
    void r18DeathReleasesTheLandingCellAndBreakoutHonorsContactBeforeMovement() {
        SpearClashConfig config = new SpearClashConfig(Facing.E, 3);
        begin(config, at(0, 0), at(3, 0), 4, 1, 0, null, WolfTurn.ready(false, false));
        contact(brace(), Result.REPELLED, 4, 2, 6);
        assertEquals(SceneMode.EXPLORATION, scene.getMode());
        state(4, 4, 0, 3, 0, WolfPhase.READY, 0, 1);
        assertEquals(TacticalEntryResult.Outcome.SUCCESS, scene.enterTactical());
        assertFalse(scene.getPlacement().creatureCell().isPresent());
        move(1, 0);
        move(2, 0);
        move(3, 0);

        begin(config, at(0, 0), at(3, 0), 4, 2, 2, null, flight(3, 0).launch());
        TacticalAttackResult hit = scene.tryBreakoutTo(cell(0, 2));
        contact(hit, Result.HIT, 2, 2, 4);
        assertEquals(Outcome.INTERRUPTED, hit.getOutcome());
        assertTrue(times(hit, BREAKOUT_MOVED).isEmpty());
        assertEquals(at(0, 0), scene.getHunterPosition());
        begin(3, 3, 0);
        TacticalAttackResult escape = scene.tryBreakoutTo(cell(0, -2));
        assertEquals(Outcome.ESCAPED, escape.getOutcome());
        assertEquals(Arrays.asList(2L), times(escape, BREAKOUT_MOVED));
        contact(escape, Result.MISS, 4, 0, 4);
        state(4, 4, 2, 1, 0, WolfPhase.RECOVERING, 4, 0);
    }
}
