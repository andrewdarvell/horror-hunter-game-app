package ru.darvell.game.hhg.graphicslab;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;

/** UI-маркер в world projection, изолированный внутри Lab. */
public final class CompassBadge {
    public enum Position { TOP_RIGHT, TOP_LEFT, ABOVE_HEAD }

    private static final float CELL_DIAMETER = .375f;
    private static final float MIN_SCREEN_DIAMETER = 8;
    private static final float MAX_SCREEN_DIAMETER = 16;
    private static final float CELL_GAP = .08f;
    private static final Color OUTLINE = Color.valueOf("172019");
    private static final Color BACKGROUND = Color.valueOf("51564a");
    private static final Color OCHRE = Color.valueOf("c0a16b");
    private Position position = Position.TOP_RIGHT;

    public Position getPosition() { return position; }
    public void nextPosition() { position = Position.values()[(position.ordinal() + 1) % Position.values().length]; }

    public static boolean isVisible(boolean tactical, FacingIndicator.Kind kind) {
        return tactical && kind == FacingIndicator.Kind.COMPASS_BADGE;
    }

    /** Чистая геометрия; logical footprint не участвует в вычислении позиции. */
    public static Rectangle layout(Rectangle visualBounds, float cellSize, float unitsPerPixel, Position position) {
        if (visualBounds == null || position == null || !Float.isFinite(cellSize) || cellSize <= 0
            || !Float.isFinite(unitsPerPixel) || unitsPerPixel <= 0) {
            throw new IllegalArgumentException("Bounds, position and positive finite scales are required");
        }
        float diameter = MathUtils.clamp(CELL_DIAMETER * cellSize,
            MIN_SCREEN_DIAMETER * unitsPerPixel, MAX_SCREEN_DIAMETER * unitsPerPixel);
        float radius = diameter / 2;
        float gap = CELL_GAP * cellSize;
        float centerX = position == Position.TOP_RIGHT ? visualBounds.x + visualBounds.width + radius + gap
            : position == Position.TOP_LEFT ? visualBounds.x - radius - gap
            : visualBounds.x + visualBounds.width / 2 + .2f * cellSize;
        return new Rectangle(centerX - radius, visualBounds.y + visualBounds.height + gap, diameter, diameter);
    }

    /** Gate находится в самом draw: выбор, hover и перемещение не обходят tactical-only. */
    public void draw(ShapeRenderer renderer, LabFigure figure, FacingIndicator.Kind kind, boolean tactical,
                     float cellSize, float unitsPerPixel) {
        if (!isVisible(tactical, kind)) return;
        Rectangle bounds = layout(figure.bounds(), cellSize, unitsPerPixel, position);
        float radius = bounds.width / 2;
        float x = bounds.x + radius, y = bounds.y + radius;
        float angle = figure.getFacing().worldDegrees();
        float dx = MathUtils.cosDeg(angle), dy = MathUtils.sinDeg(angle);
        renderer.begin(ShapeRenderer.ShapeType.Filled);
        renderer.setColor(OUTLINE);
        renderer.circle(x, y, radius, 24);
        renderer.setColor(BACKGROUND);
        renderer.circle(x, y, radius * .82f, 24);
        renderer.setColor(OUTLINE);
        chevron(renderer, x, y, dx, dy, radius * 1.15f);
        renderer.setColor(OCHRE);
        chevron(renderer, x, y, dx, dy, radius);
        renderer.end();
    }

    private void chevron(ShapeRenderer renderer, float x, float y, float dx, float dy, float radius) {
        float tipX = x + dx * .74f * radius, tipY = y + dy * .74f * radius;
        float tailX = x - dx * .16f * radius, tailY = y - dy * .16f * radius;
        float baseX = x - dx * .48f * radius, baseY = y - dy * .48f * radius;
        renderer.triangle(tipX, tipY, baseX - dy * .55f * radius, baseY + dx * .55f * radius, tailX, tailY);
        renderer.triangle(tipX, tipY, tailX, tailY, baseX + dy * .55f * radius, baseY - dx * .55f * radius);
    }
}
