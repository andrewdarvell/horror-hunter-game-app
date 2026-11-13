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
import ru.darvell.game.hhg.domain.tactical.CellCoordinates;
import ru.darvell.game.hhg.domain.actor.CreaturePatrolState;
import ru.darvell.game.hhg.domain.actor.HunterState;
import ru.darvell.game.hhg.domain.world.StaticAabb;
import ru.darvell.game.hhg.domain.tactical.TacticalGrid;
import ru.darvell.game.hhg.domain.world.WorldGeometry;
import ru.darvell.game.hhg.domain.world.WorldPosition;
import ru.darvell.game.hhg.domain.scene.WorldSceneState;
import ru.darvell.game.hhg.domain.scene.SceneMode;
import ru.darvell.game.hhg.domain.tactical.TacticalEntryResult;

/** First screen of the application. Displayed after the application is created. */
public class FirstScreen implements Screen {
    private static final float VIRTUAL_WIDTH = 20.0f;
    private static final float VIRTUAL_HEIGHT = 15.0f;
    private static final double PROTOTYPE_MOVEMENT_SPEED = 5.0;
    private static final double PROTOTYPE_CELL_SIZE = 1.0;
    private static final double PROTOTYPE_HUNTER_RADIUS = 0.4;

    private final WorldSceneState scene;
    private final TacticalGrid tacticalGrid;
    private final WorldGeometry worldGeometry;
    private final OrthographicCamera camera;
    private final Viewport viewport;
    private final ShapeRenderer shapeRenderer;
    private TacticalEntryTransition transition;
    private WorldPosition visualHunterPosition;
    private WorldPosition visualCreaturePosition;
    private boolean debugGridVisible = true;

    public FirstScreen() {
        worldGeometry = new WorldGeometry(Arrays.asList(
            new StaticAabb(3.0, -3.0, 1.0, 6.0),
            new StaticAabb(-5.0, 2.0, 4.0, 1.0),
            new StaticAabb(-3.0, -4.0, 2.0, 2.0)
        ));
        HunterState hunter = new HunterState(
            new WorldPosition(0.0, 0.0), PROTOTYPE_MOVEMENT_SPEED, PROTOTYPE_HUNTER_RADIUS);
        if (!worldGeometry.isCirclePositionValid(hunter.getPosition(), hunter.getRadius())) {
            throw new IllegalStateException("Initial hunter position must be valid");
        }
        CreaturePatrolState creaturePatrol = new CreaturePatrolState(Arrays.asList(
            new WorldPosition(5.0, 0.0),
            new WorldPosition(5.0, 1.0)
        ), 2.0, 0.3, 0.5, 3.0, worldGeometry);
        tacticalGrid = new TacticalGrid(0.0, 0.0, PROTOTYPE_CELL_SIZE);
        scene = new WorldSceneState(hunter, creaturePatrol, tacticalGrid, worldGeometry);
        camera = new OrthographicCamera();
        viewport = new FitViewport(VIRTUAL_WIDTH, VIRTUAL_HEIGHT, camera);
        shapeRenderer = new ShapeRenderer();
        selectVisualPositions();
    }

    /** Позволяет проверять обновления и resize без создания графических ресурсов. */
    FirstScreen(WorldSceneState scene, TacticalGrid tacticalGrid, WorldGeometry worldGeometry,
                Viewport viewport, ShapeRenderer shapeRenderer) {
        this.scene = scene;
        this.tacticalGrid = tacticalGrid;
        this.worldGeometry = worldGeometry;
        this.viewport = viewport;
        this.camera = (OrthographicCamera) viewport.getCamera();
        this.shapeRenderer = shapeRenderer;
        selectVisualPositions();
    }

    @Override
    public void show() {
        // Prepare your screen here.
    }

    @Override
    public void render(float delta) {
        TacticalEntryResult.Outcome outcome = updateScene(axis(Input.Keys.A, Input.Keys.D),
            axis(Input.Keys.S, Input.Keys.W), delta, Gdx.input.isKeyJustPressed(Input.Keys.T));
        if (outcome == TacticalEntryResult.Outcome.NO_VALID_PLACEMENT
            || outcome == TacticalEntryResult.Outcome.INVALID_INPUT) {
            Gdx.app.log("Tactical entry", "Entry rejected: " + outcome);
        }
        updateDebugControls();
        viewport.apply();
        followHunter();
        drawScene();
    }

    TacticalEntryResult.Outcome updateScene(double x, double y, double delta, boolean manualEntry) {
        SceneMode previousMode = scene.getMode();
        WorldPosition hunterStart = scene.getHunterPosition();
        WorldPosition creatureStart = scene.getCreaturePosition();
        double safeDelta = Double.isFinite(delta) && delta >= 0.0 ? delta : 0.0;
        TacticalEntryResult.Outcome outcome = scene.update(x, y, safeDelta, manualEntry);
        if (previousMode == SceneMode.EXPLORATION && scene.getMode() == SceneMode.TACTICAL
            && outcome == TacticalEntryResult.Outcome.SUCCESS) {
            transition = new TacticalEntryTransition(hunterStart, creatureStart,
                scene.getHunterPosition(), scene.getCreaturePosition());
            // delta относится к update до запуска: первый кадр обязан показать start.
        } else if (transition != null) {
            transition.advance(safeDelta);
            if (transition.isComplete()) transition = null;
        }
        selectVisualPositions();
        return outcome;
    }

    private void selectVisualPositions() {
        visualHunterPosition = transition == null
            ? scene.getHunterPosition() : transition.getHunterPosition();
        visualCreaturePosition = transition == null
            ? scene.getCreaturePosition() : transition.getCreaturePosition();
    }

    WorldPosition getVisualHunterPosition() {
        return visualHunterPosition;
    }

    WorldPosition getVisualCreaturePosition() {
        return visualCreaturePosition;
    }

    private boolean isCellPassable(int cellX, int cellY) {
        return tacticalGrid.isCellPassable(
            new CellCoordinates(cellX, cellY), scene.getHunterRadius(), worldGeometry);
    }

    private static int axis(int negativeKey, int positiveKey) {
        int value = 0;
        if (Gdx.input.isKeyPressed(negativeKey)) value--;
        if (Gdx.input.isKeyPressed(positiveKey)) value++;
        return value;
    }

    private void followHunter() {
        WorldPosition position = visualHunterPosition;
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
        if (debugGridVisible || scene.getMode() == SceneMode.TACTICAL) {
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
        WorldPosition position = visualHunterPosition;
        shapeRenderer.circle(
            (float) position.getX(),
            (float) position.getY(),
            (float) scene.getHunterRadius(),
            32
        );
        shapeRenderer.setColor(scene.isCreatureAlerted() ? Color.RED : Color.CYAN);
        WorldPosition creaturePosition = visualCreaturePosition;
        shapeRenderer.circle(
            (float) creaturePosition.getX(),
            (float) creaturePosition.getY(),
            (float) scene.getCreatureRadius(),
            32
        );
        WorldPosition lastSeen = scene.getLastSeenHunterPosition();
        if (lastSeen != null) {
            double dx = lastSeen.getX() - creaturePosition.getX();
            double dy = lastSeen.getY() - creaturePosition.getY();
            double length = Math.hypot(dx, dy);
            if (length > 0.0) {
                shapeRenderer.setColor(Color.WHITE);
                shapeRenderer.rectLine((float) creaturePosition.getX(), (float) creaturePosition.getY(),
                    (float) (creaturePosition.getX() + dx / length * 0.6),
                    (float) (creaturePosition.getY() + dy / length * 0.6), 0.08f);
            }
        }
        shapeRenderer.end();
        if (scene.getPlacement() != null) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            drawOccupiedCell(scene.getPlacement().getHunterCell(), Color.GOLDENROD);
            drawOccupiedCell(scene.getPlacement().getCreatureCell(), Color.CYAN);
            shapeRenderer.end();
        }
    }

    private void drawOccupiedCell(CellCoordinates cell, Color color) {
        WorldPosition center = tacticalGrid.cellToWorldCenter(cell);
        double size = tacticalGrid.getCellSize();
        shapeRenderer.setColor(color);
        shapeRenderer.rect((float) (center.getX() - size / 2),
            (float) (center.getY() - size / 2), (float) size, (float) size);
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
        viewport.update(width, height, false);
        followHunter();
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
