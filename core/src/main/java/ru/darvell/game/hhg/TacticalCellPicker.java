package ru.darvell.game.hhg;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;
import ru.darvell.game.hhg.domain.tactical.CellCoordinates;
import ru.darvell.game.hhg.domain.tactical.TacticalGrid;
import ru.darvell.game.hhg.domain.world.WorldPosition;

/** Выбор клетки в screen-единицах input, до преобразования отсекает поля. */
final class TacticalCellPicker {
    static CellCoordinates pick(Viewport viewport, TacticalGrid grid, int x, int y,
                                int width, int height, boolean ready) {
        if (!ready || width <= 0 || height <= 0
            || viewport.getScreenWidth() <= 0 || viewport.getScreenHeight() <= 0) return null;
        long left = viewport.getScreenX();
        long top = (long) height - viewport.getScreenY() - viewport.getScreenHeight();
        if (x < 0 || y < 0 || x >= width || y >= height
            || x < left || x >= left + viewport.getScreenWidth()
            || y < top || y >= top + viewport.getScreenHeight()) return null;
        Vector2 world = viewport.unproject(new Vector2(x, y));
        if (!Float.isFinite(world.x) || !Float.isFinite(world.y)) return null;
        double cellX = Math.floor((world.x - grid.getOriginX()) / grid.getCellSize());
        double cellY = Math.floor((world.y - grid.getOriginY()) / grid.getCellSize());
        if (cellX < Integer.MIN_VALUE || cellX > Integer.MAX_VALUE
            || cellY < Integer.MIN_VALUE || cellY > Integer.MAX_VALUE
            || !Double.isFinite(cellX) || !Double.isFinite(cellY)) return null;
        return grid.worldToCell(new WorldPosition(world.x, world.y));
    }
}
