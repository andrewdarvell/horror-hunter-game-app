package ru.darvell.game.hhg.domain.scene;

import org.junit.jupiter.api.Test;
import ru.darvell.game.hhg.domain.actor.*;
import ru.darvell.game.hhg.domain.tactical.*;
import ru.darvell.game.hhg.domain.world.*;
import java.lang.reflect.Field;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static ru.darvell.game.hhg.domain.scene.CombatRhythmTest.*;
import static ru.darvell.game.hhg.domain.tactical.TacticalAttackResult.Outcome.*;
import static ru.darvell.game.hhg.domain.tactical.TacticalAttackResult.RejectionReason.*;
import static ru.darvell.game.hhg.domain.tactical.PrototypeBiteState.Phase.*;

class TacticalBreakoutTest {
    private TacticalAttackResult run(WorldSceneState s, int x, int y) {
        Object[] before = snapshot(s);
        TacticalAttackResult preview = s.previewBreakoutTo(c(x,y));
        assertArrayEquals(before, snapshot(s));
        TacticalAttackResult r = s.tryBreakoutTo(c(x,y));
        assertEquals(preview.getOutcome(), r.getOutcome());
        assertEquals(preview.getHunterAfter(), r.getHunterAfter());
        assertEquals(preview.getPositionAfter(), r.getPositionAfter());
        assertEquals(preview.getModeAfter(), r.getModeAfter());
        assertEquals(preview.getDuration(), r.getDuration());
        assertEquals(preview.getPlannedDuration(), r.getPlannedDuration());
        assertEquals(events(preview), events(r));
        if (r.isAccepted()) assertSame(r, s.getLastResolution());
        assertThrows(UnsupportedOperationException.class, () -> r.getEvents().clear());
        return r;
    }

    @Test void F1CleanAndF2WoundedExit() {
        WorldSceneState s = scene(2,0);
        TacticalAttackResult r = run(s,4,0);
        assertEquals(ESCAPED,r.getOutcome()); assertEquals(2,r.getDuration());
        assertEquals(Arrays.asList("1:BREAKOUT_MOVED","2:TACTICAL_EXITED"),events(r));
        assertEquals(4,s.getHunterHealth()); phase(s,READY,0);
        s=scene(1,0); r=run(s,4,0);
        assertEquals(ESCAPED,r.getOutcome()); assertEquals(3,r.getDuration());
        assertEquals(3,s.getHunterHealth()); assertEquals(2,s.getCreatureHealth()); phase(s,RECOVERING,1);
        assertEquals(Arrays.asList("1:BITE","1:RECOVERY_STARTED","1:BREAKOUT_MOVED","3:TACTICAL_EXITED"),events(r));
        assertEquals(SceneMode.EXPLORATION,s.getMode()); assertNull(s.getPlacement());
    }

    @Test void F3OriginDeathAndF6EndpointDeathAreReachable() {
        WorldSceneState s=mistakes(); TacticalAttackResult r=run(s,4,0);
        assertEquals(INTERRUPTED,r.getOutcome()); assertEquals(1,r.getDuration()); assertEquals(3,r.getPlannedDuration());
        assertEquals(13,s.getTacticalTimeUnits()); assertEquals(new WorldPosition(1.5,.5),s.getHunterPosition());
        assertEquals(Arrays.asList("1:BITE","1:RECOVERY_STARTED","1:BREAKOUT_INTERRUPTED"),events(r));
        s=scene(1,0); for(int i=1;i<=11;i++) move(s,1,i%2);
        r=run(s,-1,1); assertEquals(INTERRUPTED,r.getOutcome()); assertEquals(2,r.getDuration());
        assertEquals(13,s.getTacticalTimeUnits()); assertEquals(new WorldPosition(-.5,1.5),s.getHunterPosition());
        assertEquals(Arrays.asList("1:RECOVERY_ENDED","1:BREAKOUT_MOVED","1:PREPARATION_STARTED",
            "2:BITE","2:RECOVERY_STARTED","2:BREAKOUT_INTERRUPTED"),events(r));
        phase(s,RECOVERING,3); assertEquals(SceneMode.TACTICAL,s.getMode());
    }

    @Test void F5AndF8RepeatCostsAndF6LateBite() {
        WorldSceneState s=scene(1,0); TacticalAttackResult first=run(s,3,0);
        assertEquals(MOVED_STILL_TACTICAL, first.getOutcome()); phase(s,RECOVERING,2);
        assertEquals(ESCAPED,run(s,5,0).getOutcome()); phase(s,READY,0);
        assertEquals(4,s.getTacticalTimeUnits()); assertEquals(3,s.getHunterHealth());
        assertEquals(MOVED_STILL_TACTICAL, first.getOutcome());
        s=scene(1,0); move(s,1,1); move(s,1,0); move(s,1,1);
        TacticalAttackResult r=run(s,-1,1); assertEquals(MOVED_STILL_TACTICAL,r.getOutcome());
        assertEquals(2,s.getHunterHealth()); assertEquals(5,s.getTacticalTimeUnits()); phase(s,RECOVERING,3);
    }

    @Test void F7HunterOnlyAndRecoveryWindow() {
        WorldSceneState s=scene(1,0); attack(s); attack(s); s.enterTactical();
        long time=s.getTacticalTimeUnits(); int hp=s.getHunterHealth();
        assertEquals(ESCAPED,run(s,3,0).getOutcome()); assertNull(s.getBiteState());
        assertEquals(time+2,s.getTacticalTimeUnits()); assertEquals(hp,s.getHunterHealth());
        s=scene(1,0); move(s,2,0); move(s,1,0); run(s,4,0);
        assertEquals(4,s.getHunterHealth()); assertEquals(5,s.getTacticalTimeUnits());
    }

    private WorldSceneState custom(int hx,int hy,int ex,int ey,WorldGeometry g,double size) {
        WorldSceneState s=new WorldSceneState(new HunterState(new WorldPosition((hx+.5)*size,(hy+.5)*size),1,.4*size),
            new CreaturePatrolState(Arrays.asList(new WorldPosition((ex+.5)*size,(ey+.5)*size),
                new WorldPosition((ex+.5)*size,(ey+1.5)*size)),1,.3*size,.5,3,g),new TacticalGrid(0,0,size),g);
        assertEquals(TacticalEntryResult.Outcome.SUCCESS,s.enterTactical()); return s;
    }

    private void rejected(WorldSceneState s,int x,int y,TacticalAttackResult.RejectionReason reason) {
        Object[] before=snapshot(s); TacticalAttackResult r=run(s,x,y);
        assertEquals(REJECTED,r.getOutcome()); assertEquals(reason,r.getRejectionReason());
        assertEquals(0,r.getDuration()); assertTrue(r.getEvents().isEmpty()); assertArrayEquals(before,snapshot(s));
    }

    @Test void F4GeometryWallsCornersBodyAndTouch() {
        WorldGeometry corrected=new WorldGeometry(Arrays.asList(new StaticAabb(1.95,1.5,.1,.1)));
        rejected(custom(0,0,-3,0,corrected,1),2,2,BLOCKED_PATH);
        WorldGeometry old=new WorldGeometry(Arrays.asList(new StaticAabb(1.95,1,.1,.1)));
        assertTrue(old.isCirclePathValid(new WorldPosition(.5,.5),new WorldPosition(4.5,4.5),.4));
        assertTrue(corrected.hasLineOfSight(new WorldPosition(.5,.5),new WorldPosition(2.5,2.5)));
        rejected(custom(0,0,-3,0,new WorldGeometry(Arrays.asList(new StaticAabb(2,0,.01,1))),1),4,0,BLOCKED_PATH);
        rejected(custom(-2,0,0,0,WorldGeometry.empty(),1),2,0,OCCUPIED_PATH);
        WorldGeometry touch=new WorldGeometry(Arrays.asList(new StaticAabb(2,.9,1,1)));
        assertNotEquals(REJECTED,run(custom(0,0,-3,0,touch,1),4,0).getOutcome());
    }

    @Test void InvalidStartAndEndpointGuardsAreAtomic() throws Exception {
        WorldGeometry g=new WorldGeometry(Arrays.asList(new StaticAabb(4,.0,1,1)));
        rejected(custom(0,0,-3,0,g,1),4,0,BLOCKED_CENTER);
        WorldSceneState s=custom(0,0,-3,0,g,1);
        Field hunter=WorldSceneState.class.getDeclaredField("hunter"); hunter.setAccessible(true);
        hunter.set(s,((HunterState)hunter.get(s)).withTacticalPosition(new WorldPosition(4.5,.5)));
        rejected(s,2,0,BLOCKED_START);
    }

    @Test void DistanceGuardsScalingAndNoIntermediateAttack() {
        WorldSceneState s=scene(2,0);
        for(int[] t:new int[][]{{3,0},{3,1},{5,3},{6,1},{Integer.MIN_VALUE,0},{Integer.MAX_VALUE,0}})
            rejected(s,t[0],t[1],INVALID_DISTANCE);
        Object[] before=snapshot(s); assertEquals(TacticalAttackResult.RejectionReason.INVALID_TARGET,s.tryBreakoutTo(null).getRejectionReason());
        assertArrayEquals(before,snapshot(s));
        assertEquals(3,run(scene(2,0),4,1).getPlannedDuration());
        assertEquals(3,run(scene(2,0),4,2).getPlannedDuration());
        assertEquals(2,run(custom(2,0,0,0,WorldGeometry.empty(),2),4,0).getDuration());
        s=custom(-2,1,0,0,WorldGeometry.empty(),1);
        assertEquals(MOVED_STILL_TACTICAL,run(s,2,1).getOutcome()); assertEquals(4,s.getHunterHealth()); phase(s,READY,0);
    }

    @Test void AllRecoveryRemaindersAndShortPassage() {
        for (int steps=1; steps<=3; steps++) for(int distance=2; distance<=3; distance++) {
            WorldSceneState s=scene(1,0);
            for(int i=1;i<=steps;i++) move(s,1,i%2);
            TacticalAttackResult r=run(s,1+distance,steps%2);
            assertEquals(steps%2==0 && distance==2 ? MOVED_STILL_TACTICAL : ESCAPED,r.getOutcome());
            assertEquals(3,s.getHunterHealth());
            int expected=Math.max(0,4-steps-r.getDuration());
            phase(s,expected==0?READY:RECOVERING,expected);
        }
        WorldGeometry wall=new WorldGeometry(Arrays.asList(new StaticAabb(3,0,.1,1)));
        WorldSceneState s=custom(0,0,-3,0,wall,1);
        rejected(s,4,0,BLOCKED_PATH);
        assertNotEquals(REJECTED,run(s,2,0).getOutcome());
    }

    @Test void OverflowAndRefusalsPublishNothingButActualElapsedFits() throws Exception {
        Field time=WorldSceneState.class.getDeclaredField("tacticalTimeUnits"); time.setAccessible(true);
        WorldSceneState s=scene(1,0); time.setLong(s,Long.MAX_VALUE-1); Object[] before=snapshot(s);
        assertThrows(ArithmeticException.class,()->s.tryBreakoutTo(c(4,0))); assertArrayEquals(before,snapshot(s));
        assertThrows(ArithmeticException.class,()->s.previewBreakoutTo(c(4,0))); assertArrayEquals(before,snapshot(s));
        WorldSceneState dying=mistakes(); time.setLong(dying,Long.MAX_VALUE-1);
        assertEquals(1,run(dying,4,0).getDuration()); assertEquals(Long.MAX_VALUE,dying.getTacticalTimeUnits());
        rejected(dying,4,0,TacticalAttackResult.RejectionReason.HUNTER_DEFEATED);
    }

    @Test void RecontactPreservesHistoryAndDoesNotImmediatelyReenter() {
        WorldSceneState s=scene(1,0); s.tryExitTactical();
        // Automatic entry provides an alerted creature and actual contact latch.
        s=custom(2,0,0,0,WorldGeometry.empty(),1);
        assertEquals(TacticalExitResult.ACTIVE_THREAT,s.tryExitTactical());
        s= new WorldSceneState(new HunterState(new WorldPosition(1.5,.5),1,.4),
            new CreaturePatrolState(Arrays.asList(new WorldPosition(.5,.5),new WorldPosition(.5,1.5)),1,.3,.5,3,WorldGeometry.empty()),
            new TacticalGrid(0,0,1),WorldGeometry.empty());
        s.update(0,0,0,false); assertTrue(s.isCreatureAlerted());
        run(s,4,0); TacticalAttackResult history=s.getLastResolution(); PrototypeBiteState phase=s.getBiteState();
        for(int i=0;i<4;i++) assertNull(s.update(0,0,0,false));
        assertEquals(SceneMode.EXPLORATION,s.getMode()); assertSame(history,s.getLastResolution()); assertSame(phase,s.getBiteState());
        rejected(s,6,0,TacticalAttackResult.RejectionReason.NOT_TACTICAL);
        assertEquals(TacticalEntryResult.Outcome.SUCCESS,s.update(-1,0,2,false));
        assertSame(phase,s.getBiteState()); assertSame(history,s.getLastResolution()); assertEquals(3,s.getHunterHealth());
    }
}
