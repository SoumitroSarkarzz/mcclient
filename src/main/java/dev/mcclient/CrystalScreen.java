package dev.mcclient;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class CrystalScreen extends Screen {
    private final Screen parent;
    private int left, top;
    public CrystalScreen(Screen parent) { super(Text.literal("Crystal / Safety")); this.parent = parent; }
    private void toggle(String name, int y, BooleanSupplier value, Runnable action) {
        button(() -> name + ": " + McClient.state(value.getAsBoolean()), left + 15, y, 290, action);
    }
    private void button(Supplier<String> name, int x, int y, int w, Runnable action) {
        addDrawableChild(ButtonWidget.builder(Text.literal(name.get()), b -> {
            action.run(); b.setMessage(Text.literal(name.get())); McClient.config.save();
        }).dimensions(x, y, w, 20).build());
    }
    @Override protected void init() {
        left = (width - 320) / 2; top = Math.max(3, (height - 232) / 2);
        var c = McClient.config;
        toggle("Crystal Aura", top + 46, () -> McClient.crystalAura, () -> McClient.crystalAura = !McClient.crystalAura);
        toggle("Auto Totem", top + 70, () -> McClient.autoTotem, () -> McClient.autoTotem = !McClient.autoTotem);
        toggle("Auto Log", top + 94, () -> McClient.autoLog, () -> McClient.autoLog = !McClient.autoLog);
        button(() -> String.format(java.util.Locale.ROOT, "Crystal reach: %.1f", c.crystalRange), left + 15, top + 118, 142,
            () -> c.crystalRange = c.crystalRange >= 5 ? 2 : c.crystalRange + 0.5);
        button(() -> "Log at: " + (int)c.autoLogHealth + " HP", left + 163, top + 118, 142,
            () -> c.autoLogHealth = c.autoLogHealth >= 18 ? 2 : c.autoLogHealth + 2);
        button(() -> "Self cap: " + (int)c.crystalMaxSelfDamage + " HP", left + 15, top + 142, 142,
            () -> c.crystalMaxSelfDamage = c.crystalMaxSelfDamage >= 12 ? 2 : c.crystalMaxSelfDamage + 2);
        button(() -> "Min hit: " + (int)c.crystalMinDamage + " HP", left + 163, top + 142, 142,
            () -> c.crystalMinDamage = c.crystalMinDamage >= 20 ? 2 : c.crystalMinDamage + 2);
        button(() -> "Reserve: " + (int)c.crystalReserve + " HP", left + 15, top + 166, 142,
            () -> c.crystalReserve = c.crystalReserve >= 16 ? 2 : c.crystalReserve + 2);
        button(() -> "Delay: " + c.crystalDelay + " ticks", left + 163, top + 166, 142,
            () -> c.crystalDelay = c.crystalDelay >= 20 ? 2 : c.crystalDelay + 2);
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close()).dimensions(left + 15, top + 198, 290, 20).build());
    }
    @Override public boolean shouldPause() { return false; }
    @Override public void close() { McClient.config.save(); client.setScreen(parent); }
    @Override public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
        draw.fill(0, 0, width, height, 0xF0101724);
        draw.drawTextWithShadow(textRenderer, "CRYSTAL / SURVIVAL MODULES", left + 15, top + 14, 0xFF64D8FF);
        draw.drawTextWithShadow(textRenderer, "HP, not hearts. Crystal damage is estimated.", left + 15, top + 30, 0xFFA3AEC4);
        super.render(draw, mouseX, mouseY, delta);
    }
}
