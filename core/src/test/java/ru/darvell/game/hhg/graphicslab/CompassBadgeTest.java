package ru.darvell.game.hhg.graphicslab;

import org.junit.jupiter.api.Test;
import com.badlogic.gdx.math.Rectangle;
import ru.darvell.game.hhg.domain.tactical.TacticalGrid;
import static org.junit.jupiter.api.Assertions.*;

class CompassBadgeTest {
    private static final float EPSILON = .00001f;

    @Test void tacticalGateRejectsExplorationForEveryIndicator() {
        for (FacingIndicator.Kind kind : FacingIndicator.Kind.values()) {
            assertFalse(CompassBadge.isVisible(false, kind));
            assertEquals(kind == FacingIndicator.Kind.COMPASS_BADGE, CompassBadge.isVisible(true, kind));
        }
    }

    @Test void threePositionsStayOutsideActualVisualBounds() {
        LabFigure figure = new LabFigure(LabFigure.Size.MEDIUM, .5f, -.5f);
        for (int size = 0; size < 3; size++) {
            figure.cycleSize(1);
            for (float scale : new float[] {.25f, 1, 2.5f}) {
                figure.setScale(scale);
                Rectangle visual = figure.bounds();
                Rectangle right = CompassBadge.layout(visual, 1, 1f / 32, CompassBadge.Position.TOP_RIGHT);
                Rectangle left = CompassBadge.layout(visual, 1, 1f / 32, CompassBadge.Position.TOP_LEFT);
                Rectangle above = CompassBadge.layout(visual, 1, 1f / 32, CompassBadge.Position.ABOVE_HEAD);
                assertEquals(visual.x + visual.width + .08f, right.x, EPSILON);
                assertEquals(visual.x - .08f, left.x + left.width, EPSILON);
                assertEquals(visual.y + visual.height + .08f, above.y, EPSILON);
                assertEquals(visual.x + visual.width / 2 + .2f, above.x + above.width / 2, EPSILON);
                assertEquals(right.y, left.y);
                assertEquals(right.y, above.y);
                assertEquals(.375f, right.width, EPSILON);
                assertFalse(visual.overlaps(right));
                assertFalse(visual.overlaps(left));
                assertFalse(visual.overlaps(above));
            }
        }
    }

    @Test void cameraZoomAndViewportUseScreenLimitsWithoutChangingFigureScale() {
        Rectangle visual = new Rectangle(0, 0, 1.5f, 2);
        // 32 px/cell при 640×480, 40 при 1000×600, 60 при 1600×900.
        for (float pixelsPerCell : new float[] {32, 40, 60}) {
            for (float zoom : new float[] {.75f, 1, 1.5f}) {
                float unitsPerPixel = zoom / pixelsPerCell;
                Rectangle badge = CompassBadge.layout(visual, 1, unitsPerPixel, CompassBadge.Position.TOP_RIGHT);
                float screenDiameter = badge.width / unitsPerPixel;
                assertEquals(Math.max(8, Math.min(16, .375f / unitsPerPixel)), screenDiameter, .0001f);
                assertEquals(1.58f, badge.x, EPSILON);
                assertEquals(2.08f, badge.y, EPSILON);
            }
        }
    }

    @Test void facingAndMovementLeaveFootprintAndBoundsUnderTheirExistingRules() {
        LabFigure figure = new LabFigure(LabFigure.Size.MEDIUM, -.5f, .5f);
        TacticalGrid grid = new TacticalGrid(0, 0, 1);
        Rectangle visual = figure.bounds();
        Rectangle first = CompassBadge.layout(visual, 1, 1f / 32, CompassBadge.Position.TOP_RIGHT);
        for (Facing facing : Facing.values()) {
            figure.resetFacing();
            figure.turn(facing.ordinal());
            assertEquals(first, CompassBadge.layout(figure.bounds(), 1, 1f / 32, CompassBadge.Position.TOP_RIGHT));
            assertEquals(visual, figure.bounds());
            assertEquals(-1, figure.cell(grid).getX());
        }
        figure.moveTo(.5f, .5f);
        Rectangle moved = CompassBadge.layout(figure.bounds(), 1, 1f / 32, CompassBadge.Position.TOP_RIGHT);
        assertEquals(first.x + 1, moved.x, EPSILON);
        assertEquals(first.y, moved.y);
        assertEquals(Facing.NW, figure.getFacing());
        assertEquals(0, figure.cell(grid).getX());
    }

    @Test void positionCyclesWithoutCreatingAnotherFacingModel() {
        CompassBadge badge = new CompassBadge();
        assertEquals(CompassBadge.Position.TOP_RIGHT, badge.getPosition());
        badge.nextPosition();
        assertEquals(CompassBadge.Position.TOP_LEFT, badge.getPosition());
        badge.nextPosition();
        assertEquals(CompassBadge.Position.ABOVE_HEAD, badge.getPosition());
        badge.nextPosition();
        assertEquals(CompassBadge.Position.TOP_RIGHT, badge.getPosition());
    }
}
