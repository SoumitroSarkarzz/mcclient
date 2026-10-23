package dev.mcclient;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

public final class AutoTotemController {
    private int retry;
    private String status = "Idle";
    public String status() { return status; }
    public void reset() { retry = 0; status = "Idle"; }
    public boolean tick(MinecraftClient mc) {
        if (!McClient.autoTotem || mc.player == null || mc.interactionManager == null) { reset(); return false; }
        var p = mc.player;
        if (!p.isAlive() || p.isSpectator()) return false;
        if (p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) { status = "Offhand ready"; return false; }
        if (mc.currentScreen != null || p.currentScreenHandler != p.playerScreenHandler
            || !p.currentScreenHandler.getCursorStack().isEmpty()) { status = "Waiting for inventory"; return false; }
        if (retry > 0) { retry--; return false; }
        for (var slot : p.playerScreenHandler.slots) {
            if (slot.inventory == p.getInventory() && slot.getIndex() >= 0 && slot.getIndex() < 36
                && slot.getStack().isOf(Items.TOTEM_OF_UNDYING)) {
                mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, slot.id, 40, SlotActionType.SWAP, p);
                retry = 10; status = "Equipping totem"; return true;
            }
        }
        status = "No spare totem";
        return false;
    }
}
