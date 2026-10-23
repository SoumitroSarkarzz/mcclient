package dev.mcclient;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import java.util.function.BooleanSupplier;

public final class AuraScreen extends Screen {
    private final Screen parent;
    private int left, top;
    public AuraScreen(Screen parent) { super(Text.literal("Aura settings")); this.parent = parent; }
    private void toggle(String name, int y, BooleanSupplier value, Runnable action) {
        addDrawableChild(ButtonWidget.builder(Text.literal(name + ": " + McClient.state(value.getAsBoolean())), b -> {
            action.run(); b.setMessage(Text.literal(name + ": " + McClient.state(value.getAsBoolean()))); McClient.config.save();
        }).dimensions(left + 15, y, 290, 20).build());
    }
    @Override protected void init() {
        left = (width - 320) / 2; top = Math.max(5, (height - 210) / 2);
        toggle("Target players", top + 46, () -> McClient.config.targetPlayers, () -> McClient.config.targetPlayers = !McClient.config.targetPlayers);
        toggle("Critical hits only", top + 70, () -> McClient.config.criticalAura, () -> McClient.config.criticalAura = !McClient.config.criticalAura);
        toggle("Auto-jump for criticals", top + 94, () -> McClient.config.autoJumpCrits, () -> McClient.config.autoJumpCrits = !McClient.config.autoJumpCrits);
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close()).dimensions(left + 15, top + 172, 290, 20).build());
    }
    @Override public boolean shouldPause() { return false; }
    @Override public void close() { McClient.config.save(); client.setScreen(parent); }
    @Override public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
        draw.fill(0, 0, width, height, 0xF0101724);
        draw.drawTextWithShadow(textRenderer, "AURA / TARGETING & CRITICALS", left + 15, top + 15, 0xFF64D8FF);
        draw.drawTextWithShadow(textRenderer, "360 targeting; no manual aim needed", left + 15, top + 30, 0xFFA3AEC4);
        draw.drawTextWithShadow(textRenderer, "Crits need falling, charge and no sprint.", left + 15, top + 128, 0xFFA3AEC4);
        draw.drawTextWithShadow(textRenderer, "Flight/water/ladders block critical mode.", left + 15, top + 142, 0xFFA3AEC4);
        draw.drawTextWithShadow(textRenderer, "Normal mode: turn Critical hits only OFF.", left + 15, top + 156, 0xFFA3AEC4);
        super.render(draw, mouseX, mouseY, delta);
    }
}
