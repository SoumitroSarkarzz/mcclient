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
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class McClient implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("mcclient");
    public static ClientConfig config;
    public static boolean aura, flight, sprint, crystalAura, autoTotem, autoLog;
    private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(Identifier.of("mcclient", "modules"));
    private KeyBinding menuKey, auraKey, flightKey, profilesKey, crystalKey, totemKey, logKey;
    private boolean appliedSprint;
    private final AuraController auraController = new AuraController();
    private final CrystalController crystalController = new CrystalController();
    private final AutoTotemController totemController = new AutoTotemController();
    private static KeyBinding key(String name, int code) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding("key.mcclient." + name, InputUtil.Type.KEYSYM, code, CATEGORY));
    }
    @Override public void onInitializeClient() {
        config = ClientConfig.load();
        menuKey = key("menu", GLFW.GLFW_KEY_RIGHT_SHIFT);
        auraKey = key("aura", GLFW.GLFW_KEY_R);
        flightKey = key("flight", GLFW.GLFW_KEY_G);
        profilesKey = key("profiles", GLFW.GLFW_KEY_F8);
        crystalKey = key("crystal", GLFW.GLFW_KEY_UNKNOWN);
        totemKey = key("totem", GLFW.GLFW_KEY_UNKNOWN);
        logKey = key("autolog", GLFW.GLFW_KEY_UNKNOWN);
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
            draw.fill(8, 8, 198, 133, 0xC0141824);
            draw.fill(8, 8, 11, 133, 0xFF64D8FF);
            draw.drawTextWithShadow(mc.textRenderer, "MC CLIENT | " + mc.getCurrentFps() + " FPS", 17, 15, 0xFF64D8FF);
            draw.drawTextWithShadow(mc.textRenderer, "Aura " + state(aura) + "  Flight " + state(flight), 17, 30, 0xFFFFFFFF);
            draw.drawTextWithShadow(mc.textRenderer, "Sprint " + state(sprint), 17, 43, 0xFFFFFFFF);
            draw.drawTextWithShadow(mc.textRenderer, mc.textRenderer.trimToWidth(aura ? auraController.status() : "Aura idle", 158), 17, 59, 0xFFA3AEC4);
            draw.drawTextWithShadow(mc.textRenderer, "Crystal " + state(crystalAura) + " Totem " + state(autoTotem), 17, 74, 0xFFFFFFFF);
            String extra = crystalAura ? crystalController.status() : autoTotem ? totemController.status() : "Combat extras idle";
            draw.drawTextWithShadow(mc.textRenderer, mc.textRenderer.trimToWidth(extra, 176), 17, 88, 0xFFA3AEC4);
            draw.drawTextWithShadow(mc.textRenderer, "Auto Log " + state(autoLog) + " (" + (int)config.autoLogHealth + " HP)", 17, 102, 0xFFFFFFFF);
            draw.drawTextWithShadow(mc.textRenderer, "Right Shift: settings", 17, 117, 0xFFA3AEC4);
        });
    }
    public static String state(boolean value) { return value ? "ON" : "OFF"; }
    private void tick(MinecraftClient mc) {
        while (profilesKey.wasPressed()) mc.setScreen(new ProfileScreen(mc.currentScreen));
        while (menuKey.wasPressed()) if (mc.player != null) mc.setScreen(new ModuleScreen());
        if (mc.player == null || mc.world == null) {
            aura = flight = sprint = crystalAura = autoTotem = autoLog = false;
            appliedSprint = false; auraController.reset(); crystalController.reset(); totemController.reset(); return;
        }
        while (auraKey.wasPressed()) aura = !aura;
        while (flightKey.wasPressed()) flight = !flight;
        while (crystalKey.wasPressed()) crystalAura = !crystalAura;
        while (totemKey.wasPressed()) autoTotem = !autoTotem;
        while (logKey.wasPressed()) autoLog = !autoLog;
        if (CombatPolicy.shouldLog(autoLog, mc.player.isAlive(), mc.player.isSpectator(),
                mc.player.getHealth(), mc.player.getAbsorptionAmount(), config.autoLogHealth)) {
            autoLog = false;
            if (mc.getNetworkHandler() != null) mc.getNetworkHandler().getConnection()
                .disconnect(Text.literal("MC Client Auto Log: effective health reached " + config.autoLogHealth + " HP."));
            return;
        }
        boolean equippedTotem = totemController.tick(mc);
        if (mc.currentScreen != null) { auraController.reset(); return; }
        if (sprint && !(aura && config.criticalAura && auraController.hasTarget()) && mc.player.input.getMovementInput().y > 0 && !mc.player.isSneaking() && mc.player.getHungerManager().getFoodLevel() > 6) {
            mc.player.setSprinting(true); appliedSprint = true;
        } else if (appliedSprint) { mc.player.setSprinting(false); appliedSprint = false; }
        if (flight && !mc.player.isSpectator() && !mc.player.isGliding()) {
            Vec3d direction = new Vec3d(mc.player.input.getMovementInput().x, 0, mc.player.input.getMovementInput().y);
            if (direction.lengthSquared() > 1) direction = direction.normalize();
            direction = direction.rotateY((float) Math.toRadians(-mc.player.getYaw())).multiply(config.flightSpeed);
            double vertical = (mc.options.jumpKey.isPressed() ? config.flightSpeed : 0) - (mc.options.sneakKey.isPressed() ? config.flightSpeed : 0);
            mc.player.setVelocity(direction.x, vertical, direction.z);
        }
        if (!equippedTotem && !crystalController.tick(mc)) auraController.tick(mc);
    }
}
