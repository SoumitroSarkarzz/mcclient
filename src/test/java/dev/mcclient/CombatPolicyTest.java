package dev.mcclient;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatPolicyTest {
    @Test void logThresholdIncludesAbsorptionAndBoundary() {
        assertTrue(CombatPolicy.shouldLog(true, true, false, 6, 0, 6));
        assertFalse(CombatPolicy.shouldLog(true, true, false, 6, 2, 6));
        assertTrue(CombatPolicy.shouldLog(true, true, false, 4, 2, 6));
    }
    @Test void disabledDeadSpectatorAndInvalidHealthNeverTriggerLog() {
        assertFalse(CombatPolicy.shouldLog(false, true, false, 2, 0, 6));
        assertFalse(CombatPolicy.shouldLog(true, false, false, 0, 0, 6));
        assertFalse(CombatPolicy.shouldLog(true, true, true, 2, 0, 6));
        assertFalse(CombatPolicy.shouldLog(true, true, false, Float.NaN, 0, 6));
    }
    @Test void lethalAndExcessSelfDamageAreRejected() {
        assertFalse(CombatPolicy.safeCrystal(30, 10, 10, 4, 12, 0));
        assertFalse(CombatPolicy.safeCrystal(30, 8, 20, 4, 6, 4));
        assertFalse(CombatPolicy.safeCrystal(30, 6, 8, 4, 6, 4));
    }
    @Test void lowTargetDamageAndUnfavorableTradesAreRejected() {
        assertFalse(CombatPolicy.safeCrystal(2, 1, 20, 4, 6, 4));
        assertFalse(CombatPolicy.safeCrystal(6, 6, 20, 4, 6, 4));
        assertFalse(CombatPolicy.safeCrystal(Double.NaN, 1, 20, 4, 6, 4));
    }
    @Test void beneficialNonlethalCandidateIsAccepted() {
        assertTrue(CombatPolicy.safeCrystal(12, 4, 20, 4, 6, 4));
        assertTrue(CombatPolicy.safeCrystal(12, 6, 10, 4, 6, 4));
    }
    @Test void distanceExposureAndDifficultyAffectRawDamage() {
        assertEquals(85, CombatPolicy.crystalDamage(0, 1, 1));
        assertEquals(0, CombatPolicy.crystalDamage(12, 1, 1));
        assertEquals(1, CombatPolicy.crystalDamage(3, 0, 1));
        assertTrue(CombatPolicy.crystalDamage(3, 1, 1.5) > CombatPolicy.crystalDamage(3, 1, 1));
        assertTrue(CombatPolicy.crystalDamage(3, 1, 1) > CombatPolicy.crystalDamage(6, 1, 1));
        assertEquals(0, CombatPolicy.crystalDamage(Double.NaN, 1, 1));
    }
}
