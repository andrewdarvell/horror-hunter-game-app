package ru.darvell.game.hhg;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.viewport.FitViewport;
import org.junit.jupiter.api.*;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import ru.darvell.game.hhg.domain.actor.*;
import ru.darvell.game.hhg.domain.scene.*;
import ru.darvell.game.hhg.domain.tactical.*;
import ru.darvell.game.hhg.domain.world.*;
import static org.junit.jupiter.api.Assertions.*;

class TacticalPointerTest {
    Graphics oldGraphics; Input oldInput; InputProcessor handler;
    int width = 640, height = 480;
    final TacticalGrid grid = new TacticalGrid(0, 0, 1);
    FitViewport viewport;
    @BeforeEach void setup() {
        GdxNativesLoader.load(); oldGraphics = Gdx.graphics; oldInput = Gdx.input;
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(), new Class<?>[] {Graphics.class},
            (p, m, a) -> {
                switch (m.getName()) {
                    case "getWidth": return width; case "getHeight": return height;
                    case "getBackBufferWidth": return width * 2; case "getBackBufferHeight": return height * 2;
                    default: return defaultValue(m.getReturnType());
                }
            });
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[] {Input.class},
            (p, m, a) -> {
                if (m.getName().equals("getInputProcessor")) return handler;
                if (m.getName().equals("setInputProcessor")) { handler = (InputProcessor) a[0]; return null; }
                return defaultValue(m.getReturnType());
            });
        viewport = new FitViewport(20, 15, new OrthographicCamera());
        bounds(640, 480);
    }
    static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false; if (type == int.class) return 0;
        if (type == float.class) return 0f; if (type == long.class) return 0L; return null;
    }
    @AfterEach void restore() { Gdx.graphics = oldGraphics; Gdx.input = oldInput; }
    void bounds(int w, int h) {
        width = w; height = h;
        float scale = Math.min(w / 20f, h / 15f);
        int vw = Math.round(20 * scale), vh = Math.round(15 * scale);
        viewport.setWorldSize(20, 15); viewport.setScreenBounds((w-vw)/2, (h-vh)/2, vw, vh);
        viewport.getCamera().viewportWidth = 20; viewport.getCamera().viewportHeight = 15;
        viewport.getCamera().position.set(.5f, .5f, 0); viewport.getCamera().update();
    }
    CellCoordinates pick(int x, int y) { return TacticalCellPicker.pick(viewport, grid, x, y, width, height, true); }
    @Test void actualUnprojectBoundsOddRoundingCameraAndHidpi() {
        for (int[] wh : new int[][] {{640,480}, {1000,600}, {800,800}, {1001,601}}) {
            bounds(wh[0], wh[1]);
            int l = viewport.getScreenX(), t = height-viewport.getScreenY()-viewport.getScreenHeight();
            int r = l+viewport.getScreenWidth(), b = t+viewport.getScreenHeight();
            assertNotNull(pick(l,t)); assertNotNull(pick(r-1,t));
            assertNotNull(pick(l,b-1)); assertNotNull(pick(r-1,b-1));
            assertNull(pick(l-1,t)); assertNull(pick(l,t-1)); assertNull(pick(r,t)); assertNull(pick(l,b));
            assertEquals(new CellCoordinates(0,0), pick(width/2,height/2));
            viewport.getCamera().position.set(-4.5f, -3.5f, 0); viewport.getCamera().update();
            assertEquals(new CellCoordinates(-5,-4),pick(width/2,height/2));
        }
        bounds(640,480); viewport.getCamera().position.set(0,0,0); viewport.getCamera().update();
        assertEquals(new CellCoordinates(0,0),pick(320,240));
        assertEquals(new CellCoordinates(-1,0),pick(319,240));
        assertEquals(new CellCoordinates(0,-1),pick(320,241));
        assertNull(TacticalCellPicker.pick(viewport,grid,320,240,width,height,false));
        assertNull(TacticalCellPicker.pick(viewport,grid,0,0,0,0,true));
        viewport.setScreenSize(0,0); assertNull(pick(0,0));
    }
    @Test void invalidWorldAndCellOverflowIgnored() {
        viewport.getCamera().position.set(Float.MAX_VALUE,0,0); viewport.getCamera().update();
        assertNull(pick(320,240));
        viewport.getCamera().invProjectionView.val[0] = Float.NaN;
        assertNull(pick(320,240));
    }
    FirstScreen screen(WorldSceneState scene) {
        return new FirstScreen(scene, grid, WorldGeometry.empty(), viewport, null);
    }
    WorldSceneState scene() {
        return new WorldSceneState(new HunterState(new WorldPosition(.5,.5),1,.4),
            new CreaturePatrolState(Arrays.asList(new WorldPosition(5.5,.5),new WorldPosition(5.5,1.5)),
                2,.3,.5,3,WorldGeometry.empty()),grid,WorldGeometry.empty());
    }
    int[] target(int x, int y) {
        Vector3 v = viewport.project(new Vector3(x+.5f,y+.5f,0));
        return new int[] {Math.round(v.x),height-Math.round(v.y)};
    }
    void down(int x,int y) { int[] p=target(x,y); handler.touchDown(p[0],p[1],0,Input.Buttons.LEFT); }
    void up() { handler.touchUp(0,0,0,Input.Buttons.LEFT); }
    void tick(FirstScreen s,double dt) { s.updateScene(1,1,dt,true); }
    void ready(FirstScreen s) {
        // Численная установка bounds заменяет только OpenGL apply в resize.
        try { java.lang.reflect.Field f=FirstScreen.class.getDeclaredField("pointerReady"); f.setAccessible(true); f.set(s,true); }
        catch(Exception e) { throw new AssertionError(e); }
    }
    @Test void pointerRouteBusyHoldCompletionAndNoReplay() {
        WorldSceneState scene=scene(); FirstScreen s=screen(scene); s.show(); ready(s);
        down(1,0); up(); assertEquals(SceneMode.EXPLORATION,scene.getMode());
        tick(s,0); down(1,0); tick(s,1); down(1,0);
        assertEquals(new CellCoordinates(0,0),scene.getPlacement().getHunterCell());
        up(); down(1,0); assertEquals(new CellCoordinates(1,0),scene.getPlacement().getHunterCell());
        Object placement=scene.getPlacement(); WorldPosition creature=scene.getCreaturePosition();
        WorldPosition seen=scene.getLastSeenHunterPosition();
        up(); down(2,0); up(); down(2,0); // второй пакет до первого render уже busy
        tick(s,100); assertEquals(new WorldPosition(.5,.5),s.getVisualHunterPosition());
        tick(s,.09); assertEquals(new WorldPosition(1,.5),s.getVisualHunterPosition());
        tick(s,.09); assertSame(scene.getHunterPosition(),s.getVisualHunterPosition());
        down(2,0); handler.touchDragged(0,0,0); tick(s,100);
        assertSame(placement,scene.getPlacement()); // busy-down удерживается после окончания
        up(); down(2,0); assertEquals(new CellCoordinates(2,0),scene.getPlacement().getHunterCell());
        tick(s,1); tick(s,1); assertSame(creature,scene.getCreaturePosition()); assertSame(seen,scene.getLastSeenHunterPosition());
        Object after=scene.getPlacement(); tick(s,100); assertSame(after,scene.getPlacement());
        s.hide(); assertNull(handler);
    }
    @Test void fieldsSecondaryButtonsLifecycleAndResizeReadiness() {
        WorldSceneState scene=scene(); scene.enterTactical(); FirstScreen s=screen(scene);
        InputProcessor previous=new InputAdapter(); handler=previous; s.show(); ready(s);
        InputProcessor own=handler; s.show(); assertSame(own,handler);
        Object before=scene.getPlacement();
        int[] p=target(1,0);
        handler.touchDown(p[0],p[1],1,Input.Buttons.LEFT);
        handler.touchDown(p[0],p[1],0,Input.Buttons.RIGHT);
        handler.touchUp(p[0],p[1],1,Input.Buttons.LEFT);
        assertSame(before,scene.getPlacement());
        bounds(1000,600); handler.touchDown(0,300,0,Input.Buttons.LEFT); up();
        assertSame(before,scene.getPlacement());
        s.resize(0,600); down(1,0); up(); assertSame(before,scene.getPlacement());
        ready(s); down(1,0); assertNotSame(before,scene.getPlacement());
        s.hide(); assertSame(previous,handler);
        s.show(); InputProcessor other=new InputAdapter(); handler=other; s.dispose(); assertSame(other,handler);
        own.touchDown(p[0],p[1],0,Input.Buttons.LEFT); // скрытый экран не реагирует
    }
}
