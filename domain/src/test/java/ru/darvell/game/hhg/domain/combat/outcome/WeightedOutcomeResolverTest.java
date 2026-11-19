package ru.darvell.game.hhg.domain.combat.outcome;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.DoubleSupplier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WeightedOutcomeResolverTest {
    @Test
    void selectsEveryOutcomeInTablesOfDifferentSizes() {
        for (int size : new int[] {1, 2, 3, 5}) {
            double[] weights = new double[size];
            for (int i = 0; i < size; i++) {
                weights[i] = 1;
            }
            OutcomeTable table = table(weights);
            for (int i = 0; i < size; i++) {
                assertPick("outcome-" + i, table, (i + 0.5) / size);
            }
        }
    }

    @Test
    void normalizesModifiedWeightsAndPreservesExactScaling() {
        OutcomeTable adjusted = table(50, 30, 20).withWeight("outcome-0", 25).withWeight("outcome-2", 45);
        for (OutcomeTable table : new OutcomeTable[] {adjusted, table(50, 60, 90)}) {
            assertPick("outcome-0", table, Math.nextDown(0.25));
            assertPick("outcome-1", table, 0.25);
            assertPick("outcome-1", table, Math.nextDown(0.55));
            assertPick("outcome-2", table, 0.55);
        }
    }

    private static OutcomeTable table(double... weights) {
        List<WeightedOutcome> outcomes = new ArrayList<>();
        for (int i = 0; i < weights.length; i++) {
            outcomes.add(new WeightedOutcome("outcome-" + i, weights[i]));
        }
        return OutcomeTable.of(outcomes);
    }

    private static void assertPick(String expected, OutcomeTable table, double sample) {
        assertEquals(expected, WeightedOutcomeResolver.resolve(table, () -> sample), "sample " + sample);
    }

    @Test
    void assignsExactBoundariesToTheNextInterval() {
        OutcomeTable table = table(50, 30, 20);
        assertPick("outcome-0", table, 0);
        assertPick("outcome-0", table, Math.nextDown(0.5));
        assertPick("outcome-1", table, 0.5);
        assertPick("outcome-1", table, Math.nextUp(0.5));
        assertPick("outcome-1", table, Math.nextDown(0.8));
        assertPick("outcome-2", table, 0.8);
        assertPick("outcome-2", table, Math.nextUp(0.8));
        assertPick("outcome-2", table, Math.nextDown(1.0));
    }

    @Test
    void skipsZeroWeightsAtAnyPosition() {
        OutcomeTable table = table(0, 1, -0.0, 1, 0);
        assertPick("outcome-1", table, 0);
        assertPick("outcome-1", table, Math.nextDown(0.5));
        assertPick("outcome-3", table, 0.5);
        assertPick("outcome-3", table, Math.nextDown(1.0));
    }

    @Test
    void handlesExtremeFiniteWeights() {
        for (double weight : new double[] {Double.MAX_VALUE, Double.MIN_VALUE}) {
            OutcomeTable table = table(weight, weight);
            assertPick("outcome-0", table, Math.nextDown(0.5));
            assertPick("outcome-1", table, 0.5);
        }
    }

    @Test
    void usesCanonicalDecimalValuesWithoutRoundingCallerArithmetic() {
        OutcomeTable table = table(0.1, 0.2, 0.7);
        assertPick("outcome-1", table, Math.nextDown(0.3));
        assertPick("outcome-2", table, 0.3);
        OutcomeTable computed = table(0.1 + 0.2, 0.7);
        assertPick("outcome-0", computed, 0.3);
        assertPick("outcome-1", computed, 0.1 + 0.2);
    }

    @Test
    void rejectsInvalidFinalConfigurationsBeforeSampling() {
        for (OutcomeTable invalid : new OutcomeTable[] {null, table(), table(0, 0)}) {
            assertThrows(IllegalArgumentException.class,
                () -> WeightedOutcomeResolver.resolve(invalid, () -> fail("Unexpected random call")));
        }
        assertThrows(IllegalArgumentException.class, () -> WeightedOutcomeResolver.resolve(table(1), null));
        assertThrows(IllegalArgumentException.class, () -> WeightedOutcomeResolver.resolve(table(1, 1), null));
    }

    @Test
    void callsRandomOnlyOnceAndOnlyForMultiplePositiveWeights() {
        CountingSource source = new CountingSource(() -> -0.0);
        OutcomeTable prepared = table(0, 5, 0).add(new WeightedOutcome("added", 4))
            .filter(id -> !id.equals("added")).withWeight("outcome-1", 7);
        assertEquals(0, source.calls);
        assertEquals("outcome-1", WeightedOutcomeResolver.resolve(prepared, source));
        assertEquals(0, source.calls);
        assertEquals("outcome-0", WeightedOutcomeResolver.resolve(table(1, 1), source));
        assertEquals(1, source.calls);
    }

    @Test
    void rejectsInvalidSamplesAndPropagatesSourceFailureWithoutRetrying() {
        OutcomeTable table = table(1, 1);
        for (double sample : new double[] {-0.1, 1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            CountingSource source = new CountingSource(() -> sample);
            assertThrows(IllegalArgumentException.class, () -> WeightedOutcomeResolver.resolve(table, source));
            assertEquals(1, source.calls);
        }

        IllegalStateException failure = new IllegalStateException("random source failure");
        CountingSource source = new CountingSource(() -> {
            throw failure;
        });
        assertSame(failure, assertThrows(IllegalStateException.class,
            () -> WeightedOutcomeResolver.resolve(table, source)));
        assertEquals(1, source.calls);
    }

    private static final class CountingSource implements DoubleSupplier {
        private final DoubleSupplier delegate;
        private int calls;

        private CountingSource(DoubleSupplier delegate) {
            this.delegate = delegate;
        }

        @Override
        public double getAsDouble() {
            calls++;
            return delegate.getAsDouble();
        }
    }

    @Test
    void replaysTheSameOrderedSequenceWithTheSameSeed() {
        CountingSource first = new CountingSource(new Random(42)::nextDouble);
        CountingSource second = new CountingSource(new Random(42)::nextDouble);
        OutcomeTable[] tables = {table(50, 30, 20), table(0, 3, 0), table(2, 1), table(1)};
        List<String> firstResults = new ArrayList<>();
        List<String> secondResults = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            OutcomeTable next = tables[i % tables.length];
            firstResults.add(WeightedOutcomeResolver.resolve(next, first));
            secondResults.add(WeightedOutcomeResolver.resolve(next, second));
        }
        assertEquals(firstResults, secondResults);
        assertEquals(12, first.calls);
        assertEquals(first.calls, second.calls);
    }

    @Test
    void assignsIntervalsInListOrder() {
        WeightedOutcome a = new WeightedOutcome("a", 1);
        WeightedOutcome b = new WeightedOutcome("b", 3);
        assertPick("b", OutcomeTable.of(Arrays.asList(a, b)), 0.5);
        assertPick("a", OutcomeTable.of(Arrays.asList(b, a)), 0.75);
    }

    @Test
    void exactScalingPreservesAllowedPositiveSelectionsAcrossASampleGrid() {
        for (int left = 1; left <= 4; left++) {
            for (int right = 1; right <= 4; right++) {
                OutcomeTable allowed = table(1_000_000, left, 0, right)
                    .filter(id -> !id.equals("outcome-0"));
                OutcomeTable scaled = allowed.withWeight("outcome-1", 2 * left)
                    .withWeight("outcome-3", 2 * right);
                for (int i = 0; i < 32; i++) {
                    double sample = i / 32.0;
                    String selected = WeightedOutcomeResolver.resolve(allowed, () -> sample);
                    assertTrue(selected.equals("outcome-1") || selected.equals("outcome-3"));
                    assertPick(selected, scaled, sample);
                }
                assertPick("outcome-3", allowed, Math.nextDown(1.0));
            }
        }
    }

    @Test
    void callerCanLookUpADescriptionForTheSelectedId() {
        Map<String, String> descriptions = Collections.singletonMap("outcome-1", "second result");
        String selected = WeightedOutcomeResolver.resolve(table(1, 1), () -> 0.5);
        assertEquals("second result", descriptions.get(selected));
    }
}
