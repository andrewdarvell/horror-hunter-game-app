package ru.darvell.game.hhg.domain.scene;

import org.junit.jupiter.api.Test;
import java.util.*;
import ru.darvell.game.hhg.domain.actor.*;
import ru.darvell.game.hhg.domain.tactical.*;
import ru.darvell.game.hhg.domain.world.*;
import static org.junit.jupiter.api.Assertions.*;

class TacticalExitTest {
    WorldSceneState scene(int x, int y, double detection, WorldGeometry g, int creatureDamage) {
        return new WorldSceneState(new HunterState(new WorldPosition(x+.5,y+.5),1,.1),
            new CreaturePatrolState(Arrays.asList(new WorldPosition(.5,.5),new WorldPosition(.5,1.5)),1,.1,.5,detection,g)
                .withDamage(creatureDamage), new TacticalGrid(0,0,1),g);
    }
    @Test void threatMatrixAndPureRefusal() {
        for (int x : new int[]{1,2,3,4}) {
            WorldSceneState s=scene(x,0,3,WorldGeometry.empty(),0);
            assertEquals(TacticalExitResult.NOT_TACTICAL,s.tryExitTactical());
            s.enterTactical(); Object placement=s.getPlacement(), bite=s.getBiteState();
            TacticalExitResult expected=x<=3?TacticalExitResult.ACTIVE_THREAT:TacticalExitResult.SUCCESS;
            assertEquals(expected,s.exitTacticalAvailability());
            assertSame(placement,s.getPlacement()); assertSame(bite,s.getBiteState());
            assertEquals(expected,s.tryExitTactical());
            assertEquals(0,s.getTacticalTimeUnits()); assertEquals(4,s.getHunterHealth());
            assertSame(bite,s.getBiteState());
            if(x<=3) assertSame(placement,s.getPlacement());
            else { assertNull(s.getPlacement()); assertNull(s.update(0,0,0,false)); }
        }
        WorldSceneState diagonal=scene(3,3,3,WorldGeometry.empty(),0); diagonal.enterTactical();
        assertEquals(TacticalExitResult.SUCCESS,diagonal.tryExitTactical());
        WorldSceneState small=scene(1,0,.01,WorldGeometry.empty(),0);small.enterTactical();
        assertEquals(TacticalExitResult.ACTIVE_THREAT,small.tryExitTactical());
        small.tryMoveHunterTo(new CellCoordinates(1,1)); // recovery does not permit exit
        assertEquals(TacticalExitResult.ACTIVE_THREAT,small.tryExitTactical());
        WorldGeometry wall=new WorldGeometry(Arrays.asList(new StaticAabb(.95,0,.1,1)));
        WorldSceneState covered=scene(1,0,3,wall,0);covered.enterTactical();
        assertEquals(TacticalExitResult.SUCCESS,covered.tryExitTactical());
    }
    @Test void cycleTimeAndHistorySurviveExitAndReentry() {
        WorldSceneState s=scene(1,0,3,WorldGeometry.empty(),0);s.enterTactical();
        for(int x=2;x<=4;x++) s.tryMoveHunterTo(new CellCoordinates(x,0));
        Object bite=s.getBiteState(), history=s.getLastResolution(); long time=s.getTacticalTimeUnits();
        WorldPosition position=s.getHunterPosition();
        assertEquals(TacticalExitResult.SUCCESS,s.tryExitTactical());
        assertSame(position,s.getHunterPosition());s.update(0,0,10,false);
        assertSame(bite,s.getBiteState()); assertSame(history,s.getLastResolution());
        s.enterTactical();assertSame(bite,s.getBiteState());assertEquals(time,s.getTacticalTimeUnits());
        s.tryMoveHunterTo(new CellCoordinates(3,0)); assertEquals(time+1,s.getTacticalTimeUnits());
    }
    @Test void victoryHunterOnlyAndDeadActorStayDead() {
        WorldSceneState s=scene(1,0,3,WorldGeometry.empty(),0);s.enterTactical();
        s.tryAttackCreature(new CellCoordinates(0,0));s.tryAttackCreature(new CellCoordinates(0,0));
        assertEquals(0,s.getCreatureHealth()); Object history=s.getLastResolution();
        WorldPosition body=s.getCreaturePosition();long time=s.getTacticalTimeUnits();
        assertEquals(SceneMode.EXPLORATION,s.getMode());
        assertEquals(TacticalExitResult.NOT_TACTICAL,s.tryExitTactical());s.update(0,0,100,false);
        assertEquals(TacticalEntryResult.Outcome.SUCCESS,s.enterTactical());
        assertFalse(s.getPlacement().creatureCell().isPresent());assertSame(body,s.getCreaturePosition());
        assertSame(history,s.getLastResolution()); assertEquals(time,s.getTacticalTimeUnits());assertNull(s.getBiteState());
        s.tryMoveHunterTo(new CellCoordinates(0,0));assertEquals(time+1,s.getTacticalTimeUnits());
        assertEquals(TacticalAttackResult.Outcome.TARGET_DEFEATED,s.tryAttackCreature(new CellCoordinates(0,0)).getOutcome());
    }
    @Test void contactFailureDoesNotRetryUntilRealSeparation() {
        List<StaticAabb> boxes=new ArrayList<>();
        for(int x=-1;x<=1;x++) for(int y=-1;y<=1;y++) boxes.add(new StaticAabb(x+.45,y+.45,.1,.1));
        WorldGeometry g=new WorldGeometry(boxes);
        WorldSceneState s=new WorldSceneState(new HunterState(new WorldPosition(8,0),1,.4),
            new CreaturePatrolState(Arrays.asList(new WorldPosition(3,0),new WorldPosition(3,1)),1,.3,.5,4,g),
            new TacticalGrid(0,0,1),g);
        s.enterTactical();assertEquals(TacticalExitResult.SUCCESS,s.tryExitTactical());
        s.update(0,-1,.5,false);
        assertEquals(TacticalEntryResult.Outcome.NO_VALID_PLACEMENT,s.update(-1,0,8.5,false));
        assertNull(s.update(0,0,0,false));assertNull(s.update(1,0,2,false)); // valid placement now, same contact
        assertEquals(SceneMode.EXPLORATION,s.getMode());
        s.update(1,0,7,false); // outside radius
        assertEquals(TacticalEntryResult.Outcome.SUCCESS,s.update(-1,0,3,false));
        assertTrue(s.isCreatureAlerted());
    }
    @Test void defeatedHunterCannotExitAndAlertedRecontactWorks() {
        WorldGeometry g=WorldGeometry.empty();
        WorldSceneState dead=new WorldSceneState(new HunterState(new WorldPosition(4.5,.5),1,.1).withDamage(4),
            new CreaturePatrolState(Arrays.asList(new WorldPosition(.5,.5),new WorldPosition(.5,1.5)),1,.1,.5,3,g),new TacticalGrid(0,0,1),g);
        dead.enterTactical();assertEquals(TacticalExitResult.HUNTER_DEFEATED,dead.tryExitTactical());
        WorldSceneState s=scene(3,0,3,g,0);
        assertEquals(TacticalEntryResult.Outcome.SUCCESS,s.update(0,0,0,false));
        s.tryMoveHunterTo(new CellCoordinates(4,0));
        assertEquals(TacticalExitResult.SUCCESS,s.tryExitTactical());
        assertNull(s.update(0,0,0,false));
        assertEquals(TacticalEntryResult.Outcome.SUCCESS,s.update(-1,0,1,false));
        assertTrue(s.isCreatureAlerted());
    }

}
