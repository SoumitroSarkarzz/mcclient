package dev.mcclient;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CriticalTimingTest {
    @Test void fullChargeDuringUnobstructedFallIsEligible() {
        assertTrue(CriticalTiming.ready(1, false, 0.05, -0.1, false, false));
    }
    @Test void weakCooldownCannotProduceFullDamageCritical() {
        assertFalse(CriticalTiming.ready(0.9f, false, 0.1, -0.1, false, false));
    }
    @Test void groundedAttacksAreNotCritical() {
        assertFalse(CriticalTiming.ready(1, true, 0.1, -0.1, false, false));
    }
    @Test void risingAndStationaryPlayersMustWait() {
        assertFalse(CriticalTiming.ready(1, false, 0.1, 0.1, false, false));
        assertFalse(CriticalTiming.ready(1, false, 0.1, 0, false, false));
        assertFalse(CriticalTiming.ready(1, false, 0, -0.1, false, false));
    }
    @Test void sprintingAndBlockedMovementCannotQualify() {
        assertFalse(CriticalTiming.ready(1, false, 0.1, -0.1, true, false));
        assertFalse(CriticalTiming.ready(1, false, 0.1, -0.1, false, true));
    }
}
