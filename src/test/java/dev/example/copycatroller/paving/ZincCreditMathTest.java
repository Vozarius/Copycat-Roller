package dev.example.copycatroller.paving;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ZincCreditMathTest {
    @Test
    void ordinaryLayersUseTwoHalfLayerCreditsEach() {
        assertEquals(0, ZincCreditMath.creditsForLayers(0));
        assertEquals(6, ZincCreditMath.creditsForLayers(3));
        assertEquals(16, ZincCreditMath.creditsForLayers(8));
    }

    @Test
    void halfLayerSidesUseOneCreditPerItem() {
        assertEquals(8, ZincCreditMath.creditsForHalfLayers(3, 5));
        assertEquals(16, ZincCreditMath.creditsForHalfLayers(8, 8));
    }

    @Test
    void oneZincCreatesSixteenCreditsAndReturnsExactChange() {
        ZincCreditMath.PaymentPlan plan = ZincCreditMath.plan(6, 0);
        assertEquals(0, plan.halfLayersToConsume());
        assertEquals(1, plan.zincIngotsToConsume());
        assertEquals(10, plan.halfLayerChange());
        assertConserved(6, plan);
    }

    @Test
    void existingHalfLayerChangeIsSpentBeforeAnotherIngot() {
        ZincCreditMath.PaymentPlan exact = ZincCreditMath.plan(7, 10);
        assertEquals(7, exact.halfLayersToConsume());
        assertEquals(0, exact.zincIngotsToConsume());
        assertEquals(0, exact.halfLayerChange());
        assertConserved(7, exact);

        ZincCreditMath.PaymentPlan mixed = ZincCreditMath.plan(12, 5);
        assertEquals(5, mixed.halfLayersToConsume());
        assertEquals(1, mixed.zincIngotsToConsume());
        assertEquals(9, mixed.halfLayerChange());
        assertConserved(12, mixed);
    }

    @Test
    void everySupportedCostConservesCredits() {
        for (int required = 1; required <= 16; required++) {
            for (int available = 0; available <= 16; available++) {
                assertConserved(required, ZincCreditMath.plan(required, available));
            }
        }
    }

    @Test
    void invalidValuesFailFast() {
        assertThrows(IllegalArgumentException.class, () -> ZincCreditMath.creditsForLayers(-1));
        assertThrows(IllegalArgumentException.class, () -> ZincCreditMath.creditsForLayers(9));
        assertThrows(
            IllegalArgumentException.class,
            () -> ZincCreditMath.creditsForHalfLayers(0, 9)
        );
        assertThrows(IllegalArgumentException.class, () -> ZincCreditMath.plan(0, 0));
        assertThrows(IllegalArgumentException.class, () -> ZincCreditMath.plan(1, -1));
    }

    private static void assertConserved(
        int requiredCredits,
        ZincCreditMath.PaymentPlan plan
    ) {
        assertEquals(
            requiredCredits + plan.halfLayerChange(),
            plan.halfLayersToConsume()
                + plan.zincIngotsToConsume() * ZincCreditMath.CREDITS_PER_ZINC
        );
    }
}
