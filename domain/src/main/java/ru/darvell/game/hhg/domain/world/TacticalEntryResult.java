package ru.darvell.game.hhg.domain.world;

/** План содержит только проверенные значения и не меняет участников. */
public final class TacticalEntryResult {
    public enum Outcome { SUCCESS, NO_VALID_PLACEMENT, INVALID_INPUT, ALREADY_TACTICAL }

    private final Outcome outcome;
    private final TacticalPlacement placement;
    private final WorldPosition hunterCenter;
    private final WorldPosition creatureCenter;

    private TacticalEntryResult(Outcome outcome, TacticalPlacement placement,
                                WorldPosition hunterCenter, WorldPosition creatureCenter) {
        this.outcome = outcome;
        this.placement = placement;
        this.hunterCenter = hunterCenter;
        this.creatureCenter = creatureCenter;
    }

    static TacticalEntryResult failure(Outcome outcome) {
        return new TacticalEntryResult(outcome, null, null, null);
    }

    static TacticalEntryResult success(TacticalPlacement placement, WorldPosition hunter,
                                       WorldPosition creature) {
        return new TacticalEntryResult(Outcome.SUCCESS, placement, hunter, creature);
    }

    public Outcome getOutcome() { return outcome; }
    public TacticalPlacement getPlacement() { return placement; }
    public WorldPosition getHunterCenter() { return hunterCenter; }
    public WorldPosition getCreatureCenter() { return creatureCenter; }
}
