package com.example.ps5controller;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

public class ControlsScreen extends Screen {
    private final Screen parent;
    private static final String[] LIST = {
            "Left stick: move in game, mouse in menus",
            "Right stick: look in game",
            "Touchpad: mouse in menus, press to click",
            "D-pad: menus and inventory only",
            "Cross: jump / select",
            "Circle: drop in game, back in menus",
            "Square: swap hands",
            "Triangle: inventory / move whole stack",
            "L2 use, R2 attack",
            "L1 / R1: hotbar",
            "L3 sneak, R3 sprint",
            "Options: pause"
    };

    public ControlsScreen(Screen parent) {
        super(new LiteralText("Control checklist"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int y = 36;
        for (int i = 0; i < PS5ControllerClient.ACT.length; i++) {
            final int action = i;
            String label = PS5ControllerClient.ACT[i] + ": " + PS5ControllerClient.buttonName(PS5ControllerClient.BINDS[i]);
            if (label.length() > 34) label = label.substring(0, 34);
            this.addDrawableChild(new ButtonWidget(this.width / 2 - 160 + (i % 2) * 165, y + (i / 2) * 22, 160, 20,
                    new LiteralText(label), b -> PS5ControllerClient.listening = action));
        }
        this.addDrawableChild(new ButtonWidget(this.width / 2 - 100, this.height - 28, 200, 20, new LiteralText("Done"), b -> this.onClose()));
    }

    @Override
    public void onClose() {
        PS5ControllerClient.listening = -1;
        this.client.setScreen(parent);
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        if (PS5ControllerClient.listening >= 0) {
            int hit = PS5ControllerClient.edgeButton();
            if (hit >= 0) {
                PS5ControllerClient.setBind(PS5ControllerClient.listening, hit);
                PS5ControllerClient.listening = -1;
                this.init(this.client, this.width, this.height);
            }
        }
        this.renderBackground(matrices);
        drawCenteredText(matrices, this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFF);
        int y = 148;
        for (String line : LIST) {
            drawCenteredText(matrices, this.textRenderer, new LiteralText(line), this.width / 2, y, 0xAAAAAA);
            y += 10;
        }
        super.render(matrices, mouseX, mouseY, delta);
    }
}
