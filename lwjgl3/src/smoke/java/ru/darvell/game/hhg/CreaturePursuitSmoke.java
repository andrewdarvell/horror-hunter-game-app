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
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import ru.darvell.game.hhg.domain.actor.CreaturePatrolState;
import ru.darvell.game.hhg.domain.actor.CreaturePatrolState.Behavior;
import ru.darvell.game.hhg.domain.actor.HunterState;
import ru.darvell.game.hhg.domain.scene.SceneMode;
import ru.darvell.game.hhg.domain.scene.WorldSceneState;
import ru.darvell.game.hhg.domain.tactical.CellCoordinates;
import ru.darvell.game.hhg.domain.tactical.TacticalAttackResult;
import ru.darvell.game.hhg.domain.tactical.TacticalEntryResult;
import ru.darvell.game.hhg.domain.tactical.TacticalGrid;
import ru.darvell.game.hhg.domain.world.StaticAabb;
import ru.darvell.game.hhg.domain.world.WorldGeometry;
import ru.darvell.game.hhg.domain.world.WorldPosition;

import java.util.Arrays;
import java.util.Collections;

/** Автоматический smoke с GLFW, отрисовкой HUD и синтетическим pointer input. */
public final class CreaturePursuitSmoke extends ApplicationAdapter {
    private static boolean failed;
    private boolean ran;
    private FirstScreen screen;
    private WorldSceneState scene;

    @Override
    public void render() {
        if (ran) return;
        ran = true;
        try {
            breakoutAndRecontact();
            escapeAndReturn();
            sightAndBlockedReturn();
            victoryAndPointer();
            measureLongUpdate();
            System.out.println("CREATURE_PURSUIT_SMOKE_OK escape/recontact/LOS/blocked-return/victory/resize/pointer");
        } catch (Throwable failure) {
            failed = true;
            failure.printStackTrace();
        } finally {
            Gdx.app.exit();
        }
    }

    private void breakoutAndRecontact() {
        escaped();
        screen.updateScene(0, 0, .33, false);
        check("Ищет".equals(screen.creatureBehaviorText()), "SEARCH HUD");
        screen.render(0);
        capture("search");
        check(screen.updateScene(0, 0, .01, false) == TacticalEntryResult.Outcome.SUCCESS, "recontact step 34");
        check(scene.getMode() == SceneMode.TACTICAL, "recontact mode");
        check(Math.abs(screen.getVisualCreaturePosition().getX() - 1.52) < 1e-10, "pre-snap search position");
        screen.render(0);
    }

    private void escapeAndReturn() {
        escaped();
        screen.updateScene(1, 0, 2, false);
        check(scene.getMode() == SceneMode.EXPLORATION && scene.getCreatureBehavior() == Behavior.RETURN, "escape");
        screen.render(0);
        screen.updateScene(0, 0, 1, false);
        check(scene.getCreatureBehavior() == Behavior.PATROL, "return to patrol");
        check(screen.visibleLastSeen() == null, "clear patrol indicator");
        screen.render(0);
    }

    private void sightAndBlockedReturn() {
        WorldGeometry geometry = new WorldGeometry(Collections.singletonList(new StaticAabb(1, -2, 1, 8)));
        CreaturePatrolState actor = patrol(p(0, 0), p(0, 4), geometry);
        check(actor.observeHunter(p(0, 1)), "initial sight");
        reset(p(3, 0), actor, geometry);
        screen.updateScene(0, 0, .67, false);
        check(scene.getCreatureBehavior() == Behavior.SEARCH, "LOS loss");
        check(p(0, 1).equals(screen.visibleLastSeen()), "last seen behind cover");
        screen.render(0);
        reset(p(8, 0), patrol(p(0, 0), p(0, 4), geometry).withTacticalPosition(p(3, 2)), geometry);
        screen.updateScene(0, 0, .5, false);
        check("Путь к патрулю перекрыт".equals(screen.creatureBehaviorText()), "blocked RETURN HUD");
        check(p(3, 2).equals(scene.getCreaturePosition()), "blocked RETURN position");
        screen.render(0);
        capture("blocked-return");
    }

    private void victoryAndPointer() {
        WorldGeometry geometry = WorldGeometry.empty();
        reset(p(1.5, .5), patrol(p(.5, .5), p(.5, 1.5), geometry), geometry);
        scene.enterTactical();
        scene.tryAttackCreature(new CellCoordinates(0, 0));
        scene.tryAttackCreature(new CellCoordinates(0, 0));
        check(scene.getMode() == SceneMode.EXPLORATION && scene.getCreatureHealth() == 0, "victory");
        check(screen.creatureBehaviorText().isEmpty() && screen.visibleLastSeen() == null, "dead HUD");
        Gdx.graphics.setWindowedMode(640, 480);
        screen.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        screen.render(0);
        Rectangle toggle = screen.modeButtonBounds();
        check(toggle != null, "mode button after resize");
        InputProcessor pointer = Gdx.input.getInputProcessor();
        int x = (int) toggle.x + 10;
        int y = Gdx.graphics.getHeight() - 1 - (int) toggle.y - 10;
        pointer.touchDown(x, y, 0, Input.Buttons.LEFT);
        pointer.touchUp(x, y, 0, Input.Buttons.LEFT);
        check(scene.getMode() == SceneMode.TACTICAL && !scene.getPlacement().creatureCell().isPresent(),
            "hunter-only pointer entry");
    }

    private void measureLongUpdate() {
        if (screen != null) screen.dispose();
        screen = new FirstScreen();
        screen.show();
        screen.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        long start = System.nanoTime();
        check(screen.updateScene(0, 0, 1.27, false) == null, "demo long update");
        System.out.println("CREATURE_PURSUIT_LONG_ACTIVE_UPDATE_NS " + (System.nanoTime() - start));
        screen.render(0);
    }

    private void capture(String scenario) {
        int width = Gdx.graphics.getBackBufferWidth();
        int height = Gdx.graphics.getBackBufferHeight();
        Pixmap image = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        try {
            image.getPixels().put(ScreenUtils.getFrameBufferPixels(0, 0, width, height, true)).flip();
            PixmapIO.writePNG(Gdx.files.local("../lwjgl3/build/pursuit-" + scenario + ".png"), image);
        } finally {
            image.dispose();
        }
    }

    private void escaped() {
        WorldGeometry geometry = WorldGeometry.empty();
        reset(p(2.5, .5), patrol(p(.5, .5), p(.5, 1.5), geometry), geometry);
        check(scene.update(0, 0, 0, false) == TacticalEntryResult.Outcome.SUCCESS, "entry");
        check(scene.tryBreakoutTo(new CellCoordinates(4, 0)).getOutcome() == TacticalAttackResult.Outcome.ESCAPED,
            "breakout");
    }

    private void reset(WorldPosition hunter, CreaturePatrolState actor, WorldGeometry geometry) {
        if (screen != null) screen.dispose();
        TacticalGrid grid = new TacticalGrid(0, 0, 1);
        scene = new WorldSceneState(new HunterState(hunter, 5, .4), actor, grid, geometry);
        screen = new FirstScreen(scene, grid, geometry, new FitViewport(20, 15, new OrthographicCamera()),
            new ShapeRenderer());
        screen.show();
        screen.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    private static CreaturePatrolState patrol(WorldPosition from, WorldPosition to, WorldGeometry geometry) {
        return new CreaturePatrolState(Arrays.asList(from, to), 2, .3, .5, 3, geometry);
    }

    private static WorldPosition p(double x, double y) { return new WorldPosition(x, y); }

    private static void check(boolean condition, String scenario) {
        if (!condition) throw new AssertionError(scenario);
    }

    @Override
    public void dispose() {
        if (screen != null) screen.dispose();
    }

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setWindowedMode(800, 600);
        config.disableAudio(true);
        config.setTitle("Creature pursuit smoke");
        new Lwjgl3Application(new CreaturePursuitSmoke(), config);
        if (failed) throw new AssertionError("Creature pursuit smoke failed");
    }
}
