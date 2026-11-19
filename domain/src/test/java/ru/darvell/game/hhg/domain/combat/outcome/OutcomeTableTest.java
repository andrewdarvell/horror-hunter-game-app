package ru.darvell.game.hhg.domain.combat.outcome;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OutcomeTableTest {
    @Test
    void validatesOutcomeValuesAndCanonicalizesZero() {
        WeightedOutcome outcome = new WeightedOutcome("case Sensitive", 0.25);
        assertEquals("case Sensitive", outcome.getId());
        assertEquals(0.25, outcome.getWeight());
        assertEquals(new WeightedOutcome("case Sensitive", 0.25), outcome);
        assertNotEquals(new WeightedOutcome("Case Sensitive", 0.25), outcome);
        assertNotEquals(new WeightedOutcome("case Sensitive", 0.5), outcome);
        WeightedOutcome zero = new WeightedOutcome("zero", -0.0);
        assertEquals(0L, Double.doubleToRawLongBits(zero.getWeight()));
        assertEquals(new WeightedOutcome("zero", 0.0), zero);
        assertEquals(new WeightedOutcome("zero", 0.0).hashCode(), zero.hashCode());

        for (String id : new String[] {null, "", " ", " a", "a "}) {
            assertThrows(IllegalArgumentException.class, () -> new WeightedOutcome(id, 1));
        }
        for (double weight : new double[] {-1, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new WeightedOutcome("a", weight));
        }
    }

    @Test
    void copiesInputAndPreservesCaseSensitiveOrder() {
        WeightedOutcome lower = new WeightedOutcome("a", 0);
        WeightedOutcome upper = new WeightedOutcome("A", 0);
        List<WeightedOutcome> input = new ArrayList<>(Arrays.asList(lower, upper));
        OutcomeTable table = OutcomeTable.of(input);
        input.clear();

        assertEquals(Arrays.asList(lower, upper), table.getOutcomes());
        assertThrows(UnsupportedOperationException.class, () -> table.getOutcomes().clear());
        assertTrue(OutcomeTable.of(Collections.emptyList()).getOutcomes().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> OutcomeTable.of(null));
        assertThrows(IllegalArgumentException.class, () -> OutcomeTable.of(Arrays.asList(lower, null)));
        assertThrows(IllegalArgumentException.class, () -> OutcomeTable.of(Arrays.asList(lower, lower)));
    }

    @Test
    void editsCompositionWithoutChangingPriorSnapshots() {
        WeightedOutcome a = new WeightedOutcome("a", 50);
        WeightedOutcome b = new WeightedOutcome("b", 30);
        WeightedOutcome c = new WeightedOutcome("c", 20);
        WeightedOutcome d = new WeightedOutcome("d", 10);
        WeightedOutcome e = new WeightedOutcome("e", 40);
        OutcomeTable original = OutcomeTable.of(Arrays.asList(a, b, c));

        assertEquals(Arrays.asList(a, e, d), original.add(d).remove("b").replace("c", e).getOutcomes());
        assertEquals(Arrays.asList(a, b, c), original.getOutcomes());
        WeightedOutcome sameId = new WeightedOutcome("b", 5);
        assertEquals(Arrays.asList(a, sameId, c), original.replace("b", sameId).getOutcomes());
        assertEquals(Collections.singletonList(a),
            OutcomeTable.of(Collections.singletonList(c)).remove("c").add(a).getOutcomes());

        assertThrows(IllegalArgumentException.class, () -> original.add(a));
        assertThrows(IllegalArgumentException.class, () -> original.add(null));
        assertThrows(IllegalArgumentException.class, () -> original.remove("missing"));
        assertThrows(IllegalArgumentException.class, () -> original.remove(null));
        assertThrows(IllegalArgumentException.class, () -> original.replace("missing", e));
        assertThrows(IllegalArgumentException.class, () -> original.replace("b", null));
        assertThrows(IllegalArgumentException.class, () -> original.replace("b", a));
        assertEquals(Arrays.asList(a, b, c), original.getOutcomes());
    }

    @Test
    void onlyChangesWeightsOfExistingOutcomesAndKeepsOrder() {
        WeightedOutcome a = new WeightedOutcome("a", 50);
        WeightedOutcome b = new WeightedOutcome("b", 30);
        WeightedOutcome c = new WeightedOutcome("c", 20);
        OutcomeTable original = OutcomeTable.of(Arrays.asList(a, b, c));
        OutcomeTable filtered = original.filter(id -> !id.equals("b"));
        assertEquals(Arrays.asList(a, c), filtered.getOutcomes());
        assertEquals(Arrays.asList(new WeightedOutcome("a", 35), c),
            filtered.withWeight("a", 25).withWeight("a", 35).getOutcomes());
        assertThrows(IllegalArgumentException.class, () -> filtered.withWeight("b", 100));
        assertThrows(IllegalArgumentException.class, () -> filtered.filter(null));
        for (double invalid : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> filtered.withWeight("a", invalid));
        }
        IllegalStateException failure = new IllegalStateException("filter failure");
        assertSame(failure, assertThrows(IllegalStateException.class, () -> original.filter(id -> {
            if (id.equals("b")) {
                throw failure;
            }
            return true;
        })));
        assertEquals(Arrays.asList(a, b, c), original.getOutcomes());
    }

    @Test
    void filtersNewCandidatesBeforeApplyingContextualWeights() {
        OutcomeTable base = OutcomeTable.of(Arrays.asList(
            new WeightedOutcome("a", 50), new WeightedOutcome("b", 30), new WeightedOutcome("c", 20)));

        OutcomeTable ordinary = prepare(base, false);
        OutcomeTable contextual = prepare(base, true);

        assertEquals(base.getOutcomes(), ordinary.getOutcomes());
        assertEquals(Arrays.asList(new WeightedOutcome("a", 25), new WeightedOutcome("b", 30),
            new WeightedOutcome("e", 45)), contextual.getOutcomes());
        assertThrows(IllegalArgumentException.class, () -> contextual.withWeight("blocked", 10));

        OutcomeTable changed = contextual.replace("b", new WeightedOutcome("blocked", 30))
            .filter(id -> !id.equals("blocked")).withWeight("e", 40);
        assertEquals(Arrays.asList(new WeightedOutcome("a", 25), new WeightedOutcome("e", 40)),
            changed.getOutcomes());
    }

    private static OutcomeTable prepare(OutcomeTable base, boolean specialContext) {
        OutcomeTable candidates = specialContext
            ? base.add(new WeightedOutcome("blocked", 1_000_000)).replace("c", new WeightedOutcome("e", 20))
            : base;
        OutcomeTable allowed = candidates.filter(id -> !id.equals("blocked"));
        return specialContext ? allowed.withWeight("a", 25).withWeight("e", 45) : allowed;
    }
}
