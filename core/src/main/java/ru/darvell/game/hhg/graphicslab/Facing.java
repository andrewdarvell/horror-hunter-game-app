package ru.darvell.game.hhg.graphicslab;

/** Направление визуального эксперимента. X вправо, Y вверх; N — нулевое. */
public enum Facing {
    N, NE, E, SE, S, SW, W, NW;

    public int clockwiseDegrees() { return ordinal() * 45; }

    /** Угол ShapeRenderer: ноль вправо, положительный поворот против часовой стрелки. */
    public int worldDegrees() { return Math.floorMod(90 - clockwiseDegrees(), 360); }

    public Facing turn(int clockwiseSteps) {
        Facing[] directions = values();
        return directions[Math.floorMod(ordinal() + Math.floorMod(clockwiseSteps, directions.length), directions.length)];
    }
}
