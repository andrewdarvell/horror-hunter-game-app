package ru.darvell.game.hhg.domain.combat.outcome;

/** Идентификатор исхода и его конечный неотрицательный вес. */
public final class WeightedOutcome {
    private final String id;
    private final double weight;

    public WeightedOutcome(String id, double weight) {
        if (id == null || id.isEmpty() || !id.equals(id.trim())) {
            throw new IllegalArgumentException("Outcome id must be nonempty and trimmed");
        }
        if (!Double.isFinite(weight) || weight < 0.0) {
            throw new IllegalArgumentException("Outcome weight must be finite and nonnegative");
        }
        this.id = id;
        this.weight = weight == 0.0 ? 0.0 : weight;
    }

    public String getId() {
        return id;
    }

    public double getWeight() {
        return weight;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof WeightedOutcome)) {
            return false;
        }
        WeightedOutcome outcome = (WeightedOutcome) other;
        return id.equals(outcome.id) && weight == outcome.weight;
    }

    @Override
    public int hashCode() {
        return 31 * id.hashCode() + Double.hashCode(weight);
    }
}
