package com.example.ps5controller;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

public class ControlsScreen extends Screen {
    private final Screen parent;
    private int row;
    private long arm;
    public ControlsScreen(Screen parent) { super(new LiteralText("Map buttons")); this.parent = parent; }

    @Override protected void init() {
        int y = 78;
        for (int i = 0; i < PS5ControllerClient.ACT.length; i++) {
            final int action = i;
            String label = PS5ControllerClient.ACT[i] + ": " + PS5ControllerClient.buttonName(PS5ControllerClient.BINDS[i]);
            this.addDrawableChild(new ButtonWidget(this.width / 2 - 110, y + i * 22, 220, 20, new LiteralText(label), b -> arm(action)));
        }
        this.addDrawableChild(new ButtonWidget(this.width / 2 - 110, this.height - 28, 220, 20, new LiteralText("Done"), b -> this.onClose()));
    }

    private void arm(int action) {
        row = action;
        PS5ControllerClient.listening = action;
        arm = System.currentTimeMillis() + 300;
    }

    @Override public void tick() {
        if (PS5ControllerClient.listening < 0 || System.currentTimeMillis() < arm) return;
        int hit = PS5ControllerClient.edgeButton();
        if (hit < 0) return;
        PS5ControllerClient.setBind(PS5ControllerClient.listening, hit);
        PS5ControllerClient.listening = -1;
        this.init(this.client, this.width, this.height);
    }

    @Override public void onClose() { PS5ControllerClient.listening = -1; this.client.setScreen(parent); }

    @Override public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        drawCenteredText(matrices, this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFF);
        if (PS5ControllerClient.listening >= 0) {
            String name = PS5ControllerClient.ACT[PS5ControllerClient.listening];
            drawCenteredText(matrices, this.textRenderer, new LiteralText("Press the pad button for " + name), this.width / 2, 28, 0xFFFF55);
            drawCenteredText(matrices, this.textRenderer, new LiteralText("It saves as soon as you press it"), this.width / 2, 42, 0xAAAAAA);
        } else {
            drawCenteredText(matrices, this.textRenderer, new LiteralText("Click an action, then press the pad button"), this.width / 2, 32, 0xAAAAAA);
        }
        super.render(matrices, mouseX, mouseY, delta);
    }
}
