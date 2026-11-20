package ru.darvell.game.hhg.graphicslab;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;

/** Реальные OpenGL/viewport/input; сохраняет кадры без генерации новых assets. */
public final class GraphicsLabSmoke extends ApplicationAdapter {
    private static boolean failed;
    private GraphicsLabScreen lab;
    private int stage;
    private int resizeWaitFrames;

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Graphics Lab smoke");
        config.setWindowedMode(640, 480);
        config.disableAudio(true);
        new Lwjgl3Application(new GraphicsLabSmoke(), config);
        if (failed) throw new AssertionError("Graphics Lab smoke failed");
    }

    @Override public void create() {
        lab = new GraphicsLabScreen();
        lab.show();
        lab.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    @Override public void render() {
        try {
            if (stage == 0) {
                check(lab.camera.zoom == 1 && lab.camera.viewportWidth == 20 && lab.camera.viewportHeight == 15, "game camera");
                lab.render(0);
                capture("comparison-640");
                key(Input.Keys.EQUALS);
                check(Math.abs(lab.figures[1].getScale() - 1.05f) < .001f && lab.figures[0].getScale() == 1, "independent scale");
                key(Input.Keys.RIGHT_BRACKET);
                check(lab.figures[1].getSize() == LabFigure.Size.LARGE, "size class switch");
                key(Input.Keys.SPACE);
                key(Input.Keys.T);
                key(Input.Keys.D);
                check(lab.tactical && lab.figures[1].getX() == 1.5f && lab.figures[1].getY() == -.5f, "cell movement");
                key(Input.Keys.A);
                lab.render(0);
                capture("tactical-640");
                key(Input.Keys.C);
                lab.render(0);
                capture("adjacent-640");
                key(Input.Keys.G);
                key(Input.Keys.B);
                key(Input.Keys.F);
                key(Input.Keys.P);
                check(!lab.showGrid && !lab.showBounds && !lab.showFootprint && !lab.showPivots, "overlays off");
                lab.render(0);
                capture("clean-adjacent-640");
                key(Input.Keys.G);
                key(Input.Keys.B);
                key(Input.Keys.F);
                key(Input.Keys.P);
                key(Input.Keys.SPACE);
                key(Input.Keys.T);
                lab.camera.position.add(-2.3f, 1.2f, 0);
                lab.controls.scrolled(0, -3);
                lab.render(0);
                assertAnchorProjection();
                check(lab.camera.zoom < 1, "zoom input");
                lab.render(0);
                capture("pan-zoom-640");
                key(Input.Keys.R);
                check(lab.camera.zoom == 1 && lab.camera.position.x == .5f, "game zoom reset");
                Gdx.graphics.setWindowedMode(1000, 600);
                stage = 1;
                return; // GLFW применяет resize между кадрами.
            }
            if (stage == 1) {
                if (!readySize(1000, 600)) return;
                lab.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
                lab.render(0);
                assertAnchorProjection();
                check(lab.viewport.getScreenWidth() == 800 && lab.viewport.getScreenX() == 100, "letterbox viewport");
                check(!lab.controls.touchDown(5, 300, 0, Input.Buttons.LEFT), "ignore letterbox input");
                capture("comparison-wide");
                Gdx.graphics.setWindowedMode(480, 800);
                stage = 2;
                resizeWaitFrames = 0;
                return;
            }
            if (stage == 2) {
                if (!readySize(480, 800)) return;
                lab.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
                lab.render(0);
                assertAnchorProjection();
                capture("comparison-portrait");
                InputAdapter sentinel = new InputAdapter();
                lab.hide();
                Gdx.input.setInputProcessor(sentinel);
                lab.show();
                lab.hide();
                check(Gdx.input.getInputProcessor() == sentinel, "input lifecycle");
                System.out.println("GRAPHICS_LAB_SMOKE_OK camera/scale/size/anchor/move/modes/overlays/pan/zoom/resize/letterbox/lifecycle");
                stage = 3;
            }
        } catch (Throwable error) {
            failed = true;
            error.printStackTrace();
        } finally {
            if (failed || stage == 3) Gdx.app.exit();
        }
    }

    private void assertAnchorProjection() {
        LabFigure f = lab.figures[1];
        Vector2 screen = lab.viewport.project(new Vector2(f.getX(), f.getY()));
        Vector2 back = lab.viewport.unproject(new Vector2(screen.x, Gdx.graphics.getHeight() - screen.y));
        check(Math.abs(back.x - f.getX()) < .001 && Math.abs(back.y - f.getY()) < .001, "anchor projection roundtrip");
        lab.controls.touchDown(Math.round(screen.x), Math.round(Gdx.graphics.getHeight() - screen.y), 0, Input.Buttons.LEFT);
        check(Math.abs(f.getX() - back.x) < .05 && Math.abs(f.getY() - back.y) < .05, "pointer maps to anchor");
    }

    private void key(int key) { lab.controls.keyDown(key); }
    private boolean readySize(int width, int height) {
        if (++resizeWaitFrames > 180) throw new AssertionError("Resize timeout");
        return Gdx.graphics.getWidth() == width && Gdx.graphics.getHeight() == height
            && Gdx.graphics.getBackBufferWidth() == width && Gdx.graphics.getBackBufferHeight() == height;
    }
    private void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private void capture(String name) {
        Pixmap pixmap = ScreenUtils.getFrameBufferPixmap(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        try {
            PixmapIO.writePNG(Gdx.files.absolute(System.getProperty("graphicsLab.output")).child(name + ".png"), pixmap, -1, true);
        } finally { pixmap.dispose(); }
    }
    @Override public void dispose() { lab.dispose(); }
}
