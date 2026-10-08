package com.example.ps5controller;

import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;

final class CursorDraw extends DrawableHelper {
    static void plus(MatrixStack m, double x, double y) {
        int cx = (int) x, cy = (int) y;
        fill(m, cx - 4, cy, cx + 5, cy + 1, 0xFFFFFFFF);
        fill(m, cx, cy - 4, cx + 1, cy + 5, 0xFFFFFFFF);
    }
    static void box(MatrixStack m, int x, int y, int w, int h) {
        fill(m, x - 1, y - 1, x + w + 1, y, 0xFFFFFF55);
        fill(m, x - 1, y + h, x + w + 1, y + h + 1, 0xFFFFFF55);
        fill(m, x - 1, y, x, y + h, 0xFFFFFF55);
        fill(m, x + w, y, x + w + 1, y + h, 0xFFFFFF55);
    }
}
