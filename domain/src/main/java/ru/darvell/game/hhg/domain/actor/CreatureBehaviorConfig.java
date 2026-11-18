package ru.darvell.game.hhg.domain.actor;

/** Неизменяемые настройки движения и восприятия одного существа. */
public final class CreatureBehaviorConfig {
    private final double patrolSpeed;
    private final double chaseSpeed;
    private final double searchSpeed;
    private final double returnSpeed;
    private final double searchSeconds;
    private final double detectionRadius;
    private final double engagementRange;
    private final double pauseSeconds;

    public CreatureBehaviorConfig(double patrolSpeed, double chaseSpeed, double searchSpeed, double returnSpeed,
                                  double searchSeconds, double detectionRadius, double engagementRange,
                                  double pauseSeconds) {
        requirePositive(patrolSpeed, "Patrol speed");
        requirePositive(chaseSpeed, "Chase speed");
        requirePositive(searchSpeed, "Search speed");
        requirePositive(returnSpeed, "Return speed");
        requirePositive(detectionRadius, "Detection radius");
        requirePositive(engagementRange, "Engagement range");
        if (engagementRange >= detectionRadius) {
            throw new IllegalArgumentException("Engagement range must be smaller than detection radius");
        }
        requirePositive(pauseSeconds, "Waypoint pause");
        if (!Double.isFinite(searchSeconds) || searchSeconds < 0.0) {
            throw new IllegalArgumentException("Search duration must be finite and non-negative");
        }
        this.patrolSpeed = patrolSpeed;
        this.chaseSpeed = chaseSpeed;
        this.searchSpeed = searchSpeed;
        this.returnSpeed = returnSpeed;
        this.searchSeconds = searchSeconds;
        this.detectionRadius = detectionRadius;
        this.engagementRange = engagementRange;
        this.pauseSeconds = pauseSeconds;
    }

    public double getPatrolSpeed() { return patrolSpeed; }
    public double getChaseSpeed() { return chaseSpeed; }
    public double getSearchSpeed() { return searchSpeed; }
    public double getReturnSpeed() { return returnSpeed; }
    public double getSearchSeconds() { return searchSeconds; }
    public double getDetectionRadius() { return detectionRadius; }
    public double getEngagementRange() { return engagementRange; }
    public double getPauseSeconds() { return pauseSeconds; }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }
}
