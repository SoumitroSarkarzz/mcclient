package dev.mcclient;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.explosion.ExplosionImpl;
import java.util.*;

public final class CrystalController {
    private int cooldown;
    private String status = "Idle";
    public String status() { return status; }
    public void reset() { cooldown = 0; status = "Idle"; }
    private record Candidate(BlockPos base, Vec3d explosion, double score) {}

    public boolean tick(MinecraftClient mc) {
        if (!McClient.crystalAura || mc.player == null || mc.world == null || mc.interactionManager == null) { reset(); return false; }
        var p = mc.player;
        if (mc.currentScreen != null || !p.isAlive() || p.isSpectator() || p.isUsingItem()) return false;
        if (cooldown > 0) { cooldown--; return false; }
        cooldown = McClient.config.crystalDelay - 1;
        double range = McClient.config.crystalRange;
        List<LivingEntity> targets = mc.world.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(12),
            e -> e != p && e.isAlive() && !e.isSpectator() && !e.isInvulnerable() && !e.isInvisibleTo(p) && !p.isTeammate(e)
                && (e instanceof HostileEntity || (McClient.config.targetPlayers && e instanceof PlayerEntity)))
            .stream().sorted(Comparator.<LivingEntity>comparingInt(e -> e instanceof PlayerEntity ? 0 : 1)
                .thenComparingDouble(p::squaredDistanceTo)).limit(8).toList();
        if (targets.isEmpty()) { status = "No combat target"; return false; }
        Vec3d eyes = p.getEyePos();
        double breakReach = Math.min(range, p.getEntityInteractionRange());
        EndCrystalEntity best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (EndCrystalEntity crystal : mc.world.getEntitiesByClass(EndCrystalEntity.class, p.getBoundingBox().expand(breakReach), e -> e.isAlive())) {
            Vec3d aim = AuraController.aimPoint(crystal.getBoundingBox(), eyes);
            if (eyes.squaredDistanceTo(aim) > breakReach * breakReach || !visible(mc, eyes, aim)) continue;
            double score = score(mc, crystal.getEntityPos(), targets);
            if (score > bestScore) { best = crystal; bestScore = score; }
        }
        if (best != null && bestScore > Double.NEGATIVE_INFINITY) {
            mc.interactionManager.attackEntity(p, best); p.swingHand(Hand.MAIN_HAND);
            status = "Breaking safe crystal"; return true;
        }
        Hand hand = p.getMainHandStack().isOf(Items.END_CRYSTAL) ? Hand.MAIN_HAND
            : p.getOffHandStack().isOf(Items.END_CRYSTAL) ? Hand.OFF_HAND : null;
        if (hand == null) { status = "Hold end crystals to place"; return false; }
        double placeReach = Math.min(range, p.getBlockInteractionRange());
        int radius = (int)Math.ceil(placeReach);
        List<BlockPos> bases = new ArrayList<>();
        for (BlockPos pos : BlockPos.iterateOutwards(p.getBlockPos(), radius, radius, radius)) {
            var block = mc.world.getBlockState(pos);
            if (!(block.isOf(Blocks.OBSIDIAN) || block.isOf(Blocks.BEDROCK))) continue;
            Vec3d hit = Vec3d.ofCenter(pos).add(0, 0.5, 0);
            if (eyes.squaredDistanceTo(hit) > placeReach * placeReach) continue;
            if (!mc.world.isAir(pos.up()) || !mc.world.isAir(pos.up(2))) continue;
            Box space = new Box(pos.getX(), pos.getY() + 1, pos.getZ(), pos.getX() + 1, pos.getY() + 3, pos.getZ() + 1);
            if (!mc.world.getOtherEntities(null, space, e -> !e.isSpectator()).isEmpty()) continue;
            // Enter the block slightly so a ray ending exactly on its top boundary cannot miss it.
            BlockHitResult ray = mc.world.raycast(new RaycastContext(eyes, hit.add(0, -0.001, 0), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
            if (ray.getType() != HitResult.Type.BLOCK || !ray.getBlockPos().equals(pos) || ray.getSide() != Direction.UP) continue;
            bases.add(pos.toImmutable());
        }
        Candidate candidate = null;
        // Bound expensive exposure calculations even on large obsidian floors.
        for (BlockPos base : bases.stream().sorted(Comparator.comparingDouble(b -> targets.getFirst().squaredDistanceTo(Vec3d.ofCenter(b)))).limit(24).toList()) {
            Vec3d explosion = Vec3d.ofCenter(base).add(0, 0.5, 0);
            double score = score(mc, explosion, targets);
            if (score > Double.NEGATIVE_INFINITY && (candidate == null || score > candidate.score())) candidate = new Candidate(base, explosion, score);
        }
        if (candidate == null) { status = bases.isEmpty() ? "No valid visible base" : "Damage limits blocked"; return false; }
        BlockHitResult hit = new BlockHitResult(candidate.explosion(), Direction.UP, candidate.base(), false);
        if (mc.interactionManager.interactBlock(p, hand, hit).isAccepted()) {
            p.swingHand(hand); status = "Placing crystal"; return true;
        }
        status = "Placement rejected";
        return false;
    }

    private static boolean visible(MinecraftClient mc, Vec3d from, Vec3d to) {
        return mc.world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE, mc.player)).getType() == HitResult.Type.MISS;
    }
    private static double damage(MinecraftClient mc, Vec3d center, LivingEntity entity) {
        if (entity instanceof PlayerEntity player && player.getAbilities().invulnerable) return 0;
        double distance = entity.getEntityPos().distanceTo(center);
        if (distance >= 12) return 0;
        double difficulty = switch (mc.world.getDifficulty()) { case PEACEFUL -> 0; case EASY -> 1; case NORMAL -> 1; case HARD -> 1.5; };
        // Vanilla block exposure, raw damage before armor/resistance/enchantments: conservative for self.
        return CombatPolicy.crystalDamage(distance, ExplosionImpl.calculateReceivedDamage(center, entity), difficulty);
    }
    private static double score(MinecraftClient mc, Vec3d center, List<LivingEntity> targets) {
        boolean protectedPlayer = mc.world.getPlayers().stream().anyMatch(other -> other != mc.player && other.isAlive()
            && !other.isSpectator() && (!McClient.config.targetPlayers || mc.player.isTeammate(other)) && damage(mc, center, other) > 0);
        if (protectedPlayer) return Double.NEGATIVE_INFINITY;
        double self = damage(mc, center, mc.player);
        double health = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        double bestTarget = targets.stream().mapToDouble(e -> damage(mc, center, e)).max().orElse(0);
        var c = McClient.config;
        return CombatPolicy.safeCrystal(bestTarget, self, health, c.crystalMinDamage, c.crystalMaxSelfDamage, c.crystalReserve)
            ? bestTarget - self : Double.NEGATIVE_INFINITY;
    }
}
