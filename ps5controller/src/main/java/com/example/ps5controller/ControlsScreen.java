package com.example.ps5controller;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

public class ControlsScreen extends Screen {
    private final Screen parent;
    public ControlsScreen(Screen parent) { super(new LiteralText("Map buttons")); this.parent = parent; }
    @Override protected void init() {
        int y = 28;
        for (int i = 0; i < PS5ControllerClient.ACT.length; i++) {
            final int action = i;
            String label = PS5ControllerClient.ACT[i] + ": " + PS5ControllerClient.buttonName(PS5ControllerClient.BINDS[i]);
            if (label.length() > 28) label = label.substring(0, 28);
            this.addDrawableChild(new ButtonWidget(this.width / 2 - 155 + (i % 2) * 160, y + (i / 2) * 22, 155, 20, new LiteralText(label), b -> PS5ControllerClient.listening = action));
        }
        this.addDrawableChild(new ButtonWidget(this.width / 2 - 100, this.height - 28, 200, 20, new LiteralText("Done"), b -> this.onClose()));
    }
    @Override public void onClose() { PS5ControllerClient.listening = -1; this.client.setScreen(parent); }
    @Override public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        if (PS5ControllerClient.listening >= 0) {
            drawCenteredText(matrices, this.textRenderer, new LiteralText("Press a button"), this.width / 2, this.height - 46, 0xFFFF55);
            int hit = PS5ControllerClient.edgeButton();
            if (hit >= 0) {
                PS5ControllerClient.setBind(PS5ControllerClient.listening, hit);
                PS5ControllerClient.listening = -1;
                this.init(this.client, this.width, this.height);
            }
        }
        this.renderBackground(matrices);
        drawCenteredText(matrices, this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFF);
        super.render(matrices, mouseX, mouseY, delta);
    }
}
