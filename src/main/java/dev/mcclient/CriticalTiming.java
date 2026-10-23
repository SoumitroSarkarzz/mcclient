package dev.mcclient;

/** Pure timing policy. No fabricated position or critical-hit packets. */
public final class CriticalTiming {
    private CriticalTiming() {}
    public static boolean ready(float cooldown, boolean onGround, double fallDistance,
                                double verticalVelocity, boolean sprinting, boolean obstructed) {
        return cooldown >= 1.0f && !onGround && fallDistance > 0 && verticalVelocity < 0
            && !sprinting && !obstructed;
    }
}
