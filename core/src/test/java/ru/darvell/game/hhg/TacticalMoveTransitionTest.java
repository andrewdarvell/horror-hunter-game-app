package ru.darvell.game.hhg;

import org.junit.jupiter.api.Test;
import ru.darvell.game.hhg.domain.world.WorldPosition;
import static org.junit.jupiter.api.Assertions.*;

class TacticalMoveTransitionTest {
    @Test void durationSmoothstepInvalidTimeAndExactEnds() {
        WorldPosition a = new WorldPosition(.5, .5), b = new WorldPosition(1.5, 1.5), c = new WorldPosition(5, 0);
        TacticalEntryTransition t = TacticalEntryTransition.forHunterMove(a, b, c);
        for (double dt : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) t.advance(dt);
        assertSame(a, t.getHunterPosition());
        t.advance(.045);
        assertEquals(.65625, t.getHunterPosition().getX());
        t.advance(.045);
        assertEquals(1, t.getHunterPosition().getX());
        t.advance(.09);
        assertTrue(t.isComplete()); assertSame(b, t.getHunterPosition()); assertSame(c, t.getCreaturePosition());
        t.advance(Double.MAX_VALUE); assertSame(b, t.getHunterPosition());
        t = TacticalEntryTransition.forHunterMove(a, b, c); t.advance(100); assertSame(b, t.getHunterPosition());
    }
}
