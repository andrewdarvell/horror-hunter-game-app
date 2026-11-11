package ru.darvell.game.hhg;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import ru.darvell.game.hhg.domain.world.HunterState;
import ru.darvell.game.hhg.domain.world.TacticalGrid;
import ru.darvell.game.hhg.domain.world.WorldPosition;

/** First screen of the application. Displayed after the application is created. */
public class FirstScreen implements Screen {
    private static final float VIRTUAL_WIDTH = 20.0f;
    private static final float VIRTUAL_HEIGHT = 15.0f;
    private static final double PROTOTYPE_MOVEMENT_SPEED = 5.0;
    private static final double PROTOTYPE_CELL_SIZE = 2.0;
    private static final float HUNTER_RADIUS = 0.4f;

    private final HunterState hunter;
    private final TacticalGrid tacticalGrid;
    private final OrthographicCamera camera;
    private final Viewport viewport;
    private final ShapeRenderer shapeRenderer;
    private boolean debugGridVisible = true;

    public FirstScreen() {
        hunter = new HunterState(new WorldPosition(0.0, 0.0), PROTOTYPE_MOVEMENT_SPEED);
        tacticalGrid = new TacticalGrid(0.0, 0.0, PROTOTYPE_CELL_SIZE);
        camera = new OrthographicCamera();
        viewport = new FitViewport(VIRTUAL_WIDTH, VIRTUAL_HEIGHT, camera);
        shapeRenderer = new ShapeRenderer();
    }

    @Override
    public void show() {
        // Prepare your screen here.
    }

    @Override
    public void render(float delta) {
        updateHunter(delta);
        updateDebugControls();
        viewport.apply();
        followHunter();
        drawScene();
    }

    private void updateHunter(float delta) {
        double directionX = axis(Input.Keys.A, Input.Keys.D);
        double directionY = axis(Input.Keys.S, Input.Keys.W);
        hunter.move(directionX, directionY, delta);
    }

    private static int axis(int negativeKey, int positiveKey) {
        int value = 0;
        if (Gdx.input.isKeyPressed(negativeKey)) value--;
        if (Gdx.input.isKeyPressed(positiveKey)) value++;
        return value;
    }

    private void followHunter() {
        WorldPosition position = hunter.getPosition();
        camera.position.set((float) position.getX(), (float) position.getY(), 0.0f);
        camera.update();
    }

    private void updateDebugControls() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.G)) {
            debugGridVisible = !debugGridVisible;
        }
    }

    private void drawScene() {
        ScreenUtils.clear(0.10f, 0.16f, 0.12f, 1.0f);
        shapeRenderer.setProjectionMatrix(camera.combined);
        if (debugGridVisible) {
            drawDebugGrid();
        }
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(Color.GOLDENROD);
        WorldPosition position = hunter.getPosition();
        shapeRenderer.circle((float) position.getX(), (float) position.getY(), HUNTER_RADIUS, 32);
        shapeRenderer.end();
    }

    private void drawDebugGrid() {
        float halfWidth = camera.viewportWidth * camera.zoom * 0.5f;
        float halfHeight = camera.viewportHeight * camera.zoom * 0.5f;
        double minX = camera.position.x - halfWidth;
        double maxX = camera.position.x + halfWidth;
        double minY = camera.position.y - halfHeight;
        double maxY = camera.position.y + halfHeight;
        double cellSize = tacticalGrid.getCellSize();

        double firstX = firstGridLine(minX, tacticalGrid.getOriginX(), cellSize);
        double firstY = firstGridLine(minY, tacticalGrid.getOriginY(), cellSize);

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(Color.DARK_GRAY);
        for (double x = firstX; x <= maxX; x += cellSize) {
            shapeRenderer.line((float) x, (float) minY, (float) x, (float) maxY);
        }
        for (double y = firstY; y <= maxY; y += cellSize) {
            shapeRenderer.line((float) minX, (float) y, (float) maxX, (float) y);
        }
        shapeRenderer.end();
    }

    private static double firstGridLine(double minimum, double origin, double cellSize) {
        return origin + Math.floor((minimum - origin) / cellSize) * cellSize;
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        viewport.update(width, height, true);
    }

    @Override
    public void pause() {
        // Invoked when your application is paused.
    }

    @Override
    public void resume() {
        // Invoked when your application is resumed after pause.
    }

    @Override
    public void hide() {
        // This method is called when another screen replaces this one.
    }

    @Override
    public void dispose() {
        shapeRenderer.dispose();
    }
}
