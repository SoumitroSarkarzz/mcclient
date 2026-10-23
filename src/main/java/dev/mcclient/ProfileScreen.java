package dev.mcclient;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import java.io.IOException;
import java.net.URI;

public final class ProfileScreen extends Screen {
    private final Screen parent;
    private ProfileStore store;
    private TextFieldWidget label, username;
    private String message = "Local profiles are test data. Your launcher account stays unchanged.";
    private int left, top, panelWidth;

    public ProfileScreen(Screen parent) { super(Text.literal("MC Profiles")); this.parent = parent; }

    @Override protected void init() {
        panelWidth = Math.min(520, width - 20);
        left = (width - panelWidth) / 2;
        top = Math.max(5, (height - 240) / 2);
        try { store = new ProfileStore(FabricLoader.getInstance().getConfigDir().resolve("mcprofiles")); }
        catch (IOException error) { store = null; message = "Settings unreadable; preserved. Back up config/mcprofiles/profiles.json."; }
        int half = (panelWidth - 30) / 2;
        button("< Previous", left + 10, top + 60, half, () -> action(() -> store.cycle(-1), "Test profile selected."), store != null && !store.profiles().isEmpty());
        button("Next >", left + 20 + half, top + 60, half, () -> action(() -> store.cycle(1), "Test profile selected."), store != null && !store.profiles().isEmpty());
        label = new TextFieldWidget(textRenderer, left + 10, top + 108, half, 20, Text.literal("Profile label"));
        label.setMaxLength(40);
        username = new TextFieldWidget(textRenderer, left + 20 + half, top + 108, half, 20, Text.literal("Offline test username"));
        username.setMaxLength(16);
        addDrawableChild(label);
        addDrawableChild(username);
        ProfileStore.Profile p = store == null ? null : store.selected();
        if (p != null) { label.setText(p.label()); username.setText(p.username()); }
        int third = (panelWidth - 40) / 3;
        button("Add new", left + 10, top + 136, third, () -> action(() -> store.add(label.getText(), username.getText()), "Offline test profile added."), store != null);
        button("Save edits", left + 20 + third, top + 136, third, () -> action(() -> store.update(label.getText(), username.getText()), "Label and test username saved."), p != null);
        button("Delete", left + 30 + 2 * third, top + 136, third, this::confirmDelete, p != null);
        button("Export test fixture", left + 10, top + 162, half, () -> action(() -> store.exportFixture(), "Saved JSON to config/mcprofiles/test-fixtures. Not a game session."), p != null);
        button("Microsoft sign-in help", left + 20 + half, top + 162, half, () -> {
            client.setScreen(new ConfirmScreen(confirmed -> {
                client.setScreen(this);
                if (confirmed) Util.getOperatingSystem().open(URI.create("https://www.minecraft.net/en-us/login"));
            }, Text.literal("Official Microsoft / Minecraft sign-in"), Text.literal("Sign in through the official launcher and restart Minecraft to change accounts. This opens the official Minecraft website. The mod never collects passwords or replaces your session.")));
        }, true);
        button("Done", left + panelWidth / 2 - 60, top + 214, 120, this::close, true);
        setInitialFocus(label);
    }

    private void button(String title, int x, int y, int w, Runnable callback, boolean enabled) {
        ButtonWidget b = ButtonWidget.builder(Text.literal(title), ignored -> callback.run()).dimensions(x, y, w, 20).build();
        b.active = enabled;
        addDrawableChild(b);
    }

    private interface Operation { void run() throws IOException; }
    private void action(Operation operation, String success) {
        try { operation.run(); message = success; clearAndInit(); }
        catch (IllegalArgumentException error) { message = error.getMessage(); }
        catch (IOException error) { message = "Could not save profiles. Check config folder permissions and disk space."; }
    }

    private void confirmDelete() {
        if (store == null || store.selected() == null) return;
        client.setScreen(new ConfirmScreen(confirmed -> {
            client.setScreen(this);
            if (confirmed) action(() -> store.delete(), "Local test profile deleted. Your Minecraft account was not changed.");
        }, Text.literal("Delete local test profile?"), Text.literal(store.selected().label() + " will be removed from this device.")));
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void close() { client.setScreen(parent); }
    @Override public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
        draw.fill(0, 0, width, height, 0xF0101724);
        draw.drawTextWithShadow(textRenderer, "MC PROFILES / LOCAL TEST DATA", left + 10, top + 8, 0xFF64D8FF);
        draw.drawTextWithShadow(textRenderer, "Launcher player: " + client.getSession().getUsername(), left + 10, top + 26, 0xFFFFFFFF);
        draw.drawTextWithShadow(textRenderer, "Authentication stays with the launcher and server.", left + 10, top + 40, 0xFFA3AEC4);
        String selected = store == null || store.selected() == null ? "No local profile selected" : "OFFLINE / TEST: " + store.selected().label();
        draw.drawTextWithShadow(textRenderer, selected, left + 10, top + 85, 0xFFFFD479);
        draw.drawTextWithShadow(textRenderer, "Local label", left + 10, top + 97, 0xFFA3AEC4);
        draw.drawTextWithShadow(textRenderer, "Test username (not login)", left + 20 + (panelWidth - 30) / 2, top + 97, 0xFFA3AEC4);
        draw.drawWrappedTextWithShadow(textRenderer, Text.literal(message), left + 10, top + 189, panelWidth - 20, 0xFFA3AEC4);
        super.render(draw, mouseX, mouseY, delta);
    }
}
