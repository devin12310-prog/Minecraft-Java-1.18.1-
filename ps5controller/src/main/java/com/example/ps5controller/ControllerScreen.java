package com.example.ps5controller;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

public class ControllerScreen extends Screen {
    private final Screen parent;

    public ControllerScreen(Screen parent) {
        super(new LiteralText("Controller"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 110;
        int y = 50;
        this.addDrawableChild(new ButtonWidget(x, y, 220, 20, new LiteralText("Control checklist"), b -> this.client.setScreen(new ControlsScreen(this))));
        this.addDrawableChild(new ButtonWidget(x, y + 24, 220, 20, new LiteralText("Map D-pad"), b -> this.client.setScreen(new DpadMapScreen(this))));
        this.addDrawableChild(new ButtonWidget(x, y + 48, 220, 20, new LiteralText("Reset layout"), b -> PS5ControllerClient.applyPs3()));
        this.addDrawableChild(new ButtonWidget(x, this.height - 32, 220, 20, new LiteralText("Done"), b -> this.onClose()));
    }

    @Override
    public void onClose() { this.client.setScreen(parent); }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        drawCenteredText(matrices, this.textRenderer, this.title, this.width / 2, 16, 0xFFFFFF);
        drawCenteredText(matrices, this.textRenderer, new LiteralText("PS5 buttons. Sticks are not the D-pad"), this.width / 2, 32, 0xAAAAAA);
        super.render(matrices, mouseX, mouseY, delta);
    }
}
