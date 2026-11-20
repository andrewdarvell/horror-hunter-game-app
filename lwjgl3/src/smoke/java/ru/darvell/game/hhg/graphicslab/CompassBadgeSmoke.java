package ru.darvell.game.hhg.graphicslab;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;

/** Проверяет tactical-only реальными framebuffer-сравнениями, сохраняет матрицу кадров. */
public final class CompassBadgeSmoke extends ApplicationAdapter {
    private static boolean failed;
    private GraphicsLabScreen lab;
    private int stage;
    private int waitFrames;

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Graphics Lab Compass Badge smoke");
        config.setWindowedMode(640, 480);
        config.disableAudio(true);
        new Lwjgl3Application(new CompassBadgeSmoke(), config);
        if (failed) throw new AssertionError("Compass Badge smoke failed");
    }

    @Override public void create() {
        lab = new GraphicsLabScreen();
        lab.show();
        lab.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        key(Input.Keys.B);
        key(Input.Keys.F);
        key(Input.Keys.P);
        key(Input.Keys.K);
    }

    @Override public void render() {
        try {
            if (stage == 0) {
                modeAndControls();
                key(Input.Keys.NUM_2);
                key(Input.Keys.J);
                key(Input.Keys.T);
                for (CompassBadge.Position position : CompassBadge.Position.values()) {
                    setPosition(position);
                    lab.figures[1].resetFacing();
                    Set<Integer> arrows = new HashSet<>();
                    for (Facing facing : Facing.values()) {
                        check(lab.figures[1].getFacing() == facing, "existing clockwise convention");
                        lab.render(0);
                        checkBadgePresent(lab.figures[1]);
                        arrows.add(badgePixels(lab.figures[1]));
                        capture("single-" + position + "-" + facing);
                        key(Input.Keys.E);
                    }
                    check(arrows.size() == 8, "eight distinct glyphs " + position);
                }
                scaleAndMovement();
                key(Input.Keys.J);
                key(Input.Keys.H);
                if (!lab.allFacing) key(Input.Keys.H);
                key(Input.Keys.C);
                differentDirections();
                resolutionMatrix("640");
                key(Input.Keys.T);
                setPosition(CompassBadge.Position.TOP_RIGHT);
                lab.render(0);
                capture("exploration-640");
                assertHidden();
                key(Input.Keys.K);
                lab.render(0);
                capture("exploration-hud-640");
                key(Input.Keys.T);
                lab.render(0);
                capture("tactical-hud-640");
                key(Input.Keys.K);
                Gdx.graphics.setWindowedMode(1000, 600);
                stage = 1;
                return;
            }
            if (stage == 1) {
                if (!readySize(1000, 600)) return;
                lab.resize(1000, 600);
                resolutionMatrix("1000");
                Gdx.graphics.setWindowedMode(1600, 900);
                waitFrames = 0;
                stage = 2;
                return;
            }
            if (stage == 2) {
                if (!readySize(1600, 900)) return;
                lab.resize(1600, 900);
                check(lab.viewport.getScreenWidth() == 1200 && lab.viewport.getScreenX() == 200, "wide viewport");
                resolutionMatrix("1600");
                key(Input.Keys.T);
                assertHidden();
                stage = 3;
                System.out.println("COMPASS_BADGE_SMOKE_OK 8-facing/3-positions/tactical-only/framebuffer-equality/selection/hover/move/scale/class/controls/3-zooms/3-windows");
            }
        } catch (Throwable error) {
            failed = true;
            error.printStackTrace();
        } finally {
            if (failed || stage == 3) Gdx.app.exit();
        }
    }

    private void modeAndControls() {
        check(!lab.tactical, "exploration startup");
        check(lab.facingIndicator.getKind() == FacingIndicator.Kind.OFF, "unchanged default");
        setBadge();
        check(lab.compassBadge.getPosition() == CompassBadge.Position.TOP_RIGHT, "default position");
        for (CompassBadge.Position position : CompassBadge.Position.values()) {
            setPosition(position);
            for (Facing facing : Facing.values()) {
                key(Input.Keys.NUM_1 + facing.ordinal() % 3);
                LabFigure selected = lab.figures[lab.selected];
                selected.resetFacing();
                selected.turn(facing.ordinal());
                lab.controls.mouseMoved(320, 220);
                assertHidden();
                key(Input.Keys.H);
                assertHidden();
            }
        }
        // Диагностика остаётся независимой от gate.
        key(Input.Keys.B);
        key(Input.Keys.F);
        key(Input.Keys.P);
        assertHidden();
        check(lab.showBounds && lab.showFootprint && lab.showPivots, "diagnostics independent");
        key(Input.Keys.B);
        key(Input.Keys.F);
        key(Input.Keys.P);
        key(Input.Keys.NUM_2);
        LabFigure figure = lab.figures[1];
        figure.resetFacing();
        float start = figure.getX();
        Input original = Gdx.input;
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[] {Input.class},
            (proxy, method, args) -> method.getName().equals("isKeyPressed")
                ? (int) args[0] == Input.Keys.D : method.invoke(original, args));
        try { lab.render(.05f); } finally { Gdx.input = original; }
        check(figure.getX() > start && figure.getFacing() == Facing.N, "movement keeps N");
        assertHidden();
        figure.moveTo(.5f, -.5f);
        if (lab.allFacing) key(Input.Keys.H);
        key(Input.Keys.T);
        check(lab.tactical, "existing T control");
        lab.render(0);
        checkBadgePresent(figure);
        key(Input.Keys.T);
        assertHidden();
    }

    private void scaleAndMovement() {
        LabFigure figure = lab.figures[1];
        setPosition(CompassBadge.Position.TOP_RIGHT);
        for (int size = 0; size < 3; size++) {
            key(Input.Keys.RIGHT_BRACKET);
            for (float scale : new float[] {.25f, 1, 2.5f}) {
                figure.setScale(scale);
                Rectangle visual = figure.bounds();
                lab.render(0);
                Rectangle badge = layout(figure);
                check(Math.abs(badge.width / unitsPerPixel() - 12) < .001, "fixed screen size at base zoom");
                checkBadgePresent(figure);
                check(visual.equals(figure.bounds()), "render keeps visual bounds");
                check(figure.cell(lab.grid).getX() == 0 && figure.cell(lab.grid).getY() == -1, "footprint unchanged");
            }
        }
        figure.setScale(1);
        figure.resetFacing();
        Rectangle before = layout(figure);
        key(Input.Keys.D);
        Rectangle after = layout(figure);
        check(Math.abs(after.x - before.x - 1) < .0001 && after.y == before.y, "badge follows cell movement");
        check(figure.getFacing() == Facing.N, "cell movement keeps facing");
        figure.moveTo(.5f, -.5f);
    }

    private void resolutionMatrix(String name) {
        for (float zoom : new float[] {.75f, 1, 1.5f}) {
            lab.setZoom(zoom);
            for (CompassBadge.Position position : CompassBadge.Position.values()) {
                setPosition(position);
                lab.render(0);
                for (LabFigure figure : lab.figures) checkBadgePresent(figure);
                float pixels = layout(lab.figures[1]).width / unitsPerPixel();
                check(pixels >= 7.999f && pixels <= 16.001f, "screen limits");
                capture("adjacent-" + name + "-zoom-" + zoom + "-" + position);
            }
        }
        lab.setZoom(1);
    }

    private void differentDirections() {
        for (LabFigure figure : lab.figures) figure.resetFacing();
        lab.figures[0].turn(1);
        lab.figures[1].turn(2);
        lab.figures[2].turn(5);
    }

    private void assertHidden() {
        check(!lab.tactical, "absence check is exploration");
        setBadge();
        lab.render(0);
        Pixmap badge = frame();
        while (lab.facingIndicator.getKind() != FacingIndicator.Kind.OFF) key(Input.Keys.V);
        lab.render(0);
        Pixmap off = frame();
        try { check(badge.getPixels().equals(off.getPixels()), "exploration Badge must equal Off framebuffer"); }
        finally { badge.dispose(); off.dispose(); }
        setBadge();
    }

    private void checkBadgePresent(LabFigure figure) {
        Rectangle badge = layout(figure);
        Vector2 pixel = lab.viewport.project(new Vector2(badge.x, badge.y));
        int size = (int) Math.ceil(badge.width / unitsPerPixel()) + 1;
        Pixmap sample = ScreenUtils.getFrameBufferPixmap((int) pixel.x, (int) pixel.y, size, size);
        boolean found = false;
        try {
            for (int x = 0; x < size; x++) for (int y = 0; y < size; y++) {
                found |= sample.getPixel(x, y) == 0xc0a16bff;
            }
            check(found, "badge glyph visible " + figure.getSize());
        }
        finally { sample.dispose(); }
    }

    private int badgePixels(LabFigure figure) {
        Rectangle badge = layout(figure);
        Vector2 pixel = lab.viewport.project(new Vector2(badge.x, badge.y));
        int size = (int) Math.ceil(badge.width / unitsPerPixel());
        Pixmap sample = ScreenUtils.getFrameBufferPixmap((int) pixel.x, (int) pixel.y, size, size);
        try { return sample.getPixels().hashCode(); } finally { sample.dispose(); }
    }

    private Rectangle layout(LabFigure figure) {
        return CompassBadge.layout(figure.bounds(), 1, unitsPerPixel(), lab.compassBadge.getPosition());
    }
    private float unitsPerPixel() { return lab.camera.viewportWidth * lab.camera.zoom / lab.viewport.getScreenWidth(); }
    private void setBadge() { while (lab.facingIndicator.getKind() != FacingIndicator.Kind.COMPASS_BADGE) key(Input.Keys.V); }
    private void setPosition(CompassBadge.Position position) {
        while (lab.compassBadge.getPosition() != position) key(Input.Keys.M);
    }
    private void key(int key) { check(lab.controls.keyDown(key), "recognized control " + key); }
    private boolean readySize(int width, int height) {
        if (++waitFrames > 180) throw new AssertionError("Resize timeout");
        return Gdx.graphics.getWidth() == width && Gdx.graphics.getHeight() == height
            && Gdx.graphics.getBackBufferWidth() == width && Gdx.graphics.getBackBufferHeight() == height;
    }
    private Pixmap frame() {
        return ScreenUtils.getFrameBufferPixmap(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    }
    private void capture(String name) {
        Pixmap pixmap = frame();
        try { PixmapIO.writePNG(Gdx.files.absolute(System.getProperty("graphicsLab.output")).child(name + ".png"), pixmap, -1, true); }
        finally { pixmap.dispose(); }
    }
    private void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    @Override public void dispose() { lab.dispose(); }
}
