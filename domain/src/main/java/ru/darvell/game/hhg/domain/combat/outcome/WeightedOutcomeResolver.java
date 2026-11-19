package ru.darvell.game.hhg.domain.combat.outcome;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.DoubleSupplier;

/** Выбирает идентификатор из уже подготовленной таблицы допустимых исходов. */
public final class WeightedOutcomeResolver {
    private WeightedOutcomeResolver() {
    }

    /**
     * Вызывает источник один раз для нескольких положительных весов.
     * Единственный положительный исход возвращается без вызова источника.
     */
    public static String resolve(OutcomeTable table, DoubleSupplier random) {
        if (table == null || random == null) {
            throw new IllegalArgumentException("Outcome table and random source are required");
        }

        List<WeightedOutcome> outcomes = table.getOutcomes();
        BigDecimal[] boundaries = new BigDecimal[outcomes.size()];
        BigDecimal total = BigDecimal.ZERO;
        int positiveCount = 0;
        String lastPositiveId = null;
        for (int i = 0; i < outcomes.size(); i++) {
            WeightedOutcome outcome = outcomes.get(i);
            if (outcome.getWeight() > 0.0) {
                total = total.add(BigDecimal.valueOf(outcome.getWeight()));
                boundaries[i] = total;
                positiveCount++;
                lastPositiveId = outcome.getId();
            }
        }
        if (positiveCount == 0) {
            throw new IllegalArgumentException("At least one outcome must have a positive weight");
        }
        if (positiveCount == 1) {
            return lastPositiveId;
        }

        double sample = random.getAsDouble();
        if (!Double.isFinite(sample) || sample < 0.0 || sample >= 1.0) {
            throw new IllegalArgumentException("Random sample must be in [0, 1)");
        }

        // Сравнение с u * S сохраняет точные границы без деления.
        BigDecimal target = BigDecimal.valueOf(sample).multiply(total);
        for (int i = 0; i < boundaries.length; i++) {
            BigDecimal boundary = boundaries[i];
            if (boundary != null && boundary.compareTo(target) > 0) {
                return outcomes.get(i).getId();
            }
        }
        throw new IllegalStateException("Valid random sample did not match any outcome");
    }
}
