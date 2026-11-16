package ru.darvell.game.hhg;

import com.badlogic.gdx.Input;
import org.junit.jupiter.api.Test;
import ru.darvell.game.hhg.domain.scene.WorldSceneState;
import ru.darvell.game.hhg.domain.actor.*;
import ru.darvell.game.hhg.domain.world.*;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class TacticalAttackPointerTest extends TacticalPointerTest {
    WorldSceneState combat() {
        WorldSceneState s=new WorldSceneState(new HunterState(new WorldPosition(.5,.5),1,.1),
            new CreaturePatrolState(Arrays.asList(new WorldPosition(1.5,.5),new WorldPosition(1.5,1.5)),1,.1,.5,.01,WorldGeometry.empty()),grid,WorldGeometry.empty());
        s.enterTactical(); return s;
    }
    void button() { handler.touchDown(viewport.getScreenX()+10,height-viewport.getScreenY()-viewport.getScreenHeight()+10,0,Input.Buttons.LEFT); }
    @Test void selectionReleaseButtonBusyAndDefeat() {
        WorldSceneState scene=combat(); FirstScreen s=screen(scene); s.show(); ready(s);
        down(1,0); assertTrue(s.hasSelectedTarget()); assertEquals(0,scene.getTacticalTimeUnits());
        button(); assertEquals(0,scene.getTacticalTimeUnits());
        up(); button(); assertEquals(2,scene.getTacticalTimeUnits()); assertTrue(s.isBusy());
        up(); down(0,1); up(); button();
        tick(s,100); assertTrue(s.isBusy()); tick(s,Double.NaN); assertTrue(s.isBusy());
        tick(s,.24); assertFalse(s.isBusy()); button(); assertEquals(2,scene.getTacticalTimeUnits());
        up(); down(1,0); up(); button(); assertEquals(0,scene.getCreatureHealth());
        tick(s,100); tick(s,.24); up(); down(1,0);
        assertEquals(5,scene.getTacticalTimeUnits()); assertFalse(s.hasSelectedTarget());
        s.hide();
    }
    @Test void contextFreeCellPauseAndSecondaryPointer() {
        WorldSceneState scene=combat(); FirstScreen s=screen(scene); s.show(); ready(s);
        down(1,0); up(); assertTrue(s.hasSelectedTarget());
        handler.touchDown(10,10,1,Input.Buttons.LEFT); assertEquals(0,scene.getTacticalTimeUnits());
        s.pause(); assertFalse(s.hasSelectedTarget());
        down(1,0); up(); down(0,1); assertFalse(s.hasSelectedTarget()); assertEquals(1,scene.getTacticalTimeUnits());
        s.hide();
    }
}
