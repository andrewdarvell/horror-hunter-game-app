package ru.darvell.game.hhg;

import org.junit.jupiter.api.Test;
import ru.darvell.game.hhg.domain.world.WorldPosition;

import static org.junit.jupiter.api.Assertions.*;

class TacticalEntryTransitionTest {
    private final WorldPosition hunterStart = new WorldPosition(-0.2, 0.1);
    private final WorldPosition creatureStart = new WorldPosition(4.8, -0.1);
    private final WorldPosition hunterEnd = new WorldPosition(-0.5, 0.5);
    private final WorldPosition creatureEnd = new WorldPosition(4.5, -0.5);

    @Test
    void returnsExactEndpointsAndSharedSmoothstepForBothActors() {
        TacticalEntryTransition transition = transition();
        assertSame(hunterStart, transition.getHunterPosition());
        assertSame(creatureStart, transition.getCreaturePosition());
        assertFalse(transition.isComplete());

        transition.advance(0.055);
        // u = 1/4: smoothstep = 5/32, что отличается от линейного lerp.
        assertPosition(transition.getHunterPosition(), -0.246875, 0.1625);
        assertPosition(transition.getCreaturePosition(), 4.753125, -0.1625);
        transition.advance(0.055);
        assertPosition(transition.getHunterPosition(), -0.35, 0.3);
        assertPosition(transition.getCreaturePosition(), 4.65, -0.3);

        transition.advance(0.11);
        assertTrue(transition.isComplete());
        assertSame(hunterEnd, transition.getHunterPosition());
        assertSame(creatureEnd, transition.getCreaturePosition());
        transition.advance(100);
        assertSame(hunterEnd, transition.getHunterPosition());
        assertSame(creatureEnd, transition.getCreaturePosition());
    }

    @Test
    void splittingElapsedTimeKeepsTheSameCurveAndFiniteCompletion() {
        TacticalEntryTransition single = transition();
        TacticalEntryTransition split = transition();
        single.advance(0.15);
        for (int i = 0; i < 15; i++) split.advance(0.01);
        assertPosition(split.getHunterPosition(), single.getHunterPosition().getX(),
            single.getHunterPosition().getY());
        assertPosition(split.getCreaturePosition(), single.getCreaturePosition().getX(),
            single.getCreaturePosition().getY());
        split.advance(0.1);
        assertSame(hunterEnd, split.getHunterPosition());
        assertTrue(split.isComplete());
    }

    @Test
    void invalidAndZeroIntervalsDoNotAdvanceAndHugeFiniteIntervalEndsExactly() {
        TacticalEntryTransition transition = transition();
        transition.advance(0.055);
        WorldPosition hunter = transition.getHunterPosition();
        WorldPosition creature = transition.getCreaturePosition();
        for (double delta : new double[] {0, -1, Double.NaN,
            Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
            transition.advance(delta);
            assertEquals(hunter, transition.getHunterPosition());
            assertEquals(creature, transition.getCreaturePosition());
            assertFalse(transition.isComplete());
        }
        transition.advance(Double.MAX_VALUE);
        assertTrue(transition.isComplete());
        assertSame(hunterEnd, transition.getHunterPosition());
        assertSame(creatureEnd, transition.getCreaturePosition());
    }

    @Test
    void coincidentEndpointsStayStillAndCompleteNormally() {
        TacticalEntryTransition transition = new TacticalEntryTransition(
            hunterEnd, creatureEnd, hunterEnd, creatureEnd);
        transition.advance(0.11);
        assertEquals(hunterEnd, transition.getHunterPosition());
        assertEquals(creatureEnd, transition.getCreaturePosition());
        assertFalse(transition.isComplete());
        transition.advance(0.11);
        assertTrue(transition.isComplete());
        assertSame(hunterEnd, transition.getHunterPosition());
        assertSame(creatureEnd, transition.getCreaturePosition());
    }

    private TacticalEntryTransition transition() {
        return new TacticalEntryTransition(hunterStart, creatureStart, hunterEnd, creatureEnd);
    }

    private static void assertPosition(WorldPosition position, double x, double y) {
        assertEquals(x, position.getX(), 1e-12);
        assertEquals(y, position.getY(), 1e-12);
    }
}
