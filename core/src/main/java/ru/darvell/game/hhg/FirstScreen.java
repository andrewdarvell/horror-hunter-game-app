package ru.darvell.game.hhg;

import java.util.Arrays;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import ru.darvell.game.hhg.domain.world.CellCoordinates;
import ru.darvell.game.hhg.domain.world.HunterState;
import ru.darvell.game.hhg.domain.world.StaticAabb;
import ru.darvell.game.hhg.domain.world.TacticalGrid;
import ru.darvell.game.hhg.domain.world.WorldGeometry;
import ru.darvell.game.hhg.domain.world.WorldPosition;

/** First screen of the application. Displayed after the application is created. */
public class FirstScreen implements Screen {
    private static final float VIRTUAL_WIDTH = 20.0f;
    private static final float VIRTUAL_HEIGHT = 15.0f;
    private static final double PROTOTYPE_MOVEMENT_SPEED = 5.0;
    private static final double PROTOTYPE_CELL_SIZE = 2.0;
    private static final double PROTOTYPE_HUNTER_RADIUS = 0.4;

    private final HunterState hunter;
    private final TacticalGrid tacticalGrid;
    private final WorldGeometry worldGeometry;
    private final OrthographicCamera camera;
    private final Viewport viewport;
    private final ShapeRenderer shapeRenderer;
    private boolean debugGridVisible = true;

    public FirstScreen() {
        worldGeometry = new WorldGeometry(Arrays.asList(
            new StaticAabb(3.0, -3.0, 1.0, 6.0),
            new StaticAabb(-5.0, 2.0, 4.0, 1.0),
            new StaticAabb(-3.0, -4.0, 2.0, 2.0)
        ));
        hunter = new HunterState(
            new WorldPosition(0.0, 0.0), PROTOTYPE_MOVEMENT_SPEED, PROTOTYPE_HUNTER_RADIUS);
        if (!worldGeometry.isCirclePositionValid(hunter.getPosition(), hunter.getRadius())) {
            throw new IllegalStateException("Initial hunter position must be valid");
        }
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
        hunter.move(directionX, directionY, delta, worldGeometry);
    }

    private boolean isCellPassable(int cellX, int cellY) {
        return tacticalGrid.isCellPassable(
            new CellCoordinates(cellX, cellY), hunter.getRadius(), worldGeometry);
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
        shapeRenderer.setColor(Color.SLATE);
        for (StaticAabb obstacle : worldGeometry.getObstacles()) {
            shapeRenderer.rect(
                (float) obstacle.getX(),
                (float) obstacle.getY(),
                (float) obstacle.getWidth(),
                (float) obstacle.getHeight()
            );
        }
        shapeRenderer.setColor(Color.GOLDENROD);
        WorldPosition position = hunter.getPosition();
        shapeRenderer.circle(
            (float) position.getX(),
            (float) position.getY(),
            (float) hunter.getRadius(),
            32
        );
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
        int minCellX = (int) Math.floor(
            (minX - tacticalGrid.getOriginX()) / cellSize);
        int maxCellX = (int) Math.floor(
            (maxX - tacticalGrid.getOriginX()) / cellSize);
        int minCellY = (int) Math.floor(
            (minY - tacticalGrid.getOriginY()) / cellSize);
        int maxCellY = (int) Math.floor(
            (maxY - tacticalGrid.getOriginY()) / cellSize);

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.32f, 0.08f, 0.08f, 1.0f);
        for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
            for (int cellY = minCellY; cellY <= maxCellY; cellY++) {
                if (!isCellPassable(cellX, cellY)) {
                    double worldX = tacticalGrid.getOriginX() + cellX * cellSize;
                    double worldY = tacticalGrid.getOriginY() + cellY * cellSize;
                    shapeRenderer.rect(
                        (float) worldX,
                        (float) worldY,
                        (float) cellSize,
                        (float) cellSize
                    );
                }
            }
        }
        shapeRenderer.end();

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
