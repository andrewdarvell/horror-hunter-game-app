package ru.darvell.game.hhg;

import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.utils.ScreenUtils;
import ru.darvell.game.hhg.domain.scene.*;
import ru.darvell.game.hhg.domain.world.WorldPosition;
import java.lang.reflect.*;
import java.util.*;

public class TransitionSmoke extends ApplicationAdapter {
    FirstScreen screen;
    Input scriptedInput;
    WorldSceneState scene;
    OrthographicCamera camera;
    Set<Integer> held = new HashSet<>(), pressed = new HashSet<>();
    int frame;
    WorldPosition startHunter, startCreature, finalHunter, finalCreature, seen;
    Object placement;
    boolean failed;
    boolean minimized, restored;
    int restoreWait;

    public static void main(String[] args) {
        TransitionSmoke app = new TransitionSmoke();
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("HorrorHunter transition QA");
        config.setWindowedMode(640, 480);
        config.setForegroundFPS(30);
        config.disableAudio(true);
        config.setWindowListener(new Lwjgl3WindowAdapter() {
            @Override public void iconified(boolean isIconified) {
                if (isIconified) app.minimized = true;
                else if (app.minimized) app.restored = true;
            }
        });
        new Lwjgl3Application(app, config);
        if (app.failed) throw new AssertionError("Desktop QA failed");
    }

    static Object field(Object target, String name) throws Exception {
        Field f = FirstScreen.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    void createScreen() throws Exception {
        screen = new FirstScreen();
        scene = (WorldSceneState) field(screen, "scene");
        camera = (OrthographicCamera) field(screen, "camera");
        screen.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    @Override public void create() {
        try {
            Input real = Gdx.input;
            scriptedInput = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[] {Input.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("isKeyPressed")) return held.contains((Integer) args[0]);
                    if (method.getName().equals("isKeyJustPressed")) return pressed.contains((Integer) args[0]);
                    return method.invoke(real, args);
                });
            Gdx.input = scriptedInput;
            createScreen();
        } catch (Exception e) { fail(e); }
    }

    @Override public void resize(int width, int height) {
        if (screen != null) screen.resize(width, height);
    }

    void draw(float delta, int justPressed, int... heldKeys) {
        Gdx.input = scriptedInput;
        pressed.clear(); held.clear();
        if (justPressed != 0) pressed.add(justPressed);
        for (int k : heldKeys) held.add(k);
        screen.render(delta);
        System.out.println("frame=" + frame + " dt=" + delta + " hunter=" + scene.getHunterPosition() + " creature=" + scene.getCreaturePosition() + " mode=" + scene.getMode() + " alert=" + scene.isCreatureAlerted());
        checkCamera();
    }

    void checkCamera() {
        WorldPosition p = screen.getVisualHunterPosition();
        require(camera.position.x == (float) p.getX() && camera.position.y == (float) p.getY(), "camera differs from visual hunter");
    }

    void require(boolean ok, String message) {
        if (!ok) throw new AssertionError("frame " + frame + ": " + message);
    }

    void capture(String name) {
        Pixmap pixmap = ScreenUtils.getFrameBufferPixmap(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        PixmapIO.writePNG(Gdx.files.absolute("/tmp/hhg-qa/" + name + ".png"), pixmap, -1, true);
        pixmap.dispose();
    }

    void saveFinalState() {
        require(scene.getMode() == SceneMode.TACTICAL, "no successful tactical entry");
        finalHunter = scene.getHunterPosition(); finalCreature = scene.getCreaturePosition();
        placement = scene.getPlacement(); seen = scene.getLastSeenHunterPosition();
        require(!scene.getPlacement().getHunterCell().equals(scene.getPlacement().getCreatureCell()), "shared cell");
        require(screen.getVisualHunterPosition().equals(startHunter), "first hunter frame jumped");
        require(screen.getVisualCreaturePosition().equals(startCreature), "first creature frame jumped");
    }

    void checkFrozen() {
        require(finalHunter.equals(scene.getHunterPosition()), "domain hunter moved");
        require(finalCreature.equals(scene.getCreaturePosition()), "domain creature moved");
        require(placement == scene.getPlacement(), "placement changed");
        require(Objects.equals(seen, scene.getLastSeenHunterPosition()), "last-seen changed");
    }

    @Override public void render() {
        if (failed) return;
        try {
            switch (frame++) {
                case 0:
                    draw(0, Input.Keys.G); capture("manual-before");
                    startHunter = scene.getHunterPosition(); startCreature = scene.getCreaturePosition();
                    break;
                case 1:
                    draw(10, Input.Keys.T); saveFinalState(); capture("manual-start");
                    require(!scene.isCreatureAlerted() && seen == null, "manual invented detection");
                    break;
                case 2:
                    draw(.055f, Input.Keys.G, Input.Keys.W, Input.Keys.T); checkFrozen();
                    capture("manual-quarter"); break;
                case 3:
                    screen.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight()); checkCamera();
                    draw(.055f, Input.Keys.T, Input.Keys.D); checkFrozen(); capture("manual-middle"); break;
                case 4:
                    Gdx.graphics.setWindowedMode(800, 600);
                    draw(.11f, 0); checkFrozen();
                    // float frame intervals may need the next sub-frame to reach the exact duration.
                    break;
                case 5:
                    draw(.001f, 0); checkFrozen();
                    require(screen.getVisualHunterPosition().equals(finalHunter), "manual end hunter");
                    require(screen.getVisualCreaturePosition().equals(finalCreature), "manual end creature");
                    capture("manual-end"); break;
                case 6:
                    screen.resize(0, 0); draw(1, Input.Keys.T, Input.Keys.W);
                    require(screen.getVisualHunterPosition().equals(finalHunter), "manual post-end jump");
                    checkFrozen(); capture("manual-after");
                    break;
                case 7:
                    checkFrozen();
                    screen.dispose(); createScreen(); draw(0, Input.Keys.G); break;
                case 8:
                    draw(.8f, 0, Input.Keys.S);
                    require(scene.getMode() == SceneMode.EXPLORATION, "unexpected early entry"); break;
                case 9:
                    draw(1, 0, Input.Keys.D);
                    require(scene.getMode() == SceneMode.EXPLORATION && !scene.isCreatureAlerted(), "blocked LOS detected hunter");
                    startHunter = scene.getHunterPosition(); startCreature = scene.getCreaturePosition(); capture("auto-before"); break;
                case 10:
                    draw(.45f, 0, Input.Keys.W); saveFinalState();
                    require(scene.isCreatureAlerted(), "no automatic detection"); capture("auto-start"); break;
                case 11:
                    draw(.11f, Input.Keys.T, Input.Keys.A); checkFrozen(); capture("auto-middle"); break;
                case 12:
                    Gdx.graphics.setWindowedMode(640, 480);
                    draw(1, Input.Keys.G); checkFrozen(); capture("auto-end"); break;
                case 13:
                    draw(1, Input.Keys.T); checkFrozen(); capture("auto-after");
                    require(screen.getVisualHunterPosition().equals(finalHunter), "auto post-end jump");
                    System.out.println("DESKTOP QA PASS: real render, manual T, automatic detection, G, WASD freeze, repeat T, camera, resize, exact endpoints, last-seen.");
                    Gdx.app.exit(); break;
            }
        } catch (Throwable error) { fail(error); }
    }

    void fail(Throwable error) { failed = true; error.printStackTrace(); Gdx.app.exit(); }
    @Override public void dispose() { if (screen != null) screen.dispose(); }
}
