package dev.mcclient;

/** Pure safety decisions; health and damage are in health points, not hearts. */
public final class CombatPolicy {
    private CombatPolicy() {}
    public static boolean shouldLog(boolean enabled, boolean alive, boolean spectator, float health, float absorption, double threshold) {
        return enabled && alive && !spectator && Float.isFinite(health) && Float.isFinite(absorption)
            && health + absorption <= threshold;
    }
    public static double crystalDamage(double distance, double exposure, double difficultyMultiplier) {
        if (!Double.isFinite(distance) || !Double.isFinite(exposure) || distance < 0 || distance >= 12) return 0;
        double impact = (1 - distance / 12) * Math.clamp(exposure, 0, 1);
        return Math.floor((impact * impact + impact) * 42 + 1) * difficultyMultiplier;
    }
    public static boolean safeCrystal(double targetDamage, double selfDamage, double health,
                                      double minimumTarget, double maximumSelf, double reserve) {
        return Double.isFinite(targetDamage) && Double.isFinite(selfDamage) && Double.isFinite(health)
            && selfDamage >= 0 && targetDamage >= minimumTarget && selfDamage <= maximumSelf
            && health - selfDamage >= reserve && health - selfDamage > 0 && targetDamage > selfDamage;
    }
}
