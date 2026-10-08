package com.example.ps5controller;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

/** Remap gameplay buttons. Press a row, then press the controller button. */
public class ControlsScreen extends Screen {
    private final Screen parent;

    public ControlsScreen(Screen parent) {
        super(new LiteralText("Controller controls"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 160;
        int y = 40;
        this.addDrawableChild(new ButtonWidget(x, y, 150, 20, new LiteralText("Normal layout"), b -> {
            PS5ControllerClient.applyNormal();
            PS5ControllerClient.listeningAction = -1;
            this.init(this.client, this.width, this.height);
        }));
        this.addDrawableChild(new ButtonWidget(x + 160, y, 160, 20, new LiteralText("L3 crouch layout"), b -> {
            PS5ControllerClient.applyStickCrouch();
            PS5ControllerClient.listeningAction = -1;
            this.init(this.client, this.width, this.height);
        }));
        y += 28;
        for (int i = 0; i < PS5ControllerClient.ACT_NAMES.length; i++) {
            final int action = i;
            String name = PS5ControllerClient.ACT_NAMES[i];
            String bound = PS5ControllerClient.buttonName(PS5ControllerClient.BINDS[i]);
            String label = (PS5ControllerClient.listeningAction == action ? "> " : "") + name + ": " + bound;
            if (label.length() > 34) label = label.substring(0, 34);
            int col = i % 2;
            int row = i / 2;
            this.addDrawableChild(new ButtonWidget(x + col * 165, y + row * 22, 160, 20, new LiteralText(label), b -> {
                PS5ControllerClient.listeningAction = action;
                this.init(this.client, this.width, this.height);
            }));
        }
        this.addDrawableChild(new ButtonWidget(x, this.height - 32, 320, 20, new LiteralText("Done"), b -> this.onClose()));
    }

    @Override
    public void onClose() {
        PS5ControllerClient.listeningAction = -1;
        this.client.setScreen(parent);
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        if (PS5ControllerClient.listeningAction >= 0 && this.client != null) {
            int hit = PS5ControllerClient.pressedBindButton(false, false);
            if (hit >= 0 && hit != PS5ControllerClient.L2 && hit != PS5ControllerClient.R2) {
                PS5ControllerClient.setBind(PS5ControllerClient.listeningAction, hit);
                PS5ControllerClient.listeningAction = -1;
                this.init(this.client, this.width, this.height);
            }
        }
        this.renderBackground(matrices);
        drawCenteredText(matrices, this.textRenderer, this.title, this.width / 2, 12, 0xFFFFFF);
        String hint = PS5ControllerClient.listeningAction >= 0
                ? "Press a controller button for " + PS5ControllerClient.ACT_NAMES[PS5ControllerClient.listeningAction]
                : "Normal = Cross jump, Circle sneak, R2 attack, L2 use";
        drawCenteredText(matrices, this.textRenderer, new LiteralText(hint), this.width / 2, 26, 0xAAAAAA);
        super.render(matrices, mouseX, mouseY, delta);
    }
}
