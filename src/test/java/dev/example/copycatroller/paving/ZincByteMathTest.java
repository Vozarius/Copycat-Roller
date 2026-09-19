package dev.example.copycatroller.paving;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ZincByteMathTest {
    @Test
    void oneZincProducesEightBytesAndExactChange() {
        ZincByteMath.PaymentPlan plan = ZincByteMath.plan(3, 0);
        assertEquals(0, plan.bytesToConsume());
        assertEquals(1, plan.zincIngotsToConsume());
        assertEquals(5, plan.byteChange());
        assertConserved(3, plan);
    }

    @Test
    void existingBytesAreConsumedBeforeZinc() {
        ZincByteMath.PaymentPlan exact = ZincByteMath.plan(4, 7);
        assertEquals(4, exact.bytesToConsume());
        assertEquals(0, exact.zincIngotsToConsume());
        assertEquals(0, exact.byteChange());
        assertConserved(4, exact);

        ZincByteMath.PaymentPlan mixed = ZincByteMath.plan(10, 3);
        assertEquals(3, mixed.bytesToConsume());
        assertEquals(1, mixed.zincIngotsToConsume());
        assertEquals(1, mixed.byteChange());
        assertConserved(10, mixed);
    }

    @Test
    void everyPracticalCostConservesItems() {
        for (int required = 1; required <= 32; required++) {
            for (int available = 0; available <= 16; available++) {
                assertConserved(required, ZincByteMath.plan(required, available));
            }
        }
    }

    @Test
    void invalidValuesFailFast() {
        assertThrows(IllegalArgumentException.class, () -> ZincByteMath.plan(0, 0));
        assertThrows(IllegalArgumentException.class, () -> ZincByteMath.plan(1, -1));
    }

    private static void assertConserved(
        int requiredBytes,
        ZincByteMath.PaymentPlan plan
    ) {
        assertEquals(
            requiredBytes + plan.byteChange(),
            plan.bytesToConsume()
                + plan.zincIngotsToConsume() * ZincByteMath.BYTES_PER_ZINC
        );
    }
}
