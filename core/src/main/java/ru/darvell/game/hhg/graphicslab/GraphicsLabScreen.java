package ru.darvell.game.hhg.graphicslab;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.glutils.HdpiUtils;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import ru.darvell.game.hhg.PrototypeViewConfig;
import ru.darvell.game.hhg.domain.tactical.CellCoordinates;
import ru.darvell.game.hhg.domain.tactical.TacticalGrid;

/** Изолированный визуальный эксперимент: боевые модели здесь не создаются. */
public final class GraphicsLabScreen extends ScreenAdapter {
    final OrthographicCamera camera = new OrthographicCamera();
    final FitViewport viewport = new FitViewport(PrototypeViewConfig.WORLD_WIDTH,
        PrototypeViewConfig.WORLD_HEIGHT, camera);
    final TacticalGrid grid = new TacticalGrid(0, 0, PrototypeViewConfig.CELL_SIZE);
    final LabFigure[] figures = {
        new LabFigure(LabFigure.Size.SMALL, -2.5f, -.5f),
        new LabFigure(LabFigure.Size.MEDIUM, .5f, -.5f),
        new LabFigure(LabFigure.Size.LARGE, 3.5f, -.5f)
    };
    private final Color[] colors = {Color.valueOf("c39458"), Color.valueOf("91aaa0"), Color.valueOf("a78679")};
    private final ShapeRenderer renderer = new ShapeRenderer();
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font;
    private final Matrix4 hud = new Matrix4();
    private final LabFigure[] drawOrder = figures.clone();
    int selected = 1;
    boolean tactical;
    boolean showGrid = true;
    boolean showBounds = true;
    boolean showFootprint = true;
    boolean showPivots = true;
    private boolean crowded;
    private InputProcessor previousInput;
    private boolean active;

    final InputAdapter controls = new InputAdapter() {
        @Override public boolean keyDown(int key) {
            LabFigure figure = figures[selected];
            switch (key) {
                case Input.Keys.NUM_1: selected = 0; break;
                case Input.Keys.NUM_2: selected = 1; break;
                case Input.Keys.NUM_3: selected = 2; break;
                case Input.Keys.TAB: selected = (selected + 1) % figures.length; break;
                case Input.Keys.G: showGrid = !showGrid; break;
                case Input.Keys.B: showBounds = !showBounds; break;
                case Input.Keys.F: showFootprint = !showFootprint; break;
                case Input.Keys.P: showPivots = !showPivots; break;
                case Input.Keys.T:
                    tactical = !tactical;
                    if (tactical) for (LabFigure item : figures) item.snap(grid);
                    break;
                case Input.Keys.LEFT_BRACKET: figure.cycleSize(-1); break;
                case Input.Keys.RIGHT_BRACKET: figure.cycleSize(1); break;
                case Input.Keys.EQUALS: figure.setScale(figure.getScale() + .05f); break;
                case Input.Keys.MINUS: figure.setScale(figure.getScale() - .05f); break;
                case Input.Keys.Z: setZoom(camera.zoom * 1.1f); break;
                case Input.Keys.X: setZoom(camera.zoom / 1.1f); break;
                case Input.Keys.R: resetCamera(); break;
                case Input.Keys.C:
                    crowded = !crowded;
                    for (int i = 0; i < figures.length; i++) figures[i].moveTo(.5f + (i - 1) * (crowded ? 1 : 3), -.5f);
                    break;
                case Input.Keys.SPACE:
                    for (int i = 0; i < figures.length; i++) {
                        figures[i].cycleSize(i - figures[i].getSize().ordinal());
                        figures[i].setScale(1);
                        figures[i].moveTo(.5f + (i - 1) * 3, -.5f);
                    }
                    crowded = false;
                    resetCamera();
                    break;
                default:
                    if (!tactical) return false;
                    int dx = key == Input.Keys.D ? 1 : key == Input.Keys.A ? -1 : 0;
                    int dy = key == Input.Keys.W ? 1 : key == Input.Keys.S ? -1 : 0;
                    if (dx == 0 && dy == 0) return false;
                    figure.moveTo(figure.getX() + dx * PrototypeViewConfig.CELL_SIZE,
                        figure.getY() + dy * PrototypeViewConfig.CELL_SIZE);
            }
            return true;
        }

        @Override public boolean scrolled(float amountX, float amountY) {
            setZoom(camera.zoom * (float) Math.pow(1.1, amountY));
            return true;
        }

        @Override public boolean touchDown(int x, int y, int pointer, int button) {
            if (button != Input.Buttons.LEFT || !insideViewport(x, y)
                || y < 116 || y >= Gdx.graphics.getHeight() - 99) return false;
            Vector2 point = viewport.unproject(new Vector2(x, y));
            figures[selected].moveTo(point.x, point.y);
            if (tactical) figures[selected].snap(grid);
            return true;
        }

        @Override public boolean touchDragged(int x, int y, int pointer) {
            if (!Gdx.input.isButtonPressed(Input.Buttons.RIGHT)) return false;
            camera.position.add(-Gdx.input.getDeltaX() * unitsPerPixel(), Gdx.input.getDeltaY() * unitsPerPixel(), 0);
            camera.update();
            return true;
        }
    };

    public GraphicsLabScreen() {
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/DejaVuSans.ttf"));
        try {
            FreeTypeFontGenerator.FreeTypeFontParameter parameters = new FreeTypeFontGenerator.FreeTypeFontParameter();
            parameters.size = 14;
            parameters.characters = FreeTypeFontGenerator.DEFAULT_CHARS
                + "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдеёжзийклмнопрстуфхцчшщъыьэюя×";
            font = generator.generateFont(parameters);
        } finally { generator.dispose(); }
        resetCamera();
    }

    private boolean insideViewport(int x, int y) {
        int top = Gdx.graphics.getHeight() - viewport.getScreenY() - viewport.getScreenHeight();
        return x >= viewport.getScreenX() && x < viewport.getScreenX() + viewport.getScreenWidth()
            && y >= top && y < top + viewport.getScreenHeight();
    }

    void setZoom(float value) {
        camera.zoom = Math.max(.4f, Math.min(2.5f, value));
        camera.update();
    }

    private float unitsPerPixel() {
        return camera.viewportWidth * camera.zoom / Math.max(1, viewport.getScreenWidth());
    }

    private void resetCamera() {
        camera.zoom = 1;
        camera.position.set(.5f, .5f, 0);
        camera.update();
    }

    @Override public void show() {
        if (active) return;
        previousInput = Gdx.input.getInputProcessor();
        Gdx.input.setInputProcessor(controls);
        active = true;
    }

    @Override public void render(float delta) {
        float dt = Float.isFinite(delta) ? Math.max(0, Math.min(delta, .05f)) : 0;
        camera.position.add(axis(Input.Keys.LEFT, Input.Keys.RIGHT) * dt * 6 * camera.zoom,
            axis(Input.Keys.DOWN, Input.Keys.UP) * dt * 6 * camera.zoom, 0);
        if (!tactical) {
            LabFigure figure = figures[selected];
            figure.moveTo(figure.getX() + axis(Input.Keys.A, Input.Keys.D) * dt * 2,
                figure.getY() + axis(Input.Keys.S, Input.Keys.W) * dt * 2);
        }
        camera.update();
        ScreenUtils.clear(.10f, .16f, .12f, 1);
        viewport.apply();
        renderer.setProjectionMatrix(camera.combined);
        drawField();
        Arrays.sort(drawOrder, Comparator.comparingDouble(LabFigure::getY).reversed());
        for (LabFigure figure : drawOrder) drawWolf(figure);
        drawGuides();
        drawHud();
    }

    private float axis(int negative, int positive) {
        return (Gdx.input.isKeyPressed(positive) ? 1 : 0) - (Gdx.input.isKeyPressed(negative) ? 1 : 0);
    }

    private void drawField() {
        if (showFootprint) {
            renderer.begin(ShapeRenderer.ShapeType.Filled);
            for (int i = 0; i < figures.length; i++) {
                CellCoordinates cell = figures[i].cell(grid);
                renderer.setColor(i == selected ? Color.valueOf("52623a") : Color.valueOf("293e32"));
                renderer.rect((float) (grid.getOriginX() + cell.getX() * grid.getCellSize()),
                    (float) (grid.getOriginY() + cell.getY() * grid.getCellSize()),
                    PrototypeViewConfig.CELL_SIZE, PrototypeViewConfig.CELL_SIZE);
            }
            renderer.end();
        }
        if (!showGrid) return;
        float left = camera.position.x - camera.viewportWidth * camera.zoom / 2;
        float right = camera.position.x + camera.viewportWidth * camera.zoom / 2;
        float bottom = camera.position.y - camera.viewportHeight * camera.zoom / 2;
        float top = camera.position.y + camera.viewportHeight * camera.zoom / 2;
        float cell = PrototypeViewConfig.CELL_SIZE;
        renderer.begin(ShapeRenderer.ShapeType.Line);
        renderer.setColor(.23f, .30f, .24f, 1);
        for (float x = (float) Math.floor(left / cell) * cell; x <= right; x += cell) renderer.line(x, bottom, x, top);
        for (float y = (float) Math.floor(bottom / cell) * cell; y <= top; y += cell) renderer.line(left, y, right, y);
        renderer.end();
    }

    private void drawWolf(LabFigure figure) {
        int index = Arrays.asList(figures).indexOf(figure);
        renderer.begin(ShapeRenderer.ShapeType.Filled);
        renderer.setColor(.055f, .075f, .06f, 1);
        renderer.ellipse(figure.getX() - .34f, figure.getY() - .09f, .68f, .18f, 24);
        renderer.setColor(colors[index]);
        triangle(figure, 69, 82, 92, 66, 84, 96); // хвост
        rect(figure, 32, 58, 35, 39); // вертикальный корпус
        rect(figure, 30, 92, 14, 16); // короткие лапы до ground line
        rect(figure, 54, 92, 14, 16);
        triangle(figure, 18, 37, 20, 7, 40, 29); // большие уши
        triangle(figure, 57, 29, 77, 7, 80, 39);
        rect(figure, 18, 28, 62, 38); // большая голова
        renderer.setColor(.30f, .22f, .20f, 1);
        triangle(figure, 24, 29, 24, 16, 33, 28);
        triangle(figure, 64, 28, 74, 16, 74, 29);
        renderer.setColor(.15f, .12f, .11f, 1);
        rect(figure, 29, 39, 10, 8);
        rect(figure, 60, 39, 10, 8);
        rect(figure, 30, 51, 39, 14); // крупная пасть
        rect(figure, 44, 47, 11, 8);
        renderer.setColor(.94f, .84f, .61f, 1);
        rect(figure, 32, 40, 4, 4);
        rect(figure, 63, 40, 4, 4);
        triangle(figure, 34, 52, 40, 52, 37, 59);
        triangle(figure, 58, 52, 64, 52, 61, 59);
        renderer.end();
    }

    // Геометрическая заглушка задана в локальном Medium-контейнере 96×128.
    private float wx(LabFigure f, float x) { return f.worldX(x * f.getSize().width / 96); }
    private float wy(LabFigure f, float y) { return f.worldY(y * f.getSize().height / 128); }
    private void rect(LabFigure f, float x, float y, float width, float height) {
        renderer.rect(wx(f, x), wy(f, y + height), wx(f, x + width) - wx(f, x), wy(f, y) - wy(f, y + height));
    }
    private void triangle(LabFigure f, float x1, float y1, float x2, float y2, float x3, float y3) {
        renderer.triangle(wx(f, x1), wy(f, y1), wx(f, x2), wy(f, y2), wx(f, x3), wy(f, y3));
    }

    private void drawGuides() {
        renderer.begin(ShapeRenderer.ShapeType.Line);
        for (int i = 0; i < figures.length; i++) {
            LabFigure f = figures[i];
            Rectangle bounds = f.bounds();
            if (showBounds) {
                renderer.setColor(Color.SKY);
                renderer.rect(bounds.x, bounds.y, bounds.width, bounds.height);
            }
            if (showFootprint) {
                CellCoordinates cell = f.cell(grid);
                renderer.setColor(i == selected ? Color.YELLOW : Color.OLIVE);
                renderer.rect(cell.getX() * PrototypeViewConfig.CELL_SIZE, cell.getY() * PrototypeViewConfig.CELL_SIZE,
                    PrototypeViewConfig.CELL_SIZE, PrototypeViewConfig.CELL_SIZE);
            }
            if (showPivots) {
                renderer.setColor(Color.ORANGE);
                renderer.line(bounds.x, f.getY(), bounds.x + bounds.width, f.getY());
                renderer.circle(f.getX(), f.getY(), .11f, 20); // world ground anchor
                renderer.setColor(Color.MAGENTA);
                renderer.line(f.getX() - .17f, f.getY(), f.getX() + .17f, f.getY());
                renderer.line(f.getX(), f.getY() - .17f, f.getX(), f.getY() + .17f); // root pivot
            }
        }
        renderer.end();
    }

    private void drawHud() {
        int width = Gdx.graphics.getWidth(), height = Gdx.graphics.getHeight();
        font.getData().setScale(Math.min(1f, width / 640f));
        HdpiUtils.glViewport(0, 0, width, height);
        hud.setToOrtho2D(0, 0, width, height);
        renderer.setProjectionMatrix(hud);
        renderer.begin(ShapeRenderer.ShapeType.Filled);
        renderer.setColor(.045f, .065f, .055f, 1);
        renderer.rect(0, height - 116, width, 116);
        renderer.rect(0, 0, width, 99);
        renderer.end();
        batch.setProjectionMatrix(hud);
        batch.begin();
        font.setColor(Color.WHITE);
        font.draw(batch, String.format(Locale.ROOT, "Graphics Lab | %s | zoom %.2f | %.1f x %.1f cells",
            tactical ? "Tactical preview" : "Exploration", camera.zoom,
            camera.viewportWidth * camera.zoom / PrototypeViewConfig.CELL_SIZE,
            camera.viewportHeight * camera.zoom / PrototypeViewConfig.CELL_SIZE), 8, height - 8);
        for (int i = 0; i < figures.length; i++) {
            LabFigure f = figures[i];
            Rectangle bounds = f.bounds();
            font.setColor(i == selected ? Color.YELLOW : colors[i]);
            font.draw(batch, String.format(Locale.ROOT, "%s%d %s | %.2fx | %.0fx%.0f px | cell (%d,%d)",
                i == selected ? "> " : "  ", i + 1, f.getSize(), f.getScale(),
                bounds.width / unitsPerPixel(), bounds.height / unitsPerPixel(),
                f.cell(grid).getX(), f.cell(grid).getY()), 8, height - 32 - i * 20);
        }
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch, "1/2/3: выбор  +/-: scale  [/]: размер  C: соседние клетки", 8, 91);
        font.draw(batch, "WASD/ЛКМ: движение  Стрелки/ПКМ: камера  Колесо/Z/X: zoom", 8, 72);
        font.draw(batch, "T: режим  R: камера 1x  Space: сброс фигур и камеры", 8, 53);
        font.draw(batch, String.format("G: сетка %s  F: клетка %s  B: bounds %s  P: pivot %s",
            on(showGrid), on(showFootprint), on(showBounds), on(showPivots)), 8, 34);
        font.draw(batch, "Голубой: bounds  Оранжевый: ground  Розовый: root pivot", 8, 15);
        batch.end();
    }

    private String on(boolean value) { return value ? "on" : "off"; }

    @Override public void resize(int width, int height) {
        if (width > 0 && height > 0) viewport.update(width, height, false);
    }

    @Override public void hide() {
        if (active && Gdx.input.getInputProcessor() == controls) Gdx.input.setInputProcessor(previousInput);
        active = false;
        previousInput = null;
    }

    @Override public void dispose() {
        hide();
        renderer.dispose();
        batch.dispose();
        font.dispose();
    }
}
