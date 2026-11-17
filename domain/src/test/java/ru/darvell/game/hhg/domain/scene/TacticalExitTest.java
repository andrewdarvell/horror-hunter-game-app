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
        WorldGeometry geometry = new WorldGeometry(Arrays.asList(
            new StaticAabb(4, 1.2, .55, 1.8), new StaticAabb(5.45, 1.2, 1.55, 1.8)));
        TacticalGrid grid = new TacticalGrid(0, 0, 1);
        WorldSceneState scene = new WorldSceneState(new HunterState(new WorldPosition(2.5, .5), 5, .4),
            new CreaturePatrolState(Arrays.asList(new WorldPosition(.5, .5), new WorldPosition(.5, 1.5)),
                2, .3, .5, 3, geometry), grid, geometry);
        assertEquals(TacticalEntryResult.Outcome.SUCCESS, scene.update(0, 0, 0, false));
        scene.tryMoveHunterTo(new CellCoordinates(3, 0));
        scene.tryMoveHunterTo(new CellCoordinates(4, 0));
        assertEquals(TacticalExitResult.SUCCESS, scene.tryExitTactical());
        assertNull(scene.update(1, 0, .10, false));
        assertNull(scene.update(0, 1, .16, false));
        assertEquals(TacticalEntryResult.Outcome.NO_VALID_PLACEMENT, scene.update(0, 0, .28, false));
        assertNull(scene.update(0, -1, .16, false));
        assertEquals(SceneMode.EXPLORATION, scene.getMode());
        assertEquals(TacticalEntryResult.Outcome.SUCCESS, new TacticalEntryPlanner().plan(
            scene.getHunterPosition(), .4, scene.getCreaturePosition(), .3, grid, geometry).getOutcome());
        // Охотник выходит за радиус своим ходом. Следующий ход поиска снова создаёт контакт.
        assertEquals(TacticalEntryResult.Outcome.SUCCESS, scene.update(1, 0, 2, false));
        assertTrue(scene.getHunterBeforeEntry().getX() > 5.5);
        assertEquals(SceneMode.TACTICAL, scene.getMode());
        assertTrue(scene.isCreatureAlerted());
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
