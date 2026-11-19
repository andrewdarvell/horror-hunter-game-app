package ru.darvell.game.hhg;

import com.badlogic.gdx.Input;
import org.junit.jupiter.api.Test;
import ru.darvell.game.hhg.domain.scene.*;
import static org.junit.jupiter.api.Assertions.*;

class TacticalExitPointerTest extends TacticalPointerTest {
    void mode(FirstScreen screen) {
        com.badlogic.gdx.math.Rectangle b=screen.modeButtonBounds();
        handler.touchDown((int)b.x+10,height-1-(int)b.y-10,0,Input.Buttons.LEFT);
    }
    @Test void touchToggleHoldSimultaneousKeyboardAndBusy() {
        WorldSceneState scene=scene();FirstScreen screen=screen(scene);screen.show();ready(screen);
        mode(screen); assertEquals(SceneMode.TACTICAL,scene.getMode());
        screen.updateScene(0,0,0,true); // same cycle T must not toggle back
        screen.updateScene(0,0,.22,false);assertFalse(screen.isBusy());
        mode(screen);assertEquals(SceneMode.TACTICAL,scene.getMode()); // held
        up();mode(screen);assertEquals(SceneMode.EXPLORATION,scene.getMode());
        screen.updateScene(1,0,10,true);assertEquals(SceneMode.EXPLORATION,scene.getMode());
        assertEquals(0,scene.getTacticalTimeUnits());
        up();mode(screen);assertEquals(SceneMode.TACTICAL,scene.getMode());
        screen.hide();
    }
    @Test void keyboardExitAndBounds() {
        WorldSceneState scene=scene();scene.enterTactical();FirstScreen screen=screen(scene);screen.show();ready(screen);
        screen.updateScene(0,0,0,true);assertEquals(SceneMode.EXPLORATION,scene.getMode());
        for(int[] size:new int[][]{{640,480},{480,800},{1001,601}}) {
            bounds(size[0],size[1]);assertNotNull(screen.modeButtonBounds());
            assertTrue(screen.modeButtonBounds().height>=48);
        }
        bounds(30,30);assertNull(screen.modeButtonBounds());screen.hide();
    }
    @Test void blockedTouchDoesNotSelectMoveOrSpendTime() {
        WorldSceneState scene=new TacticalAttackPointerTest().combat();
        FirstScreen screen=screen(scene);screen.show();ready(screen);
        Object placement=scene.getPlacement(), bite=scene.getBiteState();
        mode(screen);assertEquals(SceneMode.TACTICAL,scene.getMode());
        assertSame(placement,scene.getPlacement());assertEquals(bite,scene.getBiteState());
        assertFalse(screen.hasSelectedTarget());assertEquals(0,scene.getTacticalTimeUnits());
        screen.hide();
    }

    @Test void lowerStripOutsideButtonRoutesToMapAfterViewportResize() {
        WorldSceneState scene=scene();
        FirstScreen screen=screen(scene);
        screen.show();ready(screen);
        // The base Graphics fixture reports a 2x HiDPI back buffer.
        for (int[] size : new int[][]{{640,480},{480,800},{1001,601},{1200,400},{400,1200}}) {
            bounds(size[0],size[1]);
            com.badlogic.gdx.math.Rectangle b=screen.modeButtonBounds();
            assertEquals(viewport.getScreenX(),b.x);
            assertEquals(viewport.getScreenY(),b.y);
            assertTrue(b.width>=48 && b.width<viewport.getScreenWidth());
            assertEquals(48,b.height);
            int y=height-1-(int)b.y-10;
            // The first pixel at the right edge is outside (half-open bounds).
            int x=(int)(b.x+b.width);
            handler.touchDown(x,y,0,Input.Buttons.LEFT);up();
            assertEquals(SceneMode.EXPLORATION,scene.getMode());
            scene.enterTactical();
            down(5,0);up();assertTrue(screen.hasSelectedTarget());
            handler.touchDown(x,y,0,Input.Buttons.LEFT);up();
            assertEquals(SceneMode.TACTICAL,scene.getMode());
            assertFalse(screen.hasSelectedTarget(),"Outside-button click must reach map handling");
            assertEquals(0,scene.getTacticalTimeUnits());
            assertEquals(ru.darvell.game.hhg.domain.tactical.TacticalExitResult.SUCCESS,scene.tryExitTactical());
            // Letterbox fields must also never activate the control.
            if (viewport.getScreenX()>0) {
                handler.touchDown(viewport.getScreenX()-1,y,0,Input.Buttons.LEFT);up();
                assertEquals(SceneMode.EXPLORATION,scene.getMode());
            }
            if (viewport.getScreenY()>0) {
                handler.touchDown((int)b.x+10,height-viewport.getScreenY(),0,Input.Buttons.LEFT);up();
                assertEquals(SceneMode.EXPLORATION,scene.getMode());
            }
        }
        bounds(100,75);assertEquals(100,screen.modeButtonBounds().width);
        bounds(47,47);assertNull(screen.modeButtonBounds());
        screen.hide();
    }

}
