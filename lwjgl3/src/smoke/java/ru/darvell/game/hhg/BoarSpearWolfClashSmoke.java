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
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;
import ru.darvell.game.hhg.domain.actor.CreatureBehaviorConfig;
import ru.darvell.game.hhg.domain.actor.CreaturePatrolState;
import ru.darvell.game.hhg.domain.actor.HunterState;
import ru.darvell.game.hhg.domain.scene.WorldSceneState;
import ru.darvell.game.hhg.domain.tactical.Facing;
import ru.darvell.game.hhg.domain.tactical.SpearClashConfig;
import ru.darvell.game.hhg.domain.tactical.SpearClashState;
import ru.darvell.game.hhg.domain.tactical.SpearClashState.Result;
import ru.darvell.game.hhg.domain.tactical.SpearClashState.StopReason;
import ru.darvell.game.hhg.domain.tactical.TacticalAttackResult;
import ru.darvell.game.hhg.domain.tactical.TacticalGrid;
import ru.darvell.game.hhg.domain.world.StaticAabb;
import ru.darvell.game.hhg.domain.world.WorldGeometry;
import ru.darvell.game.hhg.domain.world.WorldPosition;

public final class BoarSpearWolfClashSmoke extends ApplicationAdapter {
    private enum Scenario {
        REPEL("3", 3, 3, 0, Result.REPELLED, 6, 3, 0),
        PASS("1", 1, 3, 0, Result.GRAZED_PAST, 8, -1, 1),
        CLOSE("0", 0, 3, 0, Result.BROKE_THROUGH, 10, 1, 0),
        SIDE("side", 3, 0, 3, Result.HIT, 6, 0, 1),
        APPROACH("approach", 3, 5, 0, Result.REPELLED, 10, 3, 0),
        FLANK("flank", 3, 2, 1, null, 4, 0, 2),
        REPEAT("repeat", 0, 3, 0, Result.BROKE_THROUGH, 10, 1, 0);

        private final String argument;
        private final long seed;
        private final WorldPosition start;
        private final Result contact;
        private final long decision;
        private final WorldPosition endpoint;

        Scenario(String argument, long seed, int x, int y, Result contact, long decision, int endX, int endY) {
            this.argument = argument;
            this.seed = seed;
            this.start = new WorldPosition(x, y);
            this.contact = contact;
            this.decision = decision;
            this.endpoint = new WorldPosition(endX, endY);
        }
    }

    private final boolean manual;
    private FirstScreen screen;
    private FitViewport viewport;
    private WorldSceneState scene;
    private Scenario scenario;
    private int stage;
    private int frames;
    private static boolean failed;

    private BoarSpearWolfClashSmoke(Scenario scenario, boolean manual) {
        this.scenario = scenario;
        this.manual = manual;
    }

    @Override public void create() { reset(); }

    private void reset() {
        if (screen != null) screen.dispose();
        WorldGeometry geometry = scenario == Scenario.FLANK
            ? new WorldGeometry(Collections.singletonList(new StaticAabb(.9, -.1, .2, .2))) : WorldGeometry.empty();
        TacticalGrid grid = new TacticalGrid(-.5, -.5, 1);
        WorldPosition start = scenario.start;
        HunterState hunter = new HunterState(new WorldPosition(0, 0), 1, .4);
        CreaturePatrolState wolf = new CreaturePatrolState(Arrays.asList(start, start.translate(1, 0)),
            new CreatureBehaviorConfig(1, 1, 1, 1, 1, 2, 1.2, 10), .4, geometry);
        scene = new WorldSceneState(hunter, wolf, grid, geometry, new SpearClashConfig(Facing.E, scenario.seed));
        viewport = new FitViewport(20, 15, new OrthographicCamera());
        screen = new FirstScreen(scene, grid, geometry, viewport, new ShapeRenderer());
        screen.show();
        screen.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        screen.updateScene(0, 0, 0, true);
        stage = 0;
    }

    @Override public void render() {
        try {
            screen.render(scenario.ordinal() % 2 == 1 ? .037f : 1f / 60);
            if (manual) {
                if (Gdx.input.isKeyJustPressed(Input.Keys.R)) reset();
                return;
            }
            check(++frames < 2000, "Timed out");
            if (screen.isBusy()) return;
            switch (stage++) {
                case 0:
                    clickSpear();
                    check(scene.getTacticalTimeQuanta() == 0 && !screen.spearPreviewText().isEmpty(), "Brace preview");
                    break;
                case 1:
                    check(scene.getSpearClashState().getRandomCursor() == 0, "Preview consumed RNG");
                    capture("preview-" + scenario.name());
                    clickSpear();
                    check(screen.isBusy(), "Input gate did not close");
                    break;
                case 2:
                    verifyOutcome(scenario.contact, scenario.decision, scenario.endpoint);
                    capture("result-" + scenario.name());
                    if (scenario == Scenario.REPEAT) selectWolf();
                    else nextScenario();
                    break;
                case 3:
                    confirmAttack();
                    check(screen.isBusy(), "Second contact has no presentation");
                    break;
                default:
                    verifyOutcome(Result.HIT, 16, scenario.endpoint);
                    check(scene.getHunterHealth() == 3 && scene.getCreatureHealth() == 1, "Second exchange HP");
                    capture("result-REPEAT-SECOND");
                    nextScenario();
            }
        } catch (Throwable failure) {
            failed = true;
            failure.printStackTrace();
            Gdx.app.exit();
        }
    }

    private void nextScenario() {
        int next = scenario.ordinal() + 1;
        if (next == Scenario.values().length) {
            System.out.println("BOAR_SPEAR_WOLF_CLASH_SMOKE_OK contacts/approach/flank/repeat/resize");
            Gdx.app.exit();
            return;
        }
        scenario = Scenario.values()[next];
        boolean portrait = scenario == Scenario.PASS || scenario == Scenario.FLANK;
        Gdx.graphics.setWindowedMode(portrait ? 480 : 800, portrait ? 800 : 600);
        reset();
    }

    private void verifyOutcome(Result expected, long decision, WorldPosition endpoint) {
        TacticalAttackResult result = scene.getLastResolution();
        SpearClashState clash = scene.getSpearClashState();
        TacticalAttackResult.ClashFact contact = result.getClash().getLastContact();
        check((contact == null ? null : contact.getContact()) == expected, "Contact");
        check(scene.getTacticalTimeQuanta() == decision, "Decision time");
        check(scene.getCreaturePosition().equals(endpoint), "Landing");
        check(clash.getRandomCursor() == (scenario == Scenario.SIDE || scenario == Scenario.FLANK ? 0 : 1), "Cursor");
        check(scene.getBiteState() == null, "Bite was armed");
        if (scenario == Scenario.FLANK) {
            check(clash.isBraced() && clash.isFlankUsed(), "Flank cleared the stance");
            check(screen.spearButtonBounds(0) == null, "The stance requires extra confirmation");
            check(result.getClash().getStopReason() == StopReason.SIDE_THREAT, "Side threat did not open a decision");
        }
        if (scenario == Scenario.APPROACH) {
            long moves = result.getClash().getTimeline().stream()
                .filter(fact -> fact.getType() == TacticalAttackResult.EventType.WOLF_MOVED).count();
            check(moves == 2, "Approach moves");
        }
        long start = decision - 2L * result.getDuration();
        System.out.println("SPEAR " + scenario + "/" + expected + " q=" + decision
            + " HP=" + scene.getHunterHealth() + "/" + scene.getCreatureHealth()
            + " cells=" + scene.getHunterPosition() + "/" + scene.getCreaturePosition()
            + " phase=" + clash.getWolfPhase() + "/" + clash.getWolfRemaining()
            + " stop=" + result.getClash().getStopReason() + " cursor=" + clash.getRandomCursor()
            + " events=" + result.getClash().getTimeline().stream()
                .map(fact -> (start + fact.getOffset()) + ":" + fact.getType()).collect(Collectors.joining(",")));
    }

    private void clickSpear() {
        Rectangle bounds = screen.spearButtonBounds(0);
        check(bounds != null, "Spear control unavailable");
        click((int) (bounds.x + bounds.width / 2), Gdx.graphics.getHeight() - 1 - (int) (bounds.y + bounds.height / 2));
    }

    private void selectWolf() {
        WorldPosition position = scene.getCreaturePosition();
        Vector3 projected = viewport.project(new Vector3((float) position.getX(), (float) position.getY(), 0));
        click(Math.round(projected.x), Gdx.graphics.getHeight() - 1 - Math.round(projected.y));
        check(screen.hasSelectedTarget(), "Wolf not targetable after recovery");
    }

    private void confirmAttack() {
        click(viewport.getScreenX() + 10, Gdx.graphics.getHeight() - viewport.getScreenY() - viewport.getScreenHeight() + 10);
    }

    private void click(int x, int y) {
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
            PixmapIO.writePNG(Gdx.files.local("../lwjgl3/build/boar-wolf-loop-" + name + ".png"), image);
        } finally {
            image.dispose();
        }
    }

    private void check(boolean condition, String requirement) {
        if (!condition) throw new AssertionError(scenario + ", stage " + stage + ": " + requirement);
    }

    @Override public void resize(int width, int height) { if (screen != null) screen.resize(width, height); }
    @Override public void dispose() { if (screen != null) screen.dispose(); }

    public static void main(String[] args) {
        Scenario scenario = Scenario.REPEL;
        boolean manual = args.length == 1;
        if (manual) {
            scenario = Arrays.stream(Scenario.values()).filter(value -> value.argument.equals(args[0])).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Use 3, 1, 0, side, approach, flank, or repeat"));
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
