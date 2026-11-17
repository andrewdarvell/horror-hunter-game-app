package ru.darvell.game.hhg;

import com.badlogic.gdx.Input;
import org.junit.jupiter.api.Test;
import ru.darvell.game.hhg.domain.scene.WorldSceneState;
import ru.darvell.game.hhg.domain.scene.SceneMode;
import ru.darvell.game.hhg.domain.tactical.TacticalAttackResult;
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
        up(); down(1,0); up(); button();
        assertEquals(0, scene.getCreatureHealth());
        assertEquals(SceneMode.EXPLORATION, scene.getMode());
        assertTrue(s.isShowingVictory());
        WorldPosition position = scene.getHunterPosition();
        TacticalAttackResult victory = scene.getLastResolution();

        up();
        int modeX = viewport.getScreenX() + 10;
        int modeY = height - 1 - viewport.getScreenY() - 10;
        handler.touchDown(modeX, modeY, 0, Input.Buttons.LEFT);
        s.updateScene(1, 0, 10, true); // первый кадр и одновременный T
        assertTrue(s.isShowingVictory());
        s.updateScene(1, 0, .24, true); // конечный кадр тоже поглощает ввод
        assertFalse(s.isShowingVictory());
        handler.touchDown(modeX, modeY, 0, Input.Buttons.LEFT); // всё то же удержание
        assertEquals(SceneMode.EXPLORATION, scene.getMode());
        assertEquals(position, scene.getHunterPosition());
        assertEquals(4, scene.getTacticalTimeUnits());
        assertSame(victory, scene.getLastResolution());
        up(); down(1,0); up();
        assertEquals(4, scene.getTacticalTimeUnits());
        s.updateScene(-1, 0, .1, false);
        assertEquals(position.translate(-.1, 0), scene.getHunterPosition());
        assertSame(victory, scene.getLastResolution());

        handler.touchDown(modeX, modeY, 0, Input.Buttons.LEFT);
        s.updateScene(0, 0, 0, true); // touch + T не переключают режим дважды
        up(); tick(s, .22);
        assertEquals(SceneMode.TACTICAL, scene.getMode());
        assertFalse(s.isShowingVictory());
        assertSame(victory, scene.getLastResolution());
        assertFalse(scene.getPlacement().creatureCell().isPresent());
        down(1,0); up();
        assertEquals(5, scene.getTacticalTimeUnits());
        assertFalse(s.hasSelectedTarget());
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
