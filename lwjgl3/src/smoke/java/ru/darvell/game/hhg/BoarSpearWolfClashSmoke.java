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
import java.util.Arrays;
import java.util.stream.Collectors;
import ru.darvell.game.hhg.domain.actor.CreatureBehaviorConfig;
import ru.darvell.game.hhg.domain.actor.CreaturePatrolState;
import ru.darvell.game.hhg.domain.actor.HunterState;
import ru.darvell.game.hhg.domain.scene.WorldSceneState;
import ru.darvell.game.hhg.domain.tactical.Facing;
import ru.darvell.game.hhg.domain.tactical.SpearClashConfig;
import ru.darvell.game.hhg.domain.tactical.SpearClashState;
import ru.darvell.game.hhg.domain.tactical.SpearClashState.Result;
import ru.darvell.game.hhg.domain.tactical.TacticalAttackResult;
import ru.darvell.game.hhg.domain.tactical.TacticalGrid;
import ru.darvell.game.hhg.domain.world.WorldGeometry;
import ru.darvell.game.hhg.domain.world.WorldPosition;

public final class BoarSpearWolfClashSmoke extends ApplicationAdapter {
    private static final long[] SEEDS = {3, 1, 0, 3};
    private static final Result[] CONTACTS = {Result.REPELLED, Result.GRAZED_PAST, Result.BROKE_THROUGH, Result.HIT};
    private static final long[] DECISIONS = {6, 8, 10, 6};
    private static final int[][] LANDINGS = {{3, 0}, {-1, 1}, {1, 0}, {0, 1}};

    private final boolean manual;
    private FirstScreen screen;
    private WorldSceneState scene;
    private int scenario;
    private int stage;
    private int frames;
    private static boolean failed;

    private BoarSpearWolfClashSmoke(int scenario, boolean manual) {
        this.scenario = scenario;
        this.manual = manual;
    }

    @Override public void create() { reset(); }

    private void reset() {
        if (screen != null) screen.dispose();
        WorldGeometry geometry = WorldGeometry.empty();
        TacticalGrid grid = new TacticalGrid(-.5, -.5, 1);
        WorldPosition start = scenario == 3 ? new WorldPosition(0, 3) : new WorldPosition(3, 0);
        HunterState hunter = new HunterState(new WorldPosition(0, 0), 1, .4);
        CreaturePatrolState wolf = new CreaturePatrolState(Arrays.asList(start, start.translate(1, 0)),
            new CreatureBehaviorConfig(1, 1, 1, 1, 1, 2, 1.2, 10), .4, geometry);
        scene = new WorldSceneState(hunter, wolf, grid, geometry, new SpearClashConfig(Facing.E, SEEDS[scenario]));
        screen = new FirstScreen(scene, grid, geometry, new FitViewport(20, 15, new OrthographicCamera()), new ShapeRenderer());
        screen.show();
        screen.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        screen.updateScene(0, 0, 0, true);
        stage = 0;
    }

    @Override public void render() {
        try {
            screen.render(stage == 3 && (scenario % 2 == 1) ? .09f : 1f / 60);
            if (manual) {
                if (Gdx.input.isKeyJustPressed(Input.Keys.R)) reset();
                return;
            }
            check(++frames < 1200, "Timed out");
            if (screen.isBusy()) return;
            switch (stage++) {
                case 0:
                    clickSpear();
                    check(scene.getTacticalTimeQuanta() == 0 && screen.spearPreviewText().contains("2 кв."),
                        "Brace preview used time");
                    break;
                case 1:
                    clickSpear();
                    check(scene.getTacticalTimeQuanta() == 2, "Brace time");
                    break;
                case 2:
                    check(CombatHud.phase(scene).contains("Волк в прыжке: 2"), "Missing flight phase");
                    clickSpear();
                    check(scene.getSpearClashState().getRandomCursor() == 0, "Preview consumed RNG");
                    break;
                case 3:
                    capture("paused-" + scenario);
                    check(scene.getTacticalTimeQuanta() == 2, "Rendering changed frozen time");
                    check(scene.getSpearClashState().getRandomCursor() == 0, "Rendering consumed RNG");
                    clickSpear();
                    check(screen.isBusy(), "Input gate did not close");
                    break;
                default:
                    verifyOutcome();
                    capture("result-" + scenario);
                    if (++scenario == SEEDS.length) {
                        System.out.println("BOAR_SPEAR_WOLF_CLASH_SMOKE_OK all outcomes/side/preview/resize");
                        Gdx.app.exit();
                    } else {
                        Gdx.graphics.setWindowedMode(scenario == 1 ? 480 : 800, scenario == 1 ? 800 : 600);
                        reset();
                    }
            }
        } catch (Throwable failure) {
            failed = true;
            failure.printStackTrace();
            Gdx.app.exit();
        }
    }

    private void verifyOutcome() {
        TacticalAttackResult result = scene.getLastResolution();
        SpearClashState clash = scene.getSpearClashState();
        check(result.getClash().getContact() == CONTACTS[scenario], "Contact");
        check(scene.getTacticalTimeQuanta() == DECISIONS[scenario], "Decision time");
        check(scene.getCreaturePosition().equals(new WorldPosition(LANDINGS[scenario][0], LANDINGS[scenario][1])), "Landing");
        check(clash.getRandomCursor() == (scenario == 3 ? 0 : 1), "Published cursor");
        check(scene.getBiteState() == null, "Bite was armed");
        System.out.println("SPEAR " + CONTACTS[scenario] + " q=" + scene.getTacticalTimeQuanta()
            + " HP=" + scene.getHunterHealth() + "/" + scene.getCreatureHealth()
            + " cells=" + scene.getHunterPosition() + "/" + scene.getCreaturePosition()
            + " phase=" + clash.getWolfPhase() + "/" + clash.getWolfRemaining()
            + " mode=" + scene.getMode() + " cursor=" + clash.getRandomCursor()
            + " events=" + result.getEvents().stream()
                .map(event -> (2 + 2 * event.getOffset()) + ":" + event.getType()).collect(Collectors.joining(",")));
    }

    private void clickSpear() {
        Rectangle bounds = screen.spearButtonBounds(0);
        check(bounds != null, "Spear control unavailable");
        int x = (int) (bounds.x + bounds.width / 2);
        int y = Gdx.graphics.getHeight() - 1 - (int) (bounds.y + bounds.height / 2);
        InputProcessor input = Gdx.input.getInputProcessor();
        input.touchDown(x, y, 0, Input.Buttons.LEFT);
        input.touchUp(x, y, 0, Input.Buttons.LEFT);
    }

    private void capture(String name) {
        int width = Gdx.graphics.getBackBufferWidth();
        int height = Gdx.graphics.getBackBufferHeight();
        Pixmap image = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        try {
            image.getPixels().put(ScreenUtils.getFrameBufferPixels(0, 0, width, height, true)).flip();
            PixmapIO.writePNG(Gdx.files.local("../lwjgl3/build/boar-spear-" + name + ".png"), image);
        } finally {
            image.dispose();
        }
    }

    private void check(boolean condition, String requirement) {
        if (!condition) throw new AssertionError(CONTACTS[scenario] + ", stage " + stage + ": " + requirement);
    }

    @Override public void resize(int width, int height) { if (screen != null) screen.resize(width, height); }
    @Override public void dispose() { if (screen != null) screen.dispose(); }

    public static void main(String[] args) {
        int scenario = 0;
        boolean manual = args.length == 1;
        if (manual) {
            switch (args[0]) {
                case "3": break;
                case "1": scenario = 1; break;
                case "0": scenario = 2; break;
                case "side": scenario = 3; break;
                default: throw new IllegalArgumentException("Use seed 3, 1, 0, or side");
            }
        } else if (args.length != 0) {
            throw new IllegalArgumentException("Use one fixture argument");
        }
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("Horror Hunter · Рогатина и волк");
        configuration.setWindowedMode(800, 600);
        configuration.disableAudio(true);
        new Lwjgl3Application(new BoarSpearWolfClashSmoke(scenario, manual), configuration);
        if (failed) throw new AssertionError("Spear clash smoke failed");
    }
}
