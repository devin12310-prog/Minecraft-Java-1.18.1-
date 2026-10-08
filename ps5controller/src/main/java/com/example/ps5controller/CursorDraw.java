package com.example.ps5controller;

import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;

final class CursorDraw extends DrawableHelper {
    static void plus(MatrixStack m, double x, double y) {
        int ix = (int) x, iy = (int) y;
        fill(m, ix - 4, iy, ix + 5, iy + 1, 0xFFFFFFFF);
        fill(m, ix, iy - 4, ix + 1, iy + 5, 0xFFFFFFFF);
    }
}
