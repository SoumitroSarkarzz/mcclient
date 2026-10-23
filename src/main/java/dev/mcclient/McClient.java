package dev.mcclient;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Comparator;

public final class McClient implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("mcclient");
    public static ClientConfig config;
    public static boolean aura, flight, sprint;
    private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(Identifier.of("mcclient", "modules"));
    private KeyBinding menuKey, auraKey, flightKey, profilesKey;
    private boolean appliedSprint;
    private static KeyBinding key(String name, int code) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding("key.mcclient." + name, InputUtil.Type.KEYSYM, code, CATEGORY));
    }
    @Override public void onInitializeClient() {
        config = ClientConfig.load();
        menuKey = key("menu", GLFW.GLFW_KEY_RIGHT_SHIFT);
        auraKey = key("aura", GLFW.GLFW_KEY_R);
        flightKey = key("flight", GLFW.GLFW_KEY_G);
        profilesKey = key("profiles", GLFW.GLFW_KEY_F8);
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (screen instanceof TitleScreen) {
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("MC Profiles"), b -> mc.setScreen(new ProfileScreen(screen)))
                    .dimensions(w - 110, 6, 104, 20).build());
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.of("mcclient", "status"), (draw, ticks) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (!config.hud || mc.player == null || mc.options.hudHidden) return;
            draw.fill(8, 8, 184, 79, 0xC0141824);
            draw.fill(8, 8, 11, 79, 0xFF64D8FF);
            draw.drawTextWithShadow(mc.textRenderer, "MC CLIENT | " + mc.getCurrentFps() + " FPS", 17, 15, 0xFF64D8FF);
            draw.drawTextWithShadow(mc.textRenderer, "Aura " + state(aura) + "  Flight " + state(flight), 17, 30, 0xFFFFFFFF);
            draw.drawTextWithShadow(mc.textRenderer, "Sprint " + state(sprint), 17, 43, 0xFFFFFFFF);
            draw.drawTextWithShadow(mc.textRenderer, "Right Shift: settings", 17, 61, 0xFFA3AEC4);
        });
    }
    public static String state(boolean value) { return value ? "ON" : "OFF"; }
    private void tick(MinecraftClient mc) {
        while (profilesKey.wasPressed()) mc.setScreen(new ProfileScreen(mc.currentScreen));
        while (menuKey.wasPressed()) if (mc.player != null) mc.setScreen(new ModuleScreen());
        if (mc.player == null || mc.world == null) { aura = flight = sprint = false; appliedSprint = false; return; }
        while (auraKey.wasPressed()) aura = !aura;
        while (flightKey.wasPressed()) flight = !flight;
        if (mc.currentScreen != null) return;
        if (sprint && mc.player.input.getMovementInput().y > 0 && !mc.player.isSneaking() && mc.player.getHungerManager().getFoodLevel() > 6) {
            mc.player.setSprinting(true); appliedSprint = true;
        } else if (appliedSprint) { mc.player.setSprinting(false); appliedSprint = false; }
        if (flight && !mc.player.isSpectator() && !mc.player.isGliding()) {
            Vec3d direction = new Vec3d(mc.player.input.getMovementInput().x, 0, mc.player.input.getMovementInput().y);
            if (direction.lengthSquared() > 1) direction = direction.normalize();
            direction = direction.rotateY((float) Math.toRadians(-mc.player.getYaw())).multiply(config.flightSpeed);
            double vertical = (mc.options.jumpKey.isPressed() ? config.flightSpeed : 0) - (mc.options.sneakKey.isPressed() ? config.flightSpeed : 0);
            mc.player.setVelocity(direction.x, vertical, direction.z);
        }
        if (!aura || mc.interactionManager == null || mc.player.isUsingItem() || mc.player.getAttackCooldownProgress(0) < 1) return;
        LivingEntity target = mc.world.getEntitiesByClass(LivingEntity.class, mc.player.getBoundingBox().expand(config.range),
            e -> e != mc.player && e.isAlive() && !e.isSpectator() && !e.isInvulnerable()
                && (e instanceof HostileEntity || (config.targetPlayers && e instanceof PlayerEntity && !mc.player.isTeammate(e)))
                && mc.player.squaredDistanceTo(e) <= config.range * config.range && mc.player.canSee(e))
            .stream().min(Comparator.comparingDouble(mc.player::squaredDistanceTo)).orElse(null);
        if (target != null) { mc.interactionManager.attackEntity(mc.player, target); mc.player.swingHand(Hand.MAIN_HAND); }
    }
}
