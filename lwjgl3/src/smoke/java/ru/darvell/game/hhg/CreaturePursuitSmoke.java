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
import ru.darvell.game.hhg.domain.actor.CreatureBehaviorConfig;
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
            demoShowsChaseBeforeEntryAndLetsHunterRun();
            System.out.println("CREATURE_PURSUIT_SMOKE_OK demo-chase/escape/recontact/LOS/blocked-return/victory/resize/pointer");
        } catch (Throwable failure) {
            failed = true;
            failure.printStackTrace();
        } finally {
            Gdx.app.exit();
        }
    }

    private void breakoutAndRecontact() {
        WorldGeometry geometry = WorldGeometry.empty();
        reset(p(1.5, .5), patrol(p(.5, .5), p(.5, 1.5), geometry), geometry);
        check(scene.update(0, 0, 0, false) == TacticalEntryResult.Outcome.SUCCESS, "initial engagement");
        TacticalAttackResult breakout = scene.tryBreakoutTo(new CellCoordinates(3, 0));
        check(breakout.getOutcome() == TacticalAttackResult.Outcome.ESCAPED && scene.getHunterHealth() == 3,
            "visible wounded breakout");
        check(screen.updateScene(0, 0, .37, false) == null, "no early recontact");
        check("Преследует".equals(screen.creatureBehaviorText()), "CHASE HUD");
        screen.render(0);
        check(screen.updateScene(0, 0, .01, false) == TacticalEntryResult.Outcome.SUCCESS, "recontact step 38");
        check(scene.getMode() == SceneMode.TACTICAL, "recontact mode");
        check(scene.getLastResolution() == breakout && scene.getTacticalTimeUnits() == 2
            && scene.getBiteState().getRemainingUnits() == 2, "combat history after recontact");
        check(Math.abs(screen.getVisualCreaturePosition().getX() - 2.02) < 1e-10, "pre-snap chase position");
        screen.render(0);
        capture("reentry-start");
        screen.updateScene(0, 0, .11, false);
        Gdx.graphics.setWindowedMode(480, 800);
        screen.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        screen.render(0);
        check(screen.isBusy(), "resize preserves active entry");
        screen.updateScene(0, 0, .11, false);
        check(!screen.isBusy() && screen.getVisualCreaturePosition().equals(scene.getCreaturePosition()), "entry endpoint");
        screen.render(0);
        capture("reentry-end");
        Gdx.graphics.setWindowedMode(800, 600);
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

    private void demoShowsChaseBeforeEntryAndLetsHunterRun() {
        resetDemo();
        driveDemoToChase();
        screen.render(0);
        capture("demo-chase");
        check(screen.updateScene(0, 1, 1, false) == null, "run north without entry");
        check("Ищет".equals(screen.creatureBehaviorText()), "SEARCH after losing the runner");
        screen.render(0);

        resetDemo();
        driveDemoToChase();
        check(screen.updateScene(0, 0, 1, false) == TacticalEntryResult.Outcome.SUCCESS,
            "stationary hunter engages on the demo map");
        screen.render(0);
        screen.updateScene(0, 0, .22, false);
        check(!screen.isBusy(), "demo entry presentation completed");
        screen.render(0);
    }

    private void resetDemo() {
        if (screen != null) screen.dispose();
        screen = new FirstScreen();
        screen.show();
        screen.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    private void driveDemoToChase() {
        check(screen.updateScene(0, 1, .76, false) == null, "north of the demo wall");
        check(screen.updateScene(1, 0, 1, false) == null, "east past the demo wall");
        for (int step = 0; step < 200 && !"Преследует".equals(screen.creatureBehaviorText()); step++) {
            check(screen.updateScene(0, 0, .01, false) == null, "wait for detection without tactical snap");
        }
        check("Преследует".equals(screen.creatureBehaviorText()), "demo CHASE HUD");
        WorldPosition before = screen.getVisualCreaturePosition();
        check(screen.updateScene(0, 0, .01, false) == null, "creature moves before the demo engagement");
        check(!before.equals(screen.getVisualCreaturePosition()), "demo chase movement");
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
        check(scene.update(0, 0, 0, false) == null, "visible outside engagement");
        check(scene.enterTactical() == TacticalEntryResult.Outcome.SUCCESS, "manual entry");
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
        return new CreaturePatrolState(Arrays.asList(from, to), new CreatureBehaviorConfig(2, 4, 3, 2, 2, 3, 1.5, .5), .3, geometry);
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
