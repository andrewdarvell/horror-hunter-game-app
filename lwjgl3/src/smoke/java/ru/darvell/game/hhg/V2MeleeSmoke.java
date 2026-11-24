package ru.darvell.game.hhg;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import java.util.Arrays;
import ru.darvell.game.hhg.domain.actor.*;
import ru.darvell.game.hhg.domain.scene.WorldSceneState;
import ru.darvell.game.hhg.domain.tactical.*;
import ru.darvell.game.hhg.domain.world.*;

/** Полная схватка через UI ручного режима: журнал, repeated bites, input gate и resize. */
public final class V2MeleeSmoke extends ApplicationAdapter {
    private FirstScreen screen;
    private WorldSceneState scene;
    private FitViewport viewport;
    private final StringBuilder transcript = new StringBuilder();
    private int blocks, frames;
    private boolean held, biteCaptured;
    private static boolean failed;

    @Override public void create() {
        TacticalGrid grid = new TacticalGrid(-.5, -.5, 1);
        WorldGeometry geometry = WorldGeometry.empty();
        WorldPosition wolf = new WorldPosition(1, 0);
        scene = new WorldSceneState(new HunterState(new WorldPosition(0, 0), 1, .4, 6),
            new CreaturePatrolState(Arrays.asList(wolf, wolf.translate(1, 0)),
                new CreatureBehaviorConfig(1, 1, 1, 1, 1, 2, 1.2, 10), .4, geometry, 4),
            grid, geometry, SpearClashConfig.v2(Facing.E, 0));
        viewport = new FitViewport(20, 15, new OrthographicCamera());
        screen = new FirstScreen(scene, grid, geometry, viewport, new ShapeRenderer());
        screen.setCombatDebugSink(result -> {
            check(result == scene.getLastResolution(), "Unpublished result logged");
            String block = CombatDebugLog.format("V2", 0, result);
            System.out.print(block);
            transcript.append(block);
            blocks++;
        });
        screen.show();
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        screen.updateScene(0, 0, 0, true);
    }

    @Override public void render() {
        try {
            check(++frames < 2000, "Timed out");
            int before = blocks;
            screen.render(frames % 2 == 0 ? .037f : 1f / 60);
            check(blocks == before, "Render logged a result");
            if (screen.isBusy()) {
                if (blocks == 1 && !held) {
                    Gdx.input.getInputProcessor().touchDown(buttonX(), buttonY(), 0, Input.Buttons.LEFT);
                    held = true;
                }
                if (blocks == 1 && !biteCaptured && screen.visualHealth(true) == 5) {
                    capture("bite");
                    biteCaptured = true;
                    Gdx.graphics.setWindowedMode(480, 800);
                }
                return;
            }
            if (held) {
                Gdx.input.getInputProcessor().touchDown(buttonX(), buttonY(), 0, Input.Buttons.LEFT);
                check(blocks == before, "Held input replayed after gate");
                Gdx.input.getInputProcessor().touchUp(buttonX(), buttonY(), 0, Input.Buttons.LEFT);
                held = false;
            }
            if (blocks < 3) {
                long[] times = {0, 6, 12};
                check(scene.getTacticalTimeQuanta() == times[blocks], "Early control return");
                for (int i = 0; i < 3; i++) scene.previewAttackCreature(new CellCoordinates(1, 0));
                check(blocks == before, "Preview logged a result");
                WorldPosition wolf = scene.getCreaturePosition();
                Vector3 point = viewport.project(new Vector3((float) wolf.getX(), (float) wolf.getY(), 0));
                click(Math.round(point.x), Gdx.graphics.getHeight() - 1 - Math.round(point.y));
                check(screen.hasSelectedTarget(), "Target unavailable after recovery");
                click(buttonX(), buttonY());
                check(blocks == before + 1, "Missing or duplicate command log");
                check(screen.isBusy(), "Input gate did not close");
                check(scene.getLastResolution().getOutcome() == TacticalAttackResult.Outcome.HIT, "Strike cancelled");
                return;
            }
            check(biteCaptured, "Missing nonlethal bite playback");
            check(scene.getTacticalTimeQuanta() == 16, "Terminal time");
            check(scene.getHunterHealth() == 0 && scene.getCreatureHealth() == 1, "Terminal HP");
            click(buttonX(), buttonY());
            check(blocks == 3, "Dead hunter accepted input");
            capture("defeat-portrait");
            Gdx.files.local("../lwjgl3/build/v2-melee-combat.log").writeString(transcript.toString(), false, "UTF-8");
            System.out.println("V2_MELEE_SMOKE_OK commands=3 q=16 HP=0/1 preview/render/input-gate/resize");
            Gdx.app.exit();
        } catch (Throwable failure) {
            failed = true;
            failure.printStackTrace();
            Gdx.app.exit();
        }
    }

    private int buttonX() { return viewport.getScreenX() + 10; }
    private int buttonY() { return Gdx.graphics.getHeight() - viewport.getScreenY() - viewport.getScreenHeight() + 10; }
    private void click(int x, int y) {
        InputProcessor input = Gdx.input.getInputProcessor();
        input.touchDown(x, y, 0, Input.Buttons.LEFT);
        input.touchUp(x, y, 0, Input.Buttons.LEFT);
    }
    private void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private void capture(String name) {
        Pixmap image = new Pixmap(Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight(), Pixmap.Format.RGBA8888);
        try {
            image.getPixels().put(ScreenUtils.getFrameBufferPixels(0, 0, image.getWidth(), image.getHeight(), true)).flip();
            PixmapIO.writePNG(Gdx.files.local("../lwjgl3/build/v2-melee-" + name + ".png"), image);
        } finally { image.dispose(); }
    }
    @Override public void resize(int width, int height) { if (screen != null) screen.resize(width, height); }
    @Override public void dispose() { if (screen != null) screen.dispose(); }
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Horror Hunter · v2 melee smoke");
        config.setWindowedMode(800, 600);
        config.disableAudio(true);
        new Lwjgl3Application(new V2MeleeSmoke(), config);
        if (failed) throw new AssertionError("V2 melee smoke failed");
    }
}
