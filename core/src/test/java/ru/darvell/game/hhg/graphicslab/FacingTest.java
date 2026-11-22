package ru.darvell.game.hhg.graphicslab;

import org.junit.jupiter.api.Test;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import ru.darvell.game.hhg.domain.tactical.TacticalGrid;
import static org.junit.jupiter.api.Assertions.*;

class FacingTest {
    @Test void eightDirectionsUseClockwiseNorthOriginAndWorldAngles() {
        int[] worldAngles = {90, 45, 0, 315, 270, 225, 180, 135};
        float diagonal = (float) Math.sqrt(.5);
        float[][] vectors = {{0,1},{diagonal,diagonal},{1,0},{diagonal,-diagonal},
            {0,-1},{-diagonal,-diagonal},{-1,0},{-diagonal,diagonal}};
        for (Facing facing : Facing.values()) {
            int index = facing.ordinal();
            assertEquals(index * 45, facing.clockwiseDegrees());
            assertEquals(worldAngles[index], facing.worldDegrees());
            assertEquals(vectors[index][0], MathUtils.cosDeg(facing.worldDegrees()), .001);
            assertEquals(vectors[index][1], MathUtils.sinDeg(facing.worldDegrees()), .001);
            assertEquals(facing, Facing.N.turn(index));
            assertEquals(facing, facing.turn(8));
        }
        assertEquals(Facing.NW, Facing.N.turn(-1));
        assertEquals(Facing.N, Facing.NW.turn(1));
        assertEquals(Facing.N, Facing.N.turn(Integer.MIN_VALUE));
    }

    @Test void movingEastSnappingAndScalingDoNotChangeFacing() {
        LabFigure f = new LabFigure(LabFigure.Size.MEDIUM, .5f, .5f);
        for (Facing direction : Facing.values()) {
            f.resetFacing();
            f.turn(direction.ordinal());
            f.moveTo(f.getX() + 1, f.getY());
            f.snap(new TacticalGrid(0, 0, 1));
            f.setScale(1.7f);
            f.cycleSize(1);
            assertEquals(direction, f.getFacing());
        }
        f.resetFacing();
        assertEquals(Facing.N, f.getFacing());
    }

    @Test void turningDoesNotChangeAnchorCellOrVisualBounds() {
        LabFigure f = new LabFigure(LabFigure.Size.MEDIUM, -.5f, 1.5f);
        TacticalGrid grid = new TacticalGrid(0, 0, 1);
        Rectangle bounds = f.bounds();
        for (int i = 0; i < 8; i++) {
            f.turn(1);
            assertEquals(bounds, f.bounds());
            assertEquals(-.5f, f.getX());
            assertEquals(1.5f, f.getY());
            assertEquals(-1, f.cell(grid).getX());
            assertEquals(1, f.cell(grid).getY());
        }
    }

    @Test void sectorSettingsAreBoundedAndModesIncludeOff() {
        FacingIndicator indicator = new FacingIndicator();
        assertEquals(FacingIndicator.Kind.OFF, indicator.getKind());
        for (FacingIndicator.Kind expected : FacingIndicator.Kind.values()) {
            indicator.nextKind();
            assertEquals(expected, indicator.getKind());
        }
        assertEquals(90, indicator.getSectorAngle());
        assertEquals(.9f, indicator.getSectorRadius());
        indicator.setSectorAngle(0);
        indicator.setSectorRadius(10);
        assertEquals(15, indicator.getSectorAngle());
        assertEquals(2, indicator.getSectorRadius());
        assertThrows(IllegalArgumentException.class, () -> indicator.setSectorAngle(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> indicator.setSectorRadius(Float.POSITIVE_INFINITY));
    }
}
