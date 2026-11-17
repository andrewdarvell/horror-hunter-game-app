package ru.darvell.game.hhg;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Rectangle;
import org.junit.jupiter.api.Test;
import ru.darvell.game.hhg.domain.scene.*;
import ru.darvell.game.hhg.domain.tactical.*;
import static org.junit.jupiter.api.Assertions.*;

class TacticalBreakoutPointerTest extends TacticalPointerTest {
    void button(Rectangle b) {
        assertNotNull(b); assertTrue(b.width>=48 && b.height>=48);
        handler.touchDown((int)b.x+10,height-1-(int)b.y-10,0,Input.Buttons.LEFT);
    }
    @Test void chooseReleaseConfirmBusyAndExplorationWaitsForPresentation() {
        WorldSceneState scene=scene(); scene.enterTactical(); FirstScreen s=screen(scene); s.show(); ready(s);
        button(s.breakoutButtonBounds()); assertTrue(s.isBreakoutSelecting());
        down(-3,0); assertEquals(0,scene.getTacticalTimeUnits()); // same hold cannot select
        up(); down(-3,0); up();
        assertTrue(s.breakoutPreviewText().contains("3 ед."));
        assertTrue(s.breakoutPreviewText().contains("выход"));
        assertEquals(0,scene.getTacticalTimeUnits());
        button(s.breakoutConfirmBounds());
        assertEquals(SceneMode.EXPLORATION,scene.getMode()); assertTrue(s.isBusy());
        Object position=scene.getHunterPosition();
        tick(s,100); assertSame(position,scene.getHunterPosition());
        tick(s,.24); assertSame(position,scene.getHunterPosition()); assertFalse(s.isBusy());
        assertFalse(s.isBreakoutSelecting()); assertEquals(3,scene.getTacticalTimeUnits());
        s.hide();
    }
    @Test void cancelInvalidDistanceResizeAndSecondaryPointerDoNotExecute() {
        WorldSceneState scene=scene(); scene.enterTactical(); FirstScreen s=screen(scene); s.show(); ready(s);
        button(s.breakoutButtonBounds()); up(); down(1,0); up();
        assertTrue(s.breakoutPreviewText().contains("Невозможно"));
        button(s.breakoutConfirmBounds()); up(); assertEquals(0,scene.getTacticalTimeUnits());
        button(s.breakoutCancelBounds()); up(); assertFalse(s.isBreakoutSelecting());
        button(s.breakoutButtonBounds()); up(); down(-2,1); up();
        s.resize(0,0); assertFalse(s.isBreakoutSelecting()); assertEquals(0,scene.getTacticalTimeUnits());
        s.hide();
    }
}
