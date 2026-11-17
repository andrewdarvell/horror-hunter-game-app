package ru.darvell.game.hhg.domain.scene;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import ru.darvell.game.hhg.domain.actor.*;
import ru.darvell.game.hhg.domain.tactical.*;
import ru.darvell.game.hhg.domain.world.*;
import static org.junit.jupiter.api.Assertions.*;
import static ru.darvell.game.hhg.domain.tactical.TacticalAttackResult.Outcome.*;

class TacticalAttackTest {
    WorldSceneState scene(int damage, int x, int y, WorldGeometry geometry) {
        return new WorldSceneState(new HunterState(new WorldPosition(.5,.5),1,.1).withDamage(damage),
            new CreaturePatrolState(Arrays.asList(new WorldPosition(x+.5,y+.5),new WorldPosition(x+.5,y+1.5)),
                1,.1,.5,.01,geometry),new TacticalGrid(0,0,1),geometry);
    }
    final CellCoordinates target = new CellCoordinates(1,0);
    @Test void twoAttacksResolveAutomaticallyAndReleaseCell() {
        WorldSceneState s=scene(0,1,0,WorldGeometry.empty()); s.enterTactical();
        assertFalse(s.isCreatureAlerted());
        TacticalAttackResult a=s.tryAttackCreature(target);
        assertEquals(Arrays.asList(TacticalAttackResult.Effect.BITE,TacticalAttackResult.Effect.HIT),a.getEffects());
        assertEquals(3,s.getHunterHealth()); assertEquals(1,s.getCreatureHealth()); assertEquals(2,s.getTacticalTimeUnits());
        assertThrows(UnsupportedOperationException.class,()->a.getEffects().clear());
        WorldPosition hunter = s.getHunterPosition(), creature = s.getCreaturePosition();
        TacticalAttackResult forecast = s.previewAttackCreature(target);
        assertEquals(SceneMode.EXPLORATION, forecast.getModeAfter());
        assertEquals(SceneMode.TACTICAL, s.getMode());
        assertSame(a, s.getLastResolution());
        TacticalAttackResult victory = s.tryAttackCreature(target);
        assertEquals(HIT, victory.getOutcome());
        assertEquals(2, victory.getDuration());
        assertEquals(Arrays.asList("2:HIT", "2:TACTICAL_EXITED"), CombatRhythmTest.events(victory));
        assertEquals(CombatRhythmTest.events(forecast), CombatRhythmTest.events(victory));
        assertEquals(SceneMode.EXPLORATION, s.getMode());
        assertEquals(hunter, s.getHunterPosition());
        assertEquals(creature, s.getCreaturePosition());
        assertNull(s.getPlacement());
        assertSame(victory, s.getLastResolution());
        assertEquals(3,s.getHunterHealth()); assertEquals(0,s.getCreatureHealth());
        assertEquals(4,s.getTacticalTimeUnits());
        assertEquals(NOT_TACTICAL, s.tryAttackCreature(target).getOutcome());
        assertNull(s.update(0, 0, 1, false));
        assertEquals(TacticalEntryResult.Outcome.SUCCESS, s.enterTactical());
        assertSame(victory, s.getLastResolution());
        assertNull(s.update(0, 0, 1, false));
        assertFalse(s.getPlacement().creatureCell().isPresent());
        assertThrows(IllegalStateException.class,()->s.getPlacement().getCreatureCell());
        assertEquals(TARGET_DEFEATED,s.tryAttackCreature(target).getOutcome());
        assertEquals(TacticalMoveResult.SUCCESS,s.tryMoveHunterTo(target));
        assertEquals(target,s.getPlacement().getHunterCell()); assertEquals(5,s.getTacticalTimeUnits());
        assertEquals(SceneMode.TACTICAL,s.getMode()); assertNull(s.getLastSeenHunterPosition());
    }
    @Test void lethalBiteInterruptsAndOnlyActualTimeIsPaid() {
        WorldSceneState s=scene(3,1,0,WorldGeometry.empty()); s.enterTactical();
        TacticalAttackResult a=s.tryAttackCreature(target);
        assertEquals(INTERRUPTED,a.getOutcome()); assertTrue(a.isAccepted()); assertEquals(1,a.getDuration());
        assertEquals(0,a.getCreatureDamage()); assertEquals(1,a.getHunterDamage());
        assertEquals(0,s.getHunterHealth()); assertEquals(2,s.getCreatureHealth());
        Object placement=s.getPlacement();
        assertEquals(HUNTER_DEFEATED,s.tryAttackCreature(target).getOutcome());
        assertEquals(TacticalMoveResult.HUNTER_DEFEATED,s.tryMoveHunterTo(new CellCoordinates(0,1)));
        s.update(1,1,100,true); assertSame(placement,s.getPlacement()); assertEquals(1,s.getTacticalTimeUnits());
    }
    @Test void refusalDoesNotMutateAndFramesDoNotAdvanceTime() {
        WorldSceneState s=scene(0,2,0,WorldGeometry.empty());
        assertEquals(NOT_TACTICAL,s.tryAttackCreature(target).getOutcome()); s.enterTactical();
        Object p=s.getPlacement();
        assertEquals(INVALID_TARGET,s.tryAttackCreature(null).getOutcome());
        assertEquals(INVALID_TARGET,s.tryAttackCreature(target).getOutcome());
        assertEquals(OUT_OF_RANGE,s.tryAttackCreature(new CellCoordinates(2,0)).getOutcome());
        s.update(1,1,100,true); assertSame(p,s.getPlacement()); assertEquals(0,s.getTacticalTimeUnits()); assertEquals(4,s.getHunterHealth());
    }
    @Test void movingUsesSameTimeAndChecksReactionAtDestination() {
        WorldSceneState s=scene(0,2,0,WorldGeometry.empty()); s.enterTactical();
        assertEquals(TacticalMoveResult.SUCCESS,s.tryMoveHunterTo(target));
        assertEquals(4,s.getHunterHealth()); assertEquals(1,s.getTacticalTimeUnits());
        assertEquals(TacticalMoveResult.SUCCESS,s.tryMoveHunterTo(new CellCoordinates(0,0)));
        assertEquals(4,s.getHunterHealth()); assertEquals(2,s.getTacticalTimeUnits());
        assertEquals(TacticalMoveResult.SUCCESS,s.tryMoveHunterTo(target));
        assertEquals(TacticalMoveResult.OCCUPIED,s.tryMoveHunterTo(new CellCoordinates(2,0)));
    }
    @Test void diagonalLineAndClosedCornerContact() {
        WorldSceneState s=scene(0,1,1,WorldGeometry.empty()); s.enterTactical();
        assertEquals(HIT,s.tryAttackCreature(new CellCoordinates(1,1)).getOutcome());
        WorldGeometry wall=new WorldGeometry(Arrays.asList(new StaticAabb(.9,-1,.1,2)));
        s=scene(0,1,0,wall); s.enterTactical();
        assertEquals(BLOCKED,s.tryAttackCreature(target).getOutcome()); assertEquals(0,s.getTacticalTimeUnits());
        WorldGeometry corner=new WorldGeometry(Arrays.asList(new StaticAabb(1,0,.2,1)));
        s=scene(0,1,1,corner); s.enterTactical();
        assertEquals(BLOCKED,s.tryAttackCreature(new CellCoordinates(1,1)).getOutcome());
    }
    @Test void scaledGridAndLargeRadiiStillUseCellAdjacency() {
        TacticalGrid grid = new TacticalGrid(0,0,10);
        WorldSceneState s = new WorldSceneState(new HunterState(new WorldPosition(5,5),1,2),
            new CreaturePatrolState(Arrays.asList(new WorldPosition(15,15),new WorldPosition(15,25)),1,3,.5,.01,WorldGeometry.empty()),grid,WorldGeometry.empty());
        s.enterTactical(); assertEquals(HIT,s.tryAttackCreature(new CellCoordinates(1,1)).getOutcome());
        assertEquals(INVALID_TARGET,s.tryAttackCreature(new CellCoordinates(Integer.MIN_VALUE,Integer.MAX_VALUE)).getOutcome());
    }
    @Test void zeroThicknessWeaponCanReachWhereBodyCannotMove() {
        WorldGeometry geometry = new WorldGeometry(Arrays.asList(new StaticAabb(.9,.7,.2,.1)));
        WorldSceneState s = scene(0,1,1,geometry); s.enterTactical();
        assertFalse(geometry.isCirclePathValid(s.getHunterPosition(),s.getCreaturePosition(),s.getHunterRadius()));
        assertEquals(HIT,s.tryAttackCreature(new CellCoordinates(1,1)).getOutcome());
    }
    @Test void lethalReactionToMoveKeepsDestinationAndOccupancy() {
        WorldSceneState s=scene(3,2,0,WorldGeometry.empty()); s.enterTactical();
        assertEquals(TacticalMoveResult.SUCCESS,s.tryMoveHunterTo(new CellCoordinates(1,1)));
        assertEquals(TacticalMoveResult.SUCCESS,s.tryMoveHunterTo(target));
        assertEquals(0,s.getHunterHealth()); assertEquals(target,s.getPlacement().getHunterCell());
        assertEquals(new WorldPosition(1.5,.5),s.getHunterPosition()); assertEquals(2,s.getTacticalTimeUnits());
    }
    @Test void oppositeIntegerLimitsCannotWrapIntoAdjacentCells() {
        WorldSceneState s = new WorldSceneState(new HunterState(new WorldPosition(Integer.MIN_VALUE+.5,.5),1,.1),
            new CreaturePatrolState(Arrays.asList(new WorldPosition(Integer.MAX_VALUE+.5,.5),
                new WorldPosition(Integer.MAX_VALUE+.5,1.5)),1,.1,.5,.01,WorldGeometry.empty()),
            new TacticalGrid(0,0,1),WorldGeometry.empty());
        s.enterTactical();
        assertEquals(OUT_OF_RANGE,s.tryAttackCreature(new CellCoordinates(Integer.MAX_VALUE,0)).getOutcome());
        assertEquals(0,s.getTacticalTimeUnits());
    }
    @Test void actorCopiesKeepHealthWithoutSharingChanges() {
        HunterState h=new HunterState(new WorldPosition(0,0),1,.1);
        assertEquals(4,h.getHealth()); assertEquals(2,h.withDamage(2).copy().withTacticalPosition(new WorldPosition(.5,.5)).getHealth());
        assertEquals(4,h.getHealth()); assertEquals(0,h.withDamage(Integer.MAX_VALUE).getHealth());
        assertThrows(IllegalArgumentException.class,()->h.withDamage(-1));
        CreaturePatrolState c=new CreaturePatrolState(Arrays.asList(new WorldPosition(0,0),new WorldPosition(1,0)),1,.1,.5,1,WorldGeometry.empty());
        assertEquals(1,c.withDamage(1).copy().withTacticalPosition(new WorldPosition(.5,.5)).getHealth()); assertEquals(2,c.getHealth());
    }
}
