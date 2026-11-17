package ru.darvell.game.hhg.domain.actor;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import ru.darvell.game.hhg.domain.world.*;
import static org.junit.jupiter.api.Assertions.*;

class CreatureExitLifecycleTest {
    CreaturePatrolState creature(WorldGeometry g) {
        return new CreaturePatrolState(Arrays.asList(new WorldPosition(0,0),new WorldPosition(2,0)),1,.1,.5,3,g);
    }
    @Test void deadAndPureDetection() {
        CreaturePatrolState c=creature(WorldGeometry.empty());
        assertTrue(c.canDetect(new WorldPosition(3,0)));assertFalse(c.isAlerted());assertNull(c.getLastSeenHunterPosition());
        c=c.withDamage(2);WorldPosition position=c.getPosition();
        c.advancePatrol(100);assertFalse(c.checkDetection(new WorldPosition(0,0)));
        assertSame(position,c.getPosition());assertEquals(.5,c.getWaitRemaining());
        assertThrows(IllegalArgumentException.class,()->creature(WorldGeometry.empty()).withDamage(2).advancePatrol(-1));
    }
    @Test void patrolResumesOrSafelyStopsAfterSnap() {
        CreaturePatrolState c=creature(WorldGeometry.empty());c.advancePatrol(.25);
        c=c.withTacticalPosition(new WorldPosition(.5,.5));c.advancePatrol(.25);
        assertEquals(0,c.getWaitRemaining());c.advancePatrol(.5);
        assertNotEquals(new WorldPosition(.5,.5),c.getPosition());
        WorldGeometry g=new WorldGeometry(Arrays.asList(new StaticAabb(.9,.3,.2,.5)));
        CreaturePatrolState blocked=creature(g).withTacticalPosition(new WorldPosition(0,1));
        blocked.advancePatrol(100);assertEquals(new WorldPosition(0,1),blocked.getPosition());
        assertEquals(.5,blocked.getWaitRemaining());assertEquals(1,blocked.getTargetIndex());
        CreaturePatrolState alerted=creature(WorldGeometry.empty());alerted.checkDetection(new WorldPosition(0,0));
        alerted.advancePatrol(100);assertEquals(new WorldPosition(0,0),alerted.getPosition());
    }
}
