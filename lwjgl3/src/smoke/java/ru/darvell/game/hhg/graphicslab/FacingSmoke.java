package ru.darvell.game.hhg.graphicslab;

import java.lang.reflect.Proxy;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.ScreenUtils;

/** Матрица реальных кадров: восемь направлений, три варианта, соседи и resize. */
public final class FacingSmoke extends ApplicationAdapter {
    private static boolean failed;
    private GraphicsLabScreen lab;
    private int stage;
    private int waitFrames;

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Graphics Lab facing smoke");
        config.setWindowedMode(640, 480);
        config.disableAudio(true);
        new Lwjgl3Application(new FacingSmoke(), config);
        if (failed) throw new AssertionError("Facing smoke failed");
    }

    @Override public void create() {
        lab = new GraphicsLabScreen();
        lab.show();
        lab.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    @Override public void render() {
        try {
            if (stage == 0) {
                check(lab.facingIndicator.getKind() == FacingIndicator.Kind.OFF, "original startup view");
                lab.controls.keyDown(Input.Keys.B);
                lab.controls.keyDown(Input.Keys.F);
                lab.controls.keyDown(Input.Keys.P);
                lab.controls.keyDown(Input.Keys.J);
                lab.controls.keyDown(Input.Keys.K);
                check(lab.solo && !lab.showHud, "solo screenshot controls");
                for (FacingIndicator.Kind kind : FacingIndicator.Kind.values()) {
                    if (kind == FacingIndicator.Kind.OFF || kind == FacingIndicator.Kind.COMPASS_BADGE) continue;
                    setKind(kind);
                    lab.figures[1].resetFacing();
                    for (Facing direction : Facing.values()) {
                        check(lab.figures[1].getFacing() == direction, "clockwise input " + direction);
                        lab.render(0);
                        capture("single-" + kind + "-" + direction);
                        lab.controls.keyDown(Input.Keys.E);
                    }
                    check(lab.figures[1].getFacing() == Facing.N, "full rotation");
                    lab.controls.keyDown(Input.Keys.Q);
                    check(lab.figures[1].getFacing() == Facing.NW, "counterclockwise input");
                    lab.controls.keyDown(Input.Keys.BACKSPACE);
                }
                indicatorSizeIgnoresVisualScale();
                movementKeepsNorth();
                checkSettingsAndBlending();
                lab.controls.keyDown(Input.Keys.J);
                lab.controls.keyDown(Input.Keys.H);
                check(lab.allFacing && !lab.solo, "all directions");
                lab.controls.keyDown(Input.Keys.C);
                differentDirections();
                comparison("adjacent-640");
                lab.controls.keyDown(Input.Keys.C);
                comparison("sizes-640");
                lab.controls.keyDown(Input.Keys.K);
                setKind(FacingIndicator.Kind.NOTCH);
                lab.render(0);
                capture("hud-640");
                lab.controls.keyDown(Input.Keys.K);
                lab.figures[2].setScale(2);
                comparison("large-scale-640");
                lab.figures[2].setScale(1);
                lab.setZoom(1.5f);
                comparison("zoom-out-640");
                lab.setZoom(.6f);
                comparison("zoom-in-640");
                lab.setZoom(1);
                Gdx.graphics.setWindowedMode(1000, 600);
                stage = 1;
                return;
            }
            if (stage == 1) {
                if (!readySize(1000, 600)) return;
                lab.resize(1000, 600);
                comparison("sizes-1000");
                lab.controls.keyDown(Input.Keys.C);
                comparison("adjacent-1000");
                Gdx.graphics.setWindowedMode(1600, 900);
                stage = 2;
                waitFrames = 0;
                return;
            }
            if (stage == 2) {
                if (!readySize(1600, 900)) return;
                lab.resize(1600, 900);
                check(lab.viewport.getScreenWidth() == 1200 && lab.viewport.getScreenX() == 200, "wide FitViewport");
                comparison("adjacent-1600");
                lab.controls.keyDown(Input.Keys.C);
                comparison("sizes-1600");
                lab.controls.keyDown(Input.Keys.SPACE);
                for (LabFigure figure : lab.figures) check(figure.getFacing() == Facing.N, "full reset facing");
                setKind(FacingIndicator.Kind.OFF);
                lab.render(0);
                stage = 3;
                System.out.println("FACING_SMOKE_OK 24-directions/adjacent/sizes/scale/zoom/640/1000/1600/move-east-facing-north/blend/reset/off");
            }
        } catch (Throwable error) {
            failed = true;
            error.printStackTrace();
        } finally {
            if (failed || stage == 3) Gdx.app.exit();
        }
    }

    private void movementKeepsNorth() {
        LabFigure medium = lab.figures[1];
        medium.resetFacing();
        float start = medium.getX();
        Input original = Gdx.input;
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[] {Input.class},
            (proxy, method, args) -> method.getName().equals("isKeyPressed")
                ? (int) args[0] == Input.Keys.D : method.invoke(original, args));
        try { lab.render(.05f); } finally { Gdx.input = original; }
        check(medium.getX() > start && medium.getFacing() == Facing.N, "continuous east movement keeps N");
        lab.controls.keyDown(Input.Keys.T);
        start = medium.getX();
        lab.controls.keyDown(Input.Keys.D);
        check(medium.getX() == start + 1 && medium.getFacing() == Facing.N, "cell east movement keeps N");
        lab.render(0);
        capture("moving-east-facing-N");
        lab.controls.keyDown(Input.Keys.T);
        medium.moveTo(.5f, -.5f);
    }

    private void indicatorSizeIgnoresVisualScale() {
        LabFigure figure = lab.figures[1];
        figure.resetFacing();
        figure.turn(4); // S: контрольный пиксель на земле не закрыт телом.
        for (FacingIndicator.Kind kind : FacingIndicator.Kind.values()) {
            if (kind == FacingIndicator.Kind.OFF || kind == FacingIndicator.Kind.COMPASS_BADGE) continue;
            float offset = kind == FacingIndicator.Kind.ARROW ? -.78f : kind == FacingIndicator.Kind.NOTCH ? -.21f : -.6f;
            setKind(FacingIndicator.Kind.OFF);
            lab.render(0);
            int ground = groundPixel(figure, offset);
            setKind(kind);
            lab.render(0);
            int expected = groundPixel(figure, offset);
            check(expected != ground, "visible ground indicator " + kind);
            for (int size = 0; size < 3; size++) {
                figure.cycleSize(1);
                for (float scale : new float[] {.25f, 1, 2.5f}) {
                    figure.setScale(scale);
                    lab.render(0);
                    check(groundPixel(figure, offset) == expected, "fixed cell-relative indicator " + kind);
                }
            }
        }
        figure.setScale(1);
        figure.resetFacing();
    }

    private int groundPixel(LabFigure figure, float yOffset) {
        Vector2 pixel = lab.viewport.project(new Vector2(figure.getX(), figure.getY() + yOffset));
        Pixmap sample = ScreenUtils.getFrameBufferPixmap(Math.round(pixel.x), Math.round(pixel.y), 1, 1);
        try { return sample.getPixel(0, 0); } finally { sample.dispose(); }
    }

    private void checkSettingsAndBlending() {
        setKind(FacingIndicator.Kind.SECTOR);
        lab.controls.keyDown(Input.Keys.I);
        lab.controls.keyDown(Input.Keys.L);
        check(lab.facingIndicator.getSectorAngle() == 105 && lab.facingIndicator.getSectorRadius() > .9f, "sector controls");
        lab.controls.keyDown(Input.Keys.U);
        lab.controls.keyDown(Input.Keys.O);
        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glBlendFuncSeparate(GL20.GL_ONE, GL20.GL_ZERO, GL20.GL_ONE, GL20.GL_ZERO);
        Rectangle before = lab.figures[1].bounds();
        lab.render(0);
        check(!Gdx.gl.glIsEnabled(GL20.GL_BLEND), "blend enable restored");
        int[] names = {GL20.GL_BLEND_SRC_RGB, GL20.GL_BLEND_DST_RGB, GL20.GL_BLEND_SRC_ALPHA, GL20.GL_BLEND_DST_ALPHA};
        int[] expected = {GL20.GL_ONE, GL20.GL_ZERO, GL20.GL_ONE, GL20.GL_ZERO};
        for (int i = 0; i < names.length; i++) {
            java.nio.IntBuffer result = BufferUtils.newIntBuffer(1);
            Gdx.gl.glGetIntegerv(names[i], result);
            check(result.get(0) == expected[i], "blend factors restored");
        }
        check(before.equals(lab.figures[1].bounds()), "render leaves bounds unchanged");
        check(Gdx.gl.glGetError() == GL20.GL_NO_ERROR, "OpenGL error");
    }

    private void differentDirections() {
        for (LabFigure figure : lab.figures) figure.resetFacing();
        lab.figures[0].turn(1); // NE: перекрытие северной части.
        lab.figures[1].turn(2); // E: между соседями.
        lab.figures[2].turn(5); // SW: перед фигурой.
    }

    private void comparison(String name) {
        for (FacingIndicator.Kind kind : FacingIndicator.Kind.values()) {
            if (kind == FacingIndicator.Kind.OFF || kind == FacingIndicator.Kind.COMPASS_BADGE) continue;
            setKind(kind);
            lab.render(0);
            capture(name + "-" + kind);
        }
    }

    private void setKind(FacingIndicator.Kind kind) {
        while (lab.facingIndicator.getKind() != kind) lab.controls.keyDown(Input.Keys.V);
    }
    private boolean readySize(int width, int height) {
        if (++waitFrames > 180) throw new AssertionError("Resize timeout");
        return Gdx.graphics.getWidth() == width && Gdx.graphics.getHeight() == height
            && Gdx.graphics.getBackBufferWidth() == width && Gdx.graphics.getBackBufferHeight() == height;
    }
    private void capture(String name) {
        Pixmap pixmap = ScreenUtils.getFrameBufferPixmap(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        try {
            PixmapIO.writePNG(Gdx.files.absolute(System.getProperty("graphicsLab.output")).child(name + ".png"), pixmap, -1, true);
        } finally { pixmap.dispose(); }
    }
    private void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    @Override public void dispose() { lab.dispose(); }
}
