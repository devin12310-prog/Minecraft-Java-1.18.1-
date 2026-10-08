package com.example.ps5controller;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

final class ScreenPad {
    static boolean cross(MinecraftClient mc, Screen screen, double x, double y) {
        if (pressHovered(screen)) return true;
        return screen.mouseClicked(x, y, 0);
    }

    static boolean page(Screen screen, boolean next, double x, double y) {
        if (pressPage(screen, next)) return true;
        ButtonWidget arrow = nearestArrow(screen, next);
        if (arrow == null) return false;
        return screen.mouseClicked(arrow.x + arrow.getWidth() / 2.0, arrow.y + arrow.getHeight() / 2.0, 0);
    }

    static void quick(MinecraftClient mc, HandledScreen<?> screen, double x, double y) {
        Slot slot = under(screen, x, y);
        if (slot == null || mc.player == null || mc.interactionManager == null) return;
        mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, slot.id, 0, SlotActionType.QUICK_MOVE, mc.player);
    }

    static void swap(MinecraftClient mc, HandledScreen<?> screen, double x, double y) {
        Slot slot = under(screen, x, y);
        if (slot == null || mc.player == null || mc.interactionManager == null) return;
        mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, slot.id, mc.player.getInventory().selectedSlot, SlotActionType.SWAP, mc.player);
    }

    private static boolean pressHovered(Screen screen) {
        for (Element e : screen.children()) {
            if (!(e instanceof ButtonWidget)) continue;
            ButtonWidget b = (ButtonWidget) e;
            if (b.visible && b.active && b.isHovered()) { b.onPress(); return true; }
        }
        return false;
    }

    private static boolean pressPage(Screen screen, boolean next) {
        ButtonWidget side = null;
        for (Element e : screen.children()) {
            if (!(e instanceof ButtonWidget)) continue;
            ButtonWidget b = (ButtonWidget) e;
            if (!b.visible || !b.active || b.getWidth() > 40) continue;
            String msg = b.getMessage().getString().toLowerCase();
            if (next && msg.contains("next")) { b.onPress(); return true; }
            if (!next && (msg.contains("prev") || msg.contains("previous"))) { b.onPress(); return true; }
            if (next && b.x >= screen.width / 2) side = b;
            if (!next && b.x < screen.width / 2) side = b;
        }
        if (side != null) { side.onPress(); return true; }
        return false;
    }

    private static ButtonWidget nearestArrow(Screen screen, boolean next) {
        ButtonWidget best = null;
        for (Element e : screen.children()) {
            if (!(e instanceof ButtonWidget)) continue;
            ButtonWidget b = (ButtonWidget) e;
            if (!b.visible || b.getWidth() > 40) continue;
            if (next && b.x < screen.width / 2) continue;
            if (!next && b.x >= screen.width / 2) continue;
            best = b;
        }
        return best;
    }

    private static Slot under(HandledScreen<?> screen, double mx, double my) {
        int left = (screen.width - 176) / 2, top = 0;
        try {
            var fx = HandledScreen.class.getDeclaredField("x");
            var fy = HandledScreen.class.getDeclaredField("y");
            fx.setAccessible(true); fy.setAccessible(true);
            left = fx.getInt(screen); top = fy.getInt(screen);
        } catch (Throwable ignored) {
            int max = 0;
            for (Slot s : screen.getScreenHandler().slots) if (s.y > max) max = s.y;
            top = (screen.height - (max + 26)) / 2;
        }
        for (Slot s : screen.getScreenHandler().slots) {
            if (mx >= left + s.x && mx < left + s.x + 16 && my >= top + s.y && my < top + s.y + 16) return s;
        }
        return null;
    }
}
