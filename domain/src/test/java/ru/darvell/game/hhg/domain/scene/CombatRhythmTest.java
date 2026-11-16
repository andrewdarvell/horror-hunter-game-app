package ru.darvell.game.hhg.domain.scene;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import ru.darvell.game.hhg.domain.actor.*;
import ru.darvell.game.hhg.domain.tactical.*;
import ru.darvell.game.hhg.domain.world.*;
import static org.junit.jupiter.api.Assertions.*;
import static ru.darvell.game.hhg.domain.tactical.TacticalAttackResult.EventType.*;
import static ru.darvell.game.hhg.domain.tactical.PrototypeBiteState.Phase.*;

class CombatRhythmTest {
    static CellCoordinates c(int x, int y) { return new CellCoordinates(x,y); }
    static WorldSceneState scene(int x, int damage) {
        WorldGeometry g=WorldGeometry.empty();
        WorldSceneState s=new WorldSceneState(new HunterState(new WorldPosition(x+.5,.5),1,.4).withDamage(damage),
            new CreaturePatrolState(Arrays.asList(new WorldPosition(.5,.5),new WorldPosition(.5,1.5)),1,.4,.5,3,g),
            new TacticalGrid(0,0,1),g);
        assertEquals(TacticalEntryResult.Outcome.SUCCESS,s.enterTactical()); return s;
    }
    static void move(WorldSceneState s,int x,int y) { assertEquals(TacticalMoveResult.SUCCESS,s.tryMoveHunterTo(c(x,y))); }
    static void phase(WorldSceneState s,PrototypeBiteState.Phase p,int n) {
        assertEquals(p,s.getBiteState().getPhase()); assertEquals(n,s.getBiteState().getRemainingUnits());
    }
    static TacticalAttackResult attack(WorldSceneState s) {
        TacticalAttackResult preview=s.previewAttackCreature(c(0,0));
        TacticalAttackResult r=s.tryAttackCreature(c(0,0));
        assertEquals(preview.getHunterAfter(),r.getHunterAfter());
        assertEquals(preview.getCreatureAfter(),r.getCreatureAfter());
        assertEquals(preview.getOutcome(),r.getOutcome());
        assertEquals(events(preview),events(r)); return r;
    }
    static Object events(TacticalAttackResult r) {
        return r.getEvents().stream().map(e->e.getOffset()+":"+e.getType()).collect(Collectors.toList());
    }
    static Object[] snapshot(WorldSceneState s) {
        return new Object[]{s.getPlacement(),s.getHunterPosition(),s.getCreaturePosition(),s.getBiteState(),
            s.getHunterHealth(),s.getCreatureHealth(),s.getTacticalTimeUnits(),s.getLastResolution(),
            s.isCreatureAlerted(),s.getLastSeenHunterPosition()};
    }
    @Test void WBothEntryDistances() {
        for(int start:new int[]{1,2}) {
            WorldSceneState s=scene(start,0);
            if(start==2) { phase(s,READY,0); move(s,1,0); }
            phase(s,PREPARING,1);
            move(s,2,0); phase(s,RECOVERING,3);
            assertEquals(BITE_MISS,s.getLastResolution().getEvents().get(0).getType());
            move(s,1,0); phase(s,RECOVERING,2);
            attack(s); phase(s,PREPARING,1); assertEquals(1,s.getCreatureHealth());
            move(s,2,0); move(s,1,0); TacticalAttackResult last=attack(s);
            assertEquals(4,s.getHunterHealth()); assertEquals(0,s.getCreatureHealth());
            assertEquals(start==2?9:8,s.getTacticalTimeUnits()); assertNull(s.getBiteState());
            assertFalse(s.getPlacement().creatureCell().isPresent());
            assertEquals(Arrays.asList("2:HIT"),events(last));
        }
    }
    static WorldSceneState mistakes() {
        WorldSceneState s=scene(1,0);
        for(int i=1;i<=12;i++) {
            move(s,1,i%2); assertEquals(4-(i+3)/4,s.getHunterHealth());
        }
        phase(s,PREPARING,1); return s;
    }
    @Test void DAndSafeCounterfactualFromSameReachableState() {
        WorldSceneState s=mistakes(); TacticalAttackResult r=attack(s);
        assertEquals(TacticalAttackResult.Outcome.INTERRUPTED,r.getOutcome());
        assertEquals(1,r.getDuration()); assertEquals(13,s.getTacticalTimeUnits());
        assertEquals(0,s.getHunterHealth()); assertEquals(2,s.getCreatureHealth());
        assertEquals(Arrays.asList("1:BITE","1:RECOVERY_STARTED"),events(r));
        Object[] dead=snapshot(s); s.update(1,1,100,true); s.tryAttackCreature(c(0,0)); s.tryMoveHunterTo(c(2,0));
        assertArrayEquals(dead,snapshot(s));
        s=mistakes(); move(s,2,0); move(s,1,0); attack(s); move(s,2,0); move(s,1,0); attack(s);
        assertEquals(20,s.getTacticalTimeUnits()); assertEquals(1,s.getHunterHealth()); assertEquals(0,s.getCreatureHealth());
    }
    @Test void CTwoAttacksAndCrossingRecovery() {
        WorldSceneState s=scene(1,0); attack(s); phase(s,RECOVERING,2); attack(s);
        assertEquals(3,s.getHunterHealth()); assertEquals(0,s.getCreatureHealth()); assertEquals(4,s.getTacticalTimeUnits());
        s=scene(1,0); move(s,1,1); move(s,1,0); move(s,1,1); phase(s,RECOVERING,1);
        TacticalAttackResult r=attack(s);
        assertEquals(Arrays.asList("1:RECOVERY_ENDED","1:PREPARATION_STARTED","2:HIT","2:BITE","2:RECOVERY_STARTED"),events(r));
        assertEquals(2,s.getHunterHealth()); assertEquals(1,s.getCreatureHealth());
        // Достижимое R1 с целью 1 HP: первый удар, затем соседний шаг.
        s=scene(1,0); attack(s); move(s,1,1); phase(s,RECOVERING,1);
        r=attack(s); assertEquals(Arrays.asList("1:RECOVERY_ENDED","1:PREPARATION_STARTED","2:HIT"),events(r));
    }
    @Test void HitRemainsCompletedWhenSameBoundaryBiteKillsHunter() {
        WorldSceneState s=scene(1,2); move(s,1,1); move(s,1,0); move(s,1,1);
        TacticalAttackResult r=attack(s); assertEquals(TacticalAttackResult.Outcome.HIT,r.getOutcome());
        assertEquals(0,s.getHunterHealth()); assertEquals(1,s.getCreatureHealth()); assertEquals(2,r.getDuration());
    }
    @Test void RefusalsFramesPreviewAndOverflowAreAtomic() throws Exception {
        WorldSceneState s=scene(1,0); Object[] before=snapshot(s);
        for(int i=0;i<5;i++) { s.previewAttackCreature(c(0,0)); s.attackAvailability(c(0,0)); s.update(1,1,100,true); }
        s.tryAttackCreature(null); s.tryMoveHunterTo(c(0,0)); s.tryMoveHunterTo(c(1,0));
        assertArrayEquals(before,snapshot(s));
        Field time=WorldSceneState.class.getDeclaredField("tacticalTimeUnits"); time.setAccessible(true); time.setLong(s,Long.MAX_VALUE);
        before=snapshot(s);
        assertThrows(ArithmeticException.class,()->s.tryAttackCreature(c(0,0)));
        assertThrows(ArithmeticException.class,()->s.tryMoveHunterTo(c(1,1)));
        assertArrayEquals(before,snapshot(s));
    }
    @Test void SnapshotsCannotBeMutatedAndRecoveryDoesNotAccumulateBites() {
        WorldSceneState s=scene(1,0); PrototypeBiteState initial=s.getBiteState();
        move(s,2,0); TacticalAttackResult old=s.getLastResolution();
        move(s,3,0); move(s,4,0); move(s,5,0); phase(s,READY,0);
        assertEquals(PREPARING,initial.getPhase()); assertEquals(RECOVERING,old.getBiteAfter().getPhase());
        assertThrows(UnsupportedOperationException.class,()->old.getEvents().clear());
        assertThrows(IllegalArgumentException.class,()->new PrototypeBiteState(RECOVERING,0));
        assertThrows(IllegalArgumentException.class,()->new PrototypeBiteState(PREPARING,2));
    }
    @Test void LosLossMissesWithoutCancellingRecoveryAndAutoEntryUsesSameCycle() {
        WorldGeometry g=new WorldGeometry(Arrays.asList(new StaticAabb(.95,.95,.1,.1)));
        WorldSceneState s=new WorldSceneState(new HunterState(new WorldPosition(1.5,.5),1,.1),
            new CreaturePatrolState(Arrays.asList(new WorldPosition(.5,.5),new WorldPosition(.5,1.5)),1,.1,.5,3,g),
            new TacticalGrid(0,0,1),g);
        assertEquals(TacticalEntryResult.Outcome.SUCCESS,s.update(0,0,0,false));
        phase(s,PREPARING,1); assertTrue(s.isCreatureAlerted());
        WorldPosition seen=s.getLastSeenHunterPosition();
        move(s,1,1); phase(s,RECOVERING,3); assertEquals(4,s.getHunterHealth());
        assertEquals(BITE_MISS,s.getLastResolution().getEvents().get(0).getType());
        move(s,1,0); phase(s,RECOVERING,2); assertSame(seen,s.getLastSeenHunterPosition());
    }

}
