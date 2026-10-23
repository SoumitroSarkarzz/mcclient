package dev.mcclient;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import java.util.function.BooleanSupplier;

public final class ModuleScreen extends Screen {
    private int left, top;
    public ModuleScreen() { super(Text.literal("MC Client")); }
    private void toggle(String label, int y, BooleanSupplier value, Runnable action) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label + ": " + McClient.state(value.getAsBoolean())), button -> {
            action.run(); button.setMessage(Text.literal(label + ": " + McClient.state(value.getAsBoolean()))); McClient.config.save();
        }).dimensions(left + 20, y, 280, 20).build());
    }
    @Override protected void init() {
        left = (width - 320) / 2; top = Math.max(10, (height - 290) / 2);
        toggle("Kill Aura [R]", top + 46, () -> McClient.aura, () -> McClient.aura = !McClient.aura);
        toggle("Flight [G]", top + 70, () -> McClient.flight, () -> McClient.flight = !McClient.flight);
        toggle("Auto Sprint", top + 94, () -> McClient.sprint, () -> McClient.sprint = !McClient.sprint);
        toggle("HUD", top + 118, () -> McClient.config.hud, () -> McClient.config.hud = !McClient.config.hud);
        toggle("Target players", top + 142, () -> McClient.config.targetPlayers, () -> McClient.config.targetPlayers = !McClient.config.targetPlayers);
        addDrawableChild(ButtonWidget.builder(rangeLabel(), b -> {
            McClient.config.range = McClient.config.range >= 6 ? 1 : McClient.config.range + 0.5;
            b.setMessage(rangeLabel()); McClient.config.save();
        }).dimensions(left + 20, top + 166, 138, 20).build());
        addDrawableChild(ButtonWidget.builder(speedLabel(), b -> {
            McClient.config.flightSpeed = McClient.config.flightSpeed >= 0.95 ? 0.1 : Math.round((McClient.config.flightSpeed + 0.1) * 10) / 10.0;
            b.setMessage(speedLabel()); McClient.config.save();
        }).dimensions(left + 162, top + 166, 138, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Aura options"), b -> client.setScreen(new AuraScreen(this)))
            .dimensions(left + 20, top + 194, 138, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("MC Profiles [F8]"), b -> client.setScreen(new ProfileScreen(this)))
            .dimensions(left + 162, top + 194, 138, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close()).dimensions(left + 20, top + 244, 280, 20).build());
    }
    private Text rangeLabel() { return Text.literal(String.format(java.util.Locale.ROOT, "Range: %.1f", McClient.config.range)); }
    private Text speedLabel() { return Text.literal(String.format(java.util.Locale.ROOT, "Flight speed: %.1f", McClient.config.flightSpeed)); }
    @Override public boolean shouldPause() { return false; }
    @Override public void close() { McClient.config.save(); super.close(); }
    @Override public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
        draw.fill(0, 0, width, height, 0xA0080B12);
        draw.fill(left, top, left + 320, top + 278, 0xF0182030);
        draw.fill(left, top, left + 320, top + 3, 0xFF64D8FF);
        draw.drawTextWithShadow(textRenderer, "MC CLIENT / MODULES", left + 20, top + 17, 0xFF64D8FF);
        draw.drawTextWithShadow(textRenderer, "Changes save automatically", left + 20, top + 32, 0xFFA3AEC4);
        draw.drawTextWithShadow(textRenderer, "Servers may reject module actions.", left + 20, top + 218, 0xFFA3AEC4);
        super.render(draw, mouseX, mouseY, delta);
    }
}
