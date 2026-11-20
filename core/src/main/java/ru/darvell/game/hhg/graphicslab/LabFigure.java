package ru.darvell.game.hhg.graphicslab;

import com.badlogic.gdx.math.Rectangle;
import ru.darvell.game.hhg.PrototypeViewConfig;
import ru.darvell.game.hhg.domain.tactical.CellCoordinates;
import ru.darvell.game.hhg.domain.tactical.TacticalGrid;
import ru.darvell.game.hhg.domain.world.WorldPosition;

/** Только визуальные параметры; не участвует в коллизиях или боевой симуляции. */
public final class LabFigure {
    public static final float REFERENCE_CELL_PIXELS = 64f;

    public enum Size {
        SMALL(80, 96), MEDIUM(96, 128), LARGE(128, 160);

        public final float width;
        public final float height;

        Size(float width, float height) {
            this.width = width;
            this.height = height;
        }
    }

    private Size size;
    private float scale = 1f;
    private float x;
    private float y;
    private Facing facing = Facing.N;

    public LabFigure(Size size, float x, float y) {
        this.size = size;
        moveTo(x, y);
    }

    public Size getSize() { return size; }
    public float getScale() { return scale; }
    public float getX() { return x; }
    public float getY() { return y; }
    public Facing getFacing() { return facing; }
    public void turn(int clockwiseSteps) { facing = facing.turn(clockwiseSteps); }
    public void resetFacing() { facing = Facing.N; }
    public float getPivotX() { return size.width / 2f; }
    public float getPivotY() { return size.height * 108f / 128f; }

    public void setScale(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Scale must be finite");
        scale = Math.max(.25f, Math.min(2.5f, value));
    }

    public void cycleSize(int direction) {
        Size[] sizes = Size.values();
        size = sizes[Math.floorMod(size.ordinal() + direction, sizes.length)];
    }

    public void moveTo(float x, float y) {
        if (!Float.isFinite(x) || !Float.isFinite(y)) throw new IllegalArgumentException("Anchor must be finite");
        this.x = x;
        this.y = y;
    }

    public float worldX(float assetX) {
        return x + (assetX - getPivotX()) * worldScale();
    }

    public float worldY(float assetY) {
        return y + (getPivotY() - assetY) * worldScale();
    }

    private float worldScale() {
        return scale * PrototypeViewConfig.CELL_SIZE / REFERENCE_CELL_PIXELS;
    }

    public Rectangle bounds() {
        return new Rectangle(worldX(0), worldY(size.height), size.width * worldScale(), size.height * worldScale());
    }

    public CellCoordinates cell(TacticalGrid grid) {
        return grid.worldToCell(new WorldPosition(x, y));
    }

    public void snap(TacticalGrid grid) {
        WorldPosition center = grid.cellToWorldCenter(cell(grid));
        moveTo((float) center.getX(), (float) center.getY());
    }
}
