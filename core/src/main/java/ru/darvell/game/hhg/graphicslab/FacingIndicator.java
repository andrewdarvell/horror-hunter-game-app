package ru.darvell.game.hhg.graphicslab;

import java.nio.IntBuffer;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.BufferUtils;

/** Только Lab. Геометрия в долях клетки; visual bounds и scale фигуры не читаются. */
public final class FacingIndicator {
    public enum Kind { ARROW, NOTCH, SECTOR, COMPASS_BADGE, OFF }

    private static final Color DARK = Color.valueOf("172019");
    private static final Color LIGHT = Color.valueOf("ffdc80");
    private static final Color WOOD_RIM = Color.valueOf("493424");
    private static final Color WOOD = Color.valueOf("ba8e53");
    private final IntBuffer blendValue = BufferUtils.newIntBuffer(1);
    private Kind kind = Kind.OFF;
    private float sectorAngle = 90;
    private float sectorRadius = .9f;

    public Kind getKind() { return kind; }
    public void nextKind() { kind = Kind.values()[(kind.ordinal() + 1) % Kind.values().length]; }
    public float getSectorAngle() { return sectorAngle; }
    public float getSectorRadius() { return sectorRadius; }

    public void setSectorAngle(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Sector angle must be finite");
        sectorAngle = MathUtils.clamp(value, 15, 180);
    }

    public void setSectorRadius(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Sector radius must be finite");
        sectorRadius = MathUtils.clamp(value, .25f, 2f);
    }

    /** Общий ground pass до всех фигур: прозрачный сектор не перекрашивает соседей. */
    public void drawSector(ShapeRenderer renderer, LabFigure figure, float cellSize) {
        if (kind != Kind.SECTOR) return;
        boolean blending = Gdx.gl.glIsEnabled(GL20.GL_BLEND);
        int srcRgb = blend(GL20.GL_BLEND_SRC_RGB), dstRgb = blend(GL20.GL_BLEND_DST_RGB);
        int srcAlpha = blend(GL20.GL_BLEND_SRC_ALPHA), dstAlpha = blend(GL20.GL_BLEND_DST_ALPHA);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        float start = figure.getFacing().worldDegrees() - sectorAngle / 2;
        int segments = Math.max(8, Math.round(sectorAngle / 5));
        try {
            renderer.begin(ShapeRenderer.ShapeType.Filled);
            renderer.setColor(1f, .76f, .3f, .23f);
            renderer.arc(figure.getX(), figure.getY(), sectorRadius * cellSize, start, sectorAngle, segments);
            renderer.end();
            renderer.begin(ShapeRenderer.ShapeType.Line);
            renderer.setColor(1f, .85f, .5f, .7f);
            renderer.arc(figure.getX(), figure.getY(), sectorRadius * cellSize, start, sectorAngle, segments);
            renderer.end();
        } finally {
            Gdx.gl.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (!blending) Gdx.gl.glDisable(GL20.GL_BLEND);
        }
    }

    private int blend(int name) {
        blendValue.clear();
        Gdx.gl.glGetIntegerv(name, blendValue);
        return blendValue.get(0);
    }

    /** Слой земли в существующем Y-порядке. Arrow никогда не рисуется поверх тела. */
    public void drawBase(ShapeRenderer renderer, LabFigure figure, float cellSize) {
        if (kind != Kind.ARROW && kind != Kind.NOTCH) return;
        renderer.begin(ShapeRenderer.ShapeType.Filled);
        if (kind == Kind.ARROW) {
            arrow(renderer, figure, cellSize, .025f, DARK);
            arrow(renderer, figure, cellSize, 0, LIGHT);
        } else {
            renderer.setColor(WOOD_RIM);
            renderer.ellipse(figure.getX() - .4f * cellSize, figure.getY() - .22f * cellSize,
                .8f * cellSize, .44f * cellSize, 32);
            renderer.setColor(WOOD);
            renderer.ellipse(figure.getX() - .36f * cellSize, figure.getY() - .18f * cellSize,
                .72f * cellSize, .36f * cellSize, 32);
        }
        renderer.end();
    }

    private void arrow(ShapeRenderer renderer, LabFigure figure, float cellSize, float border, Color color) {
        float angle = figure.getFacing().worldDegrees();
        float dx = MathUtils.cosDeg(angle), dy = MathUtils.sinDeg(angle);
        float x = figure.getX(), y = figure.getY();
        renderer.setColor(color);
        renderer.rectLine(x + dx * (.44f - border) * cellSize, y + dy * (.44f - border) * cellSize,
            x + dx * .66f * cellSize, y + dy * .66f * cellSize, (.09f + 2 * border) * cellSize);
        pointer(renderer, x, y, dx, dy, (.88f + border) * cellSize,
            (.63f - border) * cellSize, (.14f + border) * cellSize, 1);
    }

    /** Светлая кромка подставки поверх низа своей фигуры, до следующей фигуры по Y. */
    public void drawNotch(ShapeRenderer renderer, LabFigure figure, float cellSize) {
        if (kind != Kind.NOTCH) return;
        float angle = figure.getFacing().worldDegrees();
        float dx = MathUtils.cosDeg(angle), dy = MathUtils.sinDeg(angle);
        renderer.begin(ShapeRenderer.ShapeType.Filled);
        renderer.setColor(WOOD_RIM);
        pointer(renderer, figure.getX(), figure.getY(), dx, dy, .50f * cellSize, .20f * cellSize, .17f * cellSize, .55f);
        renderer.setColor(LIGHT);
        pointer(renderer, figure.getX(), figure.getY(), dx, dy, .47f * cellSize, .23f * cellSize, .12f * cellSize, .55f);
        renderer.end();
    }

    private void pointer(ShapeRenderer renderer, float x, float y, float dx, float dy,
                         float tip, float base, float halfWidth, float flattenY) {
        renderer.triangle(x + dx * tip, y + dy * tip * flattenY,
            x + dx * base - dy * halfWidth, y + (dy * base + dx * halfWidth) * flattenY,
            x + dx * base + dy * halfWidth, y + (dy * base - dx * halfWidth) * flattenY);
    }
}
