package ru.darvell.game.hhg.domain.tactical;

import java.util.*;
import org.junit.jupiter.api.Test;
import ru.darvell.game.hhg.domain.world.*;
import static org.junit.jupiter.api.Assertions.*;

class HunterOnlyEntryTest {
    @Test void explicitAbsenceValidationAndStableSelection() {
        TacticalEntryPlanner p=new TacticalEntryPlanner();TacticalGrid grid=new TacticalGrid(0,0,1);
        TacticalEntryResult r=p.planHunterOnly(new WorldPosition(.2,.2),.1,grid,WorldGeometry.empty());
        assertEquals(TacticalEntryResult.Outcome.SUCCESS,r.getOutcome());
        assertEquals(new CellCoordinates(0,0),r.getPlacement().getHunterCell());
        assertFalse(r.getPlacement().creatureCell().isPresent());assertFalse(r.creatureCenter().isPresent());
        assertEquals(TacticalEntryResult.Outcome.INVALID_INPUT,p.planHunterOnly(null,.1,grid,WorldGeometry.empty()).getOutcome());
        assertEquals(TacticalEntryResult.Outcome.INVALID_INPUT,p.planHunterOnly(new WorldPosition(Double.MAX_VALUE,0),.1,grid,WorldGeometry.empty()).getOutcome());
        List<TacticalEntryPlanner.Candidate> candidates=Arrays.asList(
            new TacticalEntryPlanner.Candidate(new CellCoordinates(1,0),new WorldPosition(1.5,.5),1,true),
            new TacticalEntryPlanner.Candidate(new CellCoordinates(-1,0),new WorldPosition(-.5,.5),1,true));
        assertEquals(new CellCoordinates(-1,0),TacticalEntryPlanner.selectHunter(candidates).getPlacement().getHunterCell());
        Collections.reverse(candidates);
        assertEquals(new CellCoordinates(-1,0),TacticalEntryPlanner.selectHunter(candidates).getPlacement().getHunterCell());
        assertEquals(TacticalEntryResult.Outcome.NO_VALID_PLACEMENT,TacticalEntryPlanner.selectHunter(Collections.emptyList()).getOutcome());
    }
}
