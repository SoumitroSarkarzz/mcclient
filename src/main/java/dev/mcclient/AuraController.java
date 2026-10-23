package dev.mcclient;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import java.util.Comparator;
import java.util.List;

public final class AuraController {
    private LivingEntity target;
    private String status = "No target";
    public boolean hasTarget() { return target != null; }
    public String status() { return status; }
    public void reset() { target = null; status = "No target"; }

    public void tick(MinecraftClient mc) {
        if (!McClient.aura || mc.player == null || mc.world == null || mc.interactionManager == null
            || mc.currentScreen != null || mc.player.isSpectator() || !mc.player.isAlive()) { reset(); return; }
        var player = mc.player;
        double reach = Math.min(McClient.config.range, player.getEntityInteractionRange());
        Vec3d eyes = player.getEyePos();
        List<LivingEntity> candidates = mc.world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(reach),
            e -> e != player && e.isAlive() && !e.isSpectator() && !e.isInvulnerable() && e.isAttackable()
                && !e.isInvisibleTo(player) && !player.isTeammate(e)
                && (e instanceof HostileEntity || (McClient.config.targetPlayers && e instanceof PlayerEntity))
                && eyes.squaredDistanceTo(aimPoint(e.getBoundingBox(), eyes)) <= reach * reach
                && mc.world.raycast(new RaycastContext(eyes, aimPoint(e.getBoundingBox(), eyes),
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player)).getType() == HitResult.Type.MISS);
        // Keep a valid target rather than alternating every tick; players take priority when enabled.
        boolean playerAvailable = candidates.stream().anyMatch(e -> e instanceof PlayerEntity);
        if (!candidates.contains(target) || (playerAvailable && !(target instanceof PlayerEntity))) {
            target = candidates.stream().min(Comparator.<LivingEntity>comparingInt(e -> e instanceof PlayerEntity ? 0 : 1)
                .thenComparingDouble(e -> eyes.squaredDistanceTo(aimPoint(e.getBoundingBox(), eyes)))
                .thenComparingInt(LivingEntity::getId)).orElse(null);
        }
        if (target == null) { status = "No visible target"; return; }
        if (player.isUsingItem()) { status = "Using item"; return; }
        float cooldown = player.getAttackCooldownProgress(0);
        if (McClient.config.criticalAura) {
            boolean blocked = McClient.flight || player.getAbilities().flying || player.isGliding() || player.hasVehicle()
                || player.isClimbing() || player.isTouchingWater() || player.isInLava()
                || player.hasStatusEffect(StatusEffects.BLINDNESS) || player.hasStatusEffect(StatusEffects.LEVITATION);
            if (blocked) { status = "Crit blocked: movement"; return; }
            if (player.isSprinting()) { player.setSprinting(false); status = "Preparing critical"; return; }
            if (cooldown < 1) { status = "Charging critical"; return; }
            if (player.isOnGround() && McClient.config.autoJumpCrits && !player.isSneaking()
                && mc.world.isSpaceEmpty(player, player.getBoundingBox().offset(0, 1.25, 0))) {
                player.jump(); status = "Jumping for critical"; return;
            }
            if (!CriticalTiming.ready(cooldown, player.isOnGround(), player.fallDistance, player.getVelocity().y,
                    player.isSprinting(), blocked)) { status = "Waiting for fall"; return; }
        } else if (cooldown < 1) { status = "Charging attack"; return; }
        // No camera rotation or view-direction requirement; collision and reach are still enforced.
        mc.interactionManager.attackEntity(player, target);
        player.swingHand(Hand.MAIN_HAND);
        status = McClient.config.criticalAura ? "Critical window hit" : "Full-charge hit";
    }

    static Vec3d aimPoint(Box box, Vec3d eyes) {
        return new Vec3d(MathHelper.clamp(eyes.x, box.minX + 0.001, box.maxX - 0.001),
            MathHelper.clamp(eyes.y, box.minY + 0.001, box.maxY - 0.001),
            MathHelper.clamp(eyes.z, box.minZ + 0.001, box.maxZ - 0.001));
    }
}
