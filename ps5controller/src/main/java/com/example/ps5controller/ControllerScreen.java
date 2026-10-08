package com.example.ps5controller;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

public class ControllerScreen extends Screen {
    private final Screen parent;
    public ControllerScreen(Screen parent) { super(new LiteralText("Controls")); this.parent = parent; }
    @Override protected void init() {
        int x = this.width / 2 - 110, y = 78;
        this.addDrawableChild(new ButtonWidget(x, y, 220, 20, new LiteralText("Map buttons"), b -> this.client.setScreen(new ControlsScreen(this))));
        this.addDrawableChild(new ButtonWidget(x, y + 24, 220, 20, new LiteralText("Map D-pad"), b -> this.client.setScreen(new DpadMapScreen(this))));
        this.addDrawableChild(new ButtonWidget(x, y + 48, 220, 20, new LiteralText("Reset"), b -> PS5ControllerClient.applyDefault()));
        this.addDrawableChild(new ButtonWidget(x, this.height - 32, 220, 20, new LiteralText("Done"), b -> this.onClose()));
    }
    @Override public void onClose() { this.client.setScreen(parent); }
    @Override public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        String status = PS5ControllerClient.connected ? "Connected: " + PS5ControllerClient.padName : "PS5 not connected";
        drawCenteredText(matrices, this.textRenderer, this.title, this.width / 2, 12, 0xFFFFFF);
        drawCenteredText(matrices, this.textRenderer, new LiteralText(status), this.width / 2, 28, PS5ControllerClient.connected ? 0x55FF55 : 0xFF5555);
        int y = 42;
        java.util.List<String> pads = PS5ControllerClient.pads();
        if (pads.isEmpty()) drawCenteredText(matrices, this.textRenderer, new LiteralText("No controllers found"), this.width / 2, y, 0xAAAAAA);
        else for (String p : pads) { drawCenteredText(matrices, this.textRenderer, new LiteralText(p), this.width / 2, y, 0xFFFFFF); y += 10; }
        super.render(matrices, mouseX, mouseY, delta);
    }
}
