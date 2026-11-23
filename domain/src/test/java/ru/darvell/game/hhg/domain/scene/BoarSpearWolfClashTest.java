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
import ru.darvell.game.hhg.domain.tactical.SpearClashState;
import ru.darvell.game.hhg.domain.tactical.SpearClashState.Result;
import ru.darvell.game.hhg.domain.tactical.SpearClashState.WolfPhase;
import ru.darvell.game.hhg.domain.tactical.TacticalAttackResult;
import ru.darvell.game.hhg.domain.tactical.TacticalAttackResult.Kind;
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

    private void start(long seed, StaticAabb... obstacles) {
        start(new SpearClashConfig(Facing.E, seed), 3, 0, 4, 2, 0, null, obstacles);
    }

    private void start(SpearClashConfig config, int wx, int wy, int hunterHp, int wolfHp, long now,
                       LongToDoubleFunction sample, StaticAabb... obstacles) {
        WorldGeometry geometry = new WorldGeometry(Arrays.asList(obstacles));
        HunterState hunter = new HunterState(point(0, 0), 1, .4).withDamage(4 - hunterHp);
        CreaturePatrolState wolf = new CreaturePatrolState(Arrays.asList(point(wx, wy), point(wx + 1, wy)),
            BEHAVIOR, .4, geometry).withDamage(2 - wolfHp);
        scene = sample == null && now == 0 ? new WorldSceneState(hunter, wolf, GRID, geometry, config)
            : new WorldSceneState(hunter, wolf, GRID, geometry, config, now, sample);
        assertEquals(TacticalEntryResult.Outcome.SUCCESS, scene.enterTactical());
    }

    private static WorldPosition point(int x, int y) { return new WorldPosition(x, y); }

    private void step(int x, int y) {
        assertEquals(TacticalMoveResult.SUCCESS, scene.tryMoveHunterTo(new CellCoordinates(x, y)));
    }

    private TacticalAttackResult action(Kind kind) {
        TacticalAttackResult result = scene.trySpearAction(kind, null);
        assertTrue(result.isAccepted());
        assertSame(result, scene.getLastResolution());
        return result;
    }

    private TacticalAttackResult collide() {
        action(Kind.BRACE);
        return action(Kind.HOLD_BRACE);
    }

    private void state(long now, int h, int w, int x, int y, WolfPhase phase, long remaining, long cursor) {
        assertEquals(now, scene.getTacticalTimeQuanta());
        assertEquals(h, scene.getHunterHealth());
        assertEquals(w, scene.getCreatureHealth());
        assertEquals(point(x, y), scene.getCreaturePosition());
        assertEquals(phase, scene.getSpearClashState().getWolfPhase());
        assertEquals(remaining, scene.getSpearClashState().getWolfRemaining());
        assertEquals(cursor, scene.getSpearClashState().getRandomCursor());
        assertNull(scene.getBiteState());
    }

    private void contact(TacticalAttackResult result, Result contact, long hunterRecovery, long wolfRecovery) {
        assertEquals(contact, result.getClash().getContact());
        assertEquals(hunterRecovery, result.getClash().getHunterRecovery());
        assertEquals(wolfRecovery, result.getClash().getWolfRecovery());
        if (contact != Result.MISS && contact != Result.BLOCKED) {
            assertFalse(scene.getSpearClashState().isBraced());
        }
        assertEquals(Facing.E, scene.getSpearClashState().getFacing());
        assertTrue(result.getEffects().stream().noneMatch(effect -> effect == TacticalAttackResult.Effect.BITE));
    }

    @Test
    void s01TurnsPayForShortestArcAndOnlyAcceptedCommandsDropStance() {
        for (Facing facing : Arrays.asList(Facing.E, Facing.NE, Facing.N, Facing.W)) {
            start(new SpearClashConfig(Facing.E, 3), 1, 0, 4, 2, 0, null);
            action(Kind.BRACE);
            TacticalAttackResult previous = scene.getLastResolution();
            TacticalAttackResult turn = scene.trySpearAction(Kind.TURN, facing);
            assertEquals(2 + 2 * Facing.E.stepsTo(facing), scene.getTacticalTimeQuanta());
            assertEquals(facing, scene.getSpearClashState().getFacing());
            assertEquals(facing != Facing.E, turn.isAccepted());
            assertEquals(facing == Facing.E, scene.getSpearClashState().isBraced());
            if (facing == Facing.E) assertSame(previous, scene.getLastResolution());
        }
        start(new SpearClashConfig(Facing.E, 3), 1, 0, 4, 2, 0, null);
        action(Kind.BRACE);
        assertEquals(TacticalMoveResult.OCCUPIED, scene.tryMoveHunterTo(new CellCoordinates(1, 0)));
        assertFalse(scene.trySpearAction(Kind.BRACE, null).isAccepted());
        assertFalse(scene.trySpearAction(Kind.HOLD_BRACE, null).isAccepted());
        assertTrue(scene.getSpearClashState().isBraced());
        assertEquals(1, action(Kind.LOWER_SPEAR).getDuration());
        assertFalse(scene.getSpearClashState().isBraced());
        action(Kind.BRACE);
        assertTrue(scene.tryAttackCreature(new CellCoordinates(1, 0)).isAccepted());
        assertFalse(scene.getSpearClashState().isBraced());
        action(Kind.BRACE);
        step(0, 1);
        assertFalse(scene.getSpearClashState().isBraced());
        action(Kind.BRACE);
        assertTrue(scene.tryBreakoutTo(new CellCoordinates(0, 3)).isAccepted());
        assertFalse(scene.getSpearClashState().isBraced());
        assertEquals(Facing.E, scene.getSpearClashState().getFacing());
    }

    @Test
    void s02PublishedStancePausesAndOneConfirmedActionCoversContactAndRecovery() {
        start(3);
        assertEquals(WolfPhase.PREPARING, scene.getSpearClashState().getWolfPhase());
        assertEquals(2, scene.getSpearClashState().getWolfRemaining());
        TacticalAttackResult braced = action(Kind.BRACE);
        assertEquals(Arrays.asList(BRACE_READY, LEAP_LAUNCHED), types(braced));
        for (int frame = 0; frame < 20; frame++) {
            scene.update(0, 0, .25, false);
            scene.previewSpearAction(Kind.HOLD_BRACE, null);
        }
        state(2, 4, 2, 3, 0, WolfPhase.IN_FLIGHT, 2, 0);
        assertTrue(scene.getSpearClashState().isBraced());
        assertSame(braced, scene.getLastResolution());
        TacticalAttackResult hold = action(Kind.HOLD_BRACE);
        assertEquals(1, hold.getPlannedDuration());
        assertEquals(2, hold.getDuration());
        assertEquals(Arrays.asList(LEAP_CONTACT, HUNTER_RECOVERY_STARTED, WOLF_RECOVERY_STARTED,
            HUNTER_RECOVERY_ENDED), types(hold));
        assertEquals(Arrays.asList(1, 1, 1, 2), times(hold));
    }

    @Test
    void s03ReboundLeavesRecoveryForWolfAfterHuntersDecision() {
        start(3);
        contact(collide(), Result.REPELLED, 2, 6);
        state(6, 4, 1, 3, 0, WolfPhase.RECOVERING, 4, 1);
        assertEquals(point(0, 0), scene.getHunterPosition());
        step(0, 1);
        state(8, 4, 1, 3, 0, WolfPhase.RECOVERING, 2, 1);
        step(0, 2);
        state(10, 4, 1, 3, 0, WolfPhase.SPENT, 0, 1);
        assertEquals(Arrays.asList(WOLF_RECOVERY_ENDED), types(scene.getLastResolution()));
    }

    @Test
    void s04PassTakesAFreeSideAndPreservesFacing() {
        start(1);
        TacticalAttackResult outcome = collide();
        contact(outcome, Result.GRAZED_PAST, 4, 4);
        assertEquals(Arrays.asList(point(3, 0), point(2, 0), point(1, 0), point(1, 1), point(0, 1), point(-1, 1)),
            outcome.getClash().getRoute());
        state(8, 4, 1, -1, 1, WolfPhase.SPENT, 0, 1);
        assertEquals(point(0, 0), scene.getHunterPosition());
        start(1, new StaticAabb(-1.5, .5, 1, 1));
        contact(collide(), Result.GRAZED_PAST, 4, 4);
        state(8, 4, 1, -1, -1, WolfPhase.SPENT, 0, 1);
    }

    @Test
    void s05BreakThroughAndLaterOrdinaryStrikesNeverArmBite() {
        start(0);
        TacticalAttackResult clash = collide();
        contact(clash, Result.BROKE_THROUGH, 6, 2);
        assertEquals(Arrays.asList(1, 1, 1, 2, 4), times(clash));
        state(10, 4, 2, 1, 0, WolfPhase.SPENT, 0, 1);
        step(0, 1);
        assertTrue(types(scene.getLastResolution()).isEmpty());
        for (int strike = 0; strike < 2; strike++) {
            TacticalAttackResult attack = scene.tryAttackCreature(new CellCoordinates(1, 0));
            assertEquals(4, attack.getHunterAfter());
            assertEquals(strike == 0 ? Arrays.asList(HIT) : Arrays.asList(HIT, TACTICAL_EXITED), types(attack));
        }
        state(20, 4, 0, 1, 0, WolfPhase.SPENT, 0, 1);
    }

    @Test
    void s06WallFiltersBeforeResolverIncludingZeroAndOnePositiveCandidates() {
        StaticAabb wall = new StaticAabb(-1.5, -1.5, 1, 3);
        start(1, wall);
        contact(collide(), Result.REPELLED, 2, 6);
        state(6, 4, 1, 3, 0, WolfPhase.RECOVERING, 4, 1);
        for (int weight : new int[] {0, 20}) {
            SpearClashConfig config = new SpearClashConfig(Facing.E, 1, 2, 2, 2, 0, 30, weight);
            start(config, 3, 0, 4, 2, 0, cursor -> { throw new AssertionError("No draw expected"); }, wall);
            TacticalAttackResult outcome = collide();
            if (weight == 0) {
                contact(outcome, Result.STOPPED, 2, 4);
                state(6, 4, 2, 2, 0, WolfPhase.RECOVERING, 2, 0);
            } else {
                contact(outcome, Result.BROKE_THROUGH, 6, 2);
                state(10, 4, 2, 1, 0, WolfPhase.SPENT, 0, 0);
            }
        }
    }

    @Test
    void s07SweptPathAndOccupiedCenterBlockLeap() {
        start(3, new StaticAabb(2.45, -.5, .1, 1));
        contact(collide(), Result.BLOCKED, 0, 4);
        state(4, 4, 2, 3, 0, WolfPhase.RECOVERING, 4, 0);
        start(3);
        step(1, 0);
        step(2, 0);
        contact(scene.getLastResolution(), Result.BLOCKED, 0, 4);
        state(4, 4, 2, 3, 0, WolfPhase.RECOVERING, 4, 0);
        assertEquals(point(2, 0), scene.getHunterPosition());
    }

    @Test
    void s08SideLeapHitsOnceAndCanBeLethal() {
        for (int hp : new int[] {4, 1}) {
            start(new SpearClashConfig(Facing.E, 3), 0, 3, hp, 2, 0, null);
            contact(collide(), Result.HIT, 2, 4);
            assertEquals(point(0, 0), scene.getHunterPosition());
            if (hp == 4) state(6, 3, 2, 0, 1, WolfPhase.RECOVERING, 2, 0);
            else {
                state(4, 0, 2, 0, 1, WolfPhase.SPENT, 0, 0);
                assertFalse(scene.trySpearAction(Kind.BRACE, null).isAccepted());
            }
        }
    }

    @Test
    void s09DiagonalBoundaryUsesEuclideanRangeAndClosedLos() {
        for (int y : new int[] {2, -2}) {
            start(new SpearClashConfig(Facing.E, 3), 2, y, 4, 2, 0, null);
            contact(collide(), Result.REPELLED, 2, 6);
            state(6, 4, 1, 2, y, WolfPhase.RECOVERING, 4, 1);
        }
        start(new SpearClashConfig(Facing.E, 3), 3, 2, 4, 2, 0, null);
        assertEquals(WolfPhase.SPENT, scene.getSpearClashState().getWolfPhase());
        start(new SpearClashConfig(Facing.E, 3), 2, 2, 4, 2, 0, null,
            new StaticAabb(.25, .5, .25, .25));
        contact(collide(), Result.MISS, 0, 4);
        state(4, 4, 2, 1, 1, WolfPhase.RECOVERING, 4, 0);
        assertTrue(scene.getSpearClashState().isBraced());
    }

    @Test
    void s10LeapDoesNotRetargetMovingHunter() {
        start(3);
        step(0, 1);
        step(0, 2);
        contact(scene.getLastResolution(), Result.MISS, 0, 4);
        state(4, 4, 2, 1, 0, WolfPhase.RECOVERING, 4, 0);
        assertEquals(point(0, 2), scene.getHunterPosition());
    }

    @Test
    void s11HitInterruptsPreparationOrTurnBeforeCompletion() {
        for (Kind kind : Arrays.asList(Kind.BRACE, Kind.TURN)) {
            start(new SpearClashConfig(Facing.E, 3, 6, 2, 2, 50, 30, 20), 3, 0, 4, 2, 0, null);
            TacticalAttackResult outcome = scene.trySpearAction(kind, Facing.W);
            assertEquals(TacticalAttackResult.Outcome.INTERRUPTED, outcome.getOutcome());
            contact(outcome, Result.HIT, 2, 4);
            assertEquals(Arrays.asList(LEAP_LAUNCHED, LEAP_CONTACT, ACTION_INTERRUPTED, HUNTER_RECOVERY_STARTED,
                WOLF_RECOVERY_STARTED, HUNTER_RECOVERY_ENDED), types(outcome));
            state(6, 3, 2, 1, 0, WolfPhase.RECOVERING, 2, 0);
            step(0, 1);
            assertEquals(Arrays.asList(WOLF_RECOVERY_ENDED), types(scene.getLastResolution()));
        }
    }

    @Test
    void s12ReadOnlyPreviewMatchesApplyAndAStaleCommandRevalidates() {
        start(1);
        TacticalAttackResult brace = action(Kind.BRACE);
        TacticalAttackResult first = scene.previewSpearAction(Kind.HOLD_BRACE, null);
        for (int read = 0; read < 5; read++) {
            TacticalAttackResult next = scene.previewSpearAction(Kind.HOLD_BRACE, null);
            assertEquals(first.getClash(), next.getClash());
            assertEquals(types(first), types(next));
            assertEquals(times(first), times(next));
            assertSame(brace, scene.getLastResolution());
            state(2, 4, 2, 3, 0, WolfPhase.IN_FLIGHT, 2, 0);
        }
        TacticalAttackResult applied = action(Kind.HOLD_BRACE);
        assertEquals(first.getClash(), applied.getClash());
        assertEquals(first.getHunterAfter(), applied.getHunterAfter());
        assertEquals(first.getCreatureAfter(), applied.getCreatureAfter());
        assertEquals(types(first), types(applied));
        assertEquals(times(first), times(applied));
        assertEquals(1, first.getClash().getAfter().getRandomCursor());
        start(1);
        assertEquals(first.getClash(), collide().getClash());
        start(1);
        action(Kind.BRACE);
        assertEquals(Result.GRAZED_PAST, scene.previewSpearAction(Kind.HOLD_BRACE, null).getClash().getContact());
        step(0, 1);
        assertFalse(scene.trySpearAction(Kind.HOLD_BRACE, null).isAccepted());
        state(4, 4, 2, 1, 0, WolfPhase.RECOVERING, 4, 0);
        assertEquals(.7457817572627011, SpearClash.sample(1, 1));
    }

    @Test
    void s13BadSamplesAndOverflowDoNotPublishPartialResults() {
        SpearClashConfig config = new SpearClashConfig(Facing.E, 3);
        int[] samples = {0};
        start(config, 3, 0, 4, 2, 0, cursor -> { samples[0]++; return Double.NaN; });
        TacticalAttackResult brace = action(Kind.BRACE);
        assertThrows(IllegalArgumentException.class, () -> scene.previewSpearAction(Kind.HOLD_BRACE, null));
        assertSame(brace, scene.getLastResolution());
        assertThrows(IllegalArgumentException.class, () -> scene.trySpearAction(Kind.HOLD_BRACE, null));
        assertEquals(2, samples[0]);
        assertSame(brace, scene.getLastResolution());
        state(2, 4, 2, 3, 0, WolfPhase.IN_FLIGHT, 2, 0);

        RuntimeException failure = new RuntimeException("Fixture failure");
        start(config, 3, 0, 4, 2, 0, cursor -> { throw failure; });
        TacticalAttackResult beforeFailure = action(Kind.BRACE);
        assertSame(failure, assertThrows(RuntimeException.class, () -> scene.trySpearAction(Kind.HOLD_BRACE, null)));
        assertSame(beforeFailure, scene.getLastResolution());
        state(2, 4, 2, 3, 0, WolfPhase.IN_FLIGHT, 2, 0);

        start(config, 3, 0, 4, 2, Long.MAX_VALUE - 7, cursor -> SpearClash.sample(3, cursor));
        TacticalAttackResult beforeOverflow = action(Kind.BRACE);
        assertThrows(ArithmeticException.class, () -> scene.previewSpearAction(Kind.HOLD_BRACE, null));
        assertThrows(ArithmeticException.class, () -> scene.trySpearAction(Kind.HOLD_BRACE, null));
        assertSame(beforeOverflow, scene.getLastResolution());
        state(Long.MAX_VALUE - 5, 4, 2, 3, 0, WolfPhase.IN_FLIGHT, 2, 0);
    }

    @Test
    void s14SimultaneousStrikeAndBraceAreResolvedBeforeContact() {
        start(new SpearClashConfig(Facing.E, 3, 2, 4, 2, 50, 30, 20), 2, 0, 4, 1, 0, null);
        step(1, 0);
        TacticalAttackResult strike = scene.tryAttackCreature(new CellCoordinates(2, 0));
        assertEquals(Arrays.asList(LEAP_LAUNCHED, HIT, LEAP_CANCELLED, TACTICAL_EXITED), types(strike));
        state(6, 4, 0, 2, 0, WolfPhase.SPENT, 0, 0);
        assertEquals(SceneMode.EXPLORATION, scene.getMode());
        start(new SpearClashConfig(Facing.E, 3, 4, 2, 2, 50, 30, 20), 3, 0, 4, 2, 0, null);
        TacticalAttackResult outcome = action(Kind.BRACE);
        contact(outcome, Result.REPELLED, 2, 6);
        assertEquals(Arrays.asList(LEAP_LAUNCHED, BRACE_READY, LEAP_CONTACT, HUNTER_RECOVERY_STARTED,
            WOLF_RECOVERY_STARTED, HUNTER_RECOVERY_ENDED), types(outcome));
        state(6, 4, 1, 3, 0, WolfPhase.RECOVERING, 4, 1);
    }

    @Test
    void s15LethalSpearReleasesOccupancyAtLandingCell() {
        start(new SpearClashConfig(Facing.E, 3), 3, 0, 4, 1, 0, null);
        contact(collide(), Result.REPELLED, 2, 6);
        state(4, 4, 0, 3, 0, WolfPhase.SPENT, 0, 1);
        assertEquals(SceneMode.EXPLORATION, scene.getMode());
        assertEquals(TacticalEntryResult.Outcome.SUCCESS, scene.enterTactical());
        assertFalse(scene.getPlacement().creatureCell().isPresent());
        step(1, 0);
        step(2, 0);
        step(3, 0);
    }

    @Test
    void s16JumpResolvesBeforeBreakoutMoveAndCanBlockTheRoute() {
        for (int hp : new int[] {4, 1}) {
            start(new SpearClashConfig(Facing.E, 3), 3, 0, hp, 2, 0, null);
            action(Kind.BRACE);
            TacticalAttackResult result = scene.tryBreakoutTo(new CellCoordinates(0, 2));
            assertEquals(TacticalAttackResult.Outcome.INTERRUPTED, result.getOutcome());
            assertEquals(Result.HIT, result.getClash().getContact());
            assertEquals(point(0, 0), scene.getHunterPosition());
            assertEquals(hp - 1, scene.getHunterHealth());
            assertEquals(hp == 1 ? 4 : 6, scene.getTacticalTimeQuanta());
            assertFalse(types(result).contains(BREAKOUT_MOVED));
        }
        start(3);
        step(1, 1);
        TacticalAttackResult result = scene.tryBreakoutTo(new CellCoordinates(1, -1));
        assertEquals(TacticalAttackResult.Outcome.INTERRUPTED, result.getOutcome());
        assertEquals(point(1, 1), scene.getHunterPosition());
        assertEquals(Arrays.asList(LEAP_MISSED, WOLF_RECOVERY_STARTED, BREAKOUT_BLOCKED), types(result));
        state(4, 4, 2, 1, 0, WolfPhase.RECOVERING, 4, 0);
    }

    @Test
    void s17ExitPreservesRecoveryButCancelsUnresolvedFlight() {
        start(3);
        TacticalAttackResult collision = collide();
        assertEquals(TacticalExitResult.SUCCESS, scene.tryExitTactical());
        scene.update(0, 0, .1, false);
        assertEquals(TacticalEntryResult.Outcome.SUCCESS, scene.enterTactical());
        state(6, 4, 1, 3, 0, WolfPhase.RECOVERING, 4, 1);
        assertSame(collision, scene.getLastResolution());
        start(3);
        action(Kind.BRACE);
        assertEquals(TacticalExitResult.SUCCESS, scene.tryExitTactical());
        assertEquals(TacticalEntryResult.Outcome.SUCCESS, scene.enterTactical());
        state(2, 4, 2, 3, 0, WolfPhase.SPENT, 0, 0);
        assertFalse(scene.getSpearClashState().isBraced());
    }

    @Test
    void s18BreakoutAndBoundsKeepPhysicalTimeline() {
        start(3);
        TacticalAttackResult escape = scene.tryBreakoutTo(new CellCoordinates(0, -2));
        assertEquals(TacticalAttackResult.Outcome.ESCAPED, escape.getOutcome());
        assertEquals(point(0, -2), scene.getHunterPosition());
        assertEquals(Arrays.asList(LEAP_LAUNCHED, BREAKOUT_MOVED, LEAP_MISSED, WOLF_RECOVERY_STARTED, TACTICAL_EXITED),
            types(escape));
        assertEquals(Arrays.asList(1, 1, 2, 2, 2), times(escape));
        state(4, 4, 2, 1, 0, WolfPhase.RECOVERING, 4, 0);
        start(1, new StaticAabb(-2.5, -2, 2, 4));
        contact(collide(), Result.REPELLED, 2, 6);
        start(1, new StaticAabb(.45, .95, .1, .1));
        contact(collide(), Result.GRAZED_PAST, 4, 4);
        assertEquals(point(-1, -1), scene.getCreaturePosition());
    }

    private static List<TacticalAttackResult.EventType> types(TacticalAttackResult result) {
        return result.getEvents().stream().map(TacticalAttackResult.Event::getType).collect(Collectors.toList());
    }

    private static List<Integer> times(TacticalAttackResult result) {
        return result.getEvents().stream().map(TacticalAttackResult.Event::getOffset).collect(Collectors.toList());
    }
}
