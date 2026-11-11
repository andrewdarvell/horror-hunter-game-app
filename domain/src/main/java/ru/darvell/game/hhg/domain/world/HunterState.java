package ru.darvell.game.hhg.domain.world;

/** Минимальное состояние охотника и правило свободного перемещения. */
public final class HunterState {
    private WorldPosition position;
    private final double movementSpeed;

    public HunterState(WorldPosition position, double movementSpeed) {
        if (position == null) {
            throw new IllegalArgumentException("Position must not be null");
        }
        if (!Double.isFinite(movementSpeed) || movementSpeed <= 0.0) {
            throw new IllegalArgumentException("Movement speed must be finite and positive");
        }
        this.position = position;
        this.movementSpeed = movementSpeed;
    }

    public WorldPosition getPosition() {
        return position;
    }

    public double getMovementSpeed() {
        return movementSpeed;
    }

    public void move(double directionX, double directionY, double deltaSeconds) {
        if (!Double.isFinite(directionX) || !Double.isFinite(directionY)) {
            throw new IllegalArgumentException("Movement direction must be finite");
        }
        if (!Double.isFinite(deltaSeconds) || deltaSeconds < 0.0) {
            throw new IllegalArgumentException("Delta time must be finite and non-negative");
        }

        double lengthSquared = directionX * directionX + directionY * directionY;
        if (lengthSquared == 0.0 || deltaSeconds == 0.0) {
            return;
        }

        if (lengthSquared > 1.0) {
            double inverseLength = 1.0 / Math.sqrt(lengthSquared);
            directionX *= inverseLength;
            directionY *= inverseLength;
        }

        double distance = movementSpeed * deltaSeconds;
        position = position.translate(directionX * distance, directionY * distance);
    }
}
