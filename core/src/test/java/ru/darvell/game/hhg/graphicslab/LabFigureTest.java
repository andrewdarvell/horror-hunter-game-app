package ru.darvell.game.hhg.graphicslab;

import org.junit.jupiter.api.Test;
import com.badlogic.gdx.math.Rectangle;
import ru.darvell.game.hhg.PrototypeViewConfig;
import ru.darvell.game.hhg.domain.tactical.TacticalGrid;

import static org.junit.jupiter.api.Assertions.*;

class LabFigureTest {
    private final TacticalGrid grid = new TacticalGrid(0, 0, PrototypeViewConfig.CELL_SIZE);

    @Test void mediumUsesGroundAnchorWithTransparentMarginBelowFeet() {
        LabFigure figure = new LabFigure(LabFigure.Size.MEDIUM, .5f, .5f);
        assertEquals(48, figure.getPivotX());
        assertEquals(108, figure.getPivotY());
        assertEquals(.5f, figure.worldX(48));
        assertEquals(.5f, figure.worldY(108));
        Rectangle bounds = figure.bounds();
        assertEquals(1.5f, bounds.width);
        assertEquals(2f, bounds.height);
        assertEquals(.1875f, bounds.y);
        assertEquals(2.1875f, bounds.y + bounds.height);
    }

    @Test void visualChangesKeepAnchorAndLogicalCellIncludingNegativeCoordinates() {
        LabFigure figure = new LabFigure(LabFigure.Size.SMALL, -.2f, -1.2f);
        for (int i = 0; i < 3; i++) {
            figure.cycleSize(1);
            figure.setScale(2);
            assertEquals(-1, figure.cell(grid).getX());
            assertEquals(-2, figure.cell(grid).getY());
            assertEquals(-.2f, figure.worldX(figure.getPivotX()));
            assertEquals(-1.2f, figure.worldY(figure.getPivotY()));
        }
        figure.snap(grid);
        assertEquals(-.5f, figure.getX());
        assertEquals(-1.5f, figure.getY());
    }

    @Test void scaleIsBoundedAndRejectsInvalidInput() {
        LabFigure figure = new LabFigure(LabFigure.Size.LARGE, 0, 0);
        figure.setScale(-1);
        assertEquals(.25f, figure.getScale());
        figure.setScale(100);
        assertEquals(2.5f, figure.getScale());
        assertThrows(IllegalArgumentException.class, () -> figure.setScale(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> figure.moveTo(Float.POSITIVE_INFINITY, 0));
    }
}
