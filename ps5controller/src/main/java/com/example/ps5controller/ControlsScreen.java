package com.example.ps5controller;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

public class ControlsScreen extends Screen {
    private final Screen parent;
    private final ButtonWidget[] rows = new ButtonWidget[PS5ControllerClient.ACT.length];
    private long arm;
    private String saved = "";
    public ControlsScreen(Screen parent) { super(new LiteralText("Map buttons")); this.parent = parent; }

    @Override protected void init() {
        int left = this.width / 2 - 210;
        int right = this.width / 2 + 10;
        for (int i = 0; i < PS5ControllerClient.ACT.length; i++) {
            final int action = i;
            int x = i < 6 ? left : right;
            int y = 70 + (i % 6) * 24;
            rows[i] = this.addDrawableChild(new ButtonWidget(x, y, 200, 20, new LiteralText(label(i)), b -> arm(action)));
        }
        this.addDrawableChild(new ButtonWidget(this.width / 2 - 100, this.height - 28, 200, 20, new LiteralText("Done"), b -> this.onClose()));
    }

    private String label(int i) {
        return PS5ControllerClient.ACT[i] + ": " + PS5ControllerClient.buttonName(PS5ControllerClient.BINDS[i]);
    }

    private void arm(int action) {
        PS5ControllerClient.listening = action;
        saved = "Press a button for " + PS5ControllerClient.ACT[action];
        arm = System.currentTimeMillis() + 250;
    }

    @Override public void tick() {
        if (PS5ControllerClient.listening < 0 || System.currentTimeMillis() < arm) return;
        int hit = PS5ControllerClient.edgeButton();
        if (hit < 0) hit = PS5ControllerClient.dpadEdge();
        if (hit < 0) return;
        int action = PS5ControllerClient.listening;
        PS5ControllerClient.setBind(action, hit);
        PS5ControllerClient.listening = -1;
        saved = "Saved " + label(action);
        if (action >= 0 && action < rows.length && rows[action] != null) rows[action].setMessage(new LiteralText(label(action)));
    }

    @Override public void onClose() { PS5ControllerClient.listening = -1; this.client.setScreen(parent); }

    @Override public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        drawCenteredText(matrices, this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFF);
        String line = saved.isEmpty() ? "Click an action, then press the pad. The name changes when it saves." : saved;
        drawCenteredText(matrices, this.textRenderer, new LiteralText(line), this.width / 2, 28, saved.startsWith("Saved") ? 0x55FF55 : 0xFFFFFF);
        super.render(matrices, mouseX, mouseY, delta);
    }
}
