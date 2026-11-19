package ru.darvell.game.hhg.domain.combat.outcome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Упорядоченный снимок исходов. Промежуточный снимок может быть пустым. */
public final class OutcomeTable {
    private final List<WeightedOutcome> outcomes;

    private OutcomeTable(List<WeightedOutcome> snapshot) {
        outcomes = Collections.unmodifiableList(snapshot);
    }

    public static OutcomeTable of(List<WeightedOutcome> outcomes) {
        if (outcomes == null) {
            throw new IllegalArgumentException("Outcome list is required");
        }
        List<WeightedOutcome> snapshot = new ArrayList<>(outcomes);
        Set<String> ids = new HashSet<>();
        for (WeightedOutcome outcome : snapshot) {
            if (outcome == null) {
                throw new IllegalArgumentException("Outcome is required");
            }
            if (!ids.add(outcome.getId())) {
                throw new IllegalArgumentException("Duplicate outcome id: " + outcome.getId());
            }
        }
        return new OutcomeTable(snapshot);
    }

    public List<WeightedOutcome> getOutcomes() {
        return outcomes;
    }

    public OutcomeTable add(WeightedOutcome outcome) {
        List<WeightedOutcome> updated = new ArrayList<>(outcomes);
        updated.add(outcome);
        return of(updated);
    }

    public OutcomeTable remove(String id) {
        List<WeightedOutcome> updated = new ArrayList<>(outcomes);
        updated.remove(indexOf(id));
        return new OutcomeTable(updated);
    }

    public OutcomeTable replace(String id, WeightedOutcome replacement) {
        List<WeightedOutcome> updated = new ArrayList<>(outcomes);
        updated.set(indexOf(id), replacement);
        return of(updated);
    }

    private int indexOf(String id) {
        for (int i = 0; i < outcomes.size(); i++) {
            if (outcomes.get(i).getId().equals(id)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Unknown outcome id: " + id);
    }

    /** Сохраняет исходы, для которых вызывающая сторона подтвердила физический допуск. */
    public OutcomeTable filter(Predicate<String> allowed) {
        if (allowed == null) {
            throw new IllegalArgumentException("Outcome filter is required");
        }
        List<WeightedOutcome> filtered = new ArrayList<>();
        for (WeightedOutcome outcome : outcomes) {
            if (allowed.test(outcome.getId())) {
                filtered.add(outcome);
            }
        }
        return new OutcomeTable(filtered);
    }

    public OutcomeTable withWeight(String id, double weight) {
        int index = indexOf(id);
        List<WeightedOutcome> updated = new ArrayList<>(outcomes);
        updated.set(index, new WeightedOutcome(id, weight));
        return new OutcomeTable(updated);
    }
}
