package com.example.ps5controller;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

/** Press up, down, left, right once each. Stored separate from the sticks. */
public class DpadMapScreen extends Screen {
    private final Screen parent;
    private static final String[] STEPS = {"Press D-pad UP", "Press D-pad DOWN", "Press D-pad LEFT", "Press D-pad RIGHT"};

    public DpadMapScreen(Screen parent) {
        super(new LiteralText("Map D-pad"));
        this.parent = parent;
        PS5ControllerClient.mappingDpad = 1;
    }

    @Override
    protected void init() {
        this.addDrawableChild(new ButtonWidget(this.width / 2 - 100, this.height - 32, 200, 20,
                new LiteralText("Cancel"), b -> this.onClose()));
    }

    @Override
    public void onClose() {
        PS5ControllerClient.mappingDpad = 0;
        this.client.setScreen(parent);
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        int step = PS5ControllerClient.mappingDpad;
        if (step >= 1 && step <= 4 && this.client != null) {
            int hit = PS5ControllerClient.rawDpadEdge();
            if (hit >= 0) {
                if (step == 1) PS5ControllerClient.mapUp = hit;
                if (step == 2) PS5ControllerClient.mapDown = hit;
                if (step == 3) PS5ControllerClient.mapLeft = hit;
                if (step == 4) PS5ControllerClient.mapRight = hit;
                PS5ControllerClient.mappingDpad = step + 1;
                if (PS5ControllerClient.mappingDpad > 4) {
                    PS5ControllerClient.applyNormal();
                    PS5ControllerClient.mappingDpad = 0;
                    this.client.setScreen(parent);
                    return;
                }
            }
        }
        this.renderBackground(matrices);
        drawCenteredText(matrices, this.textRenderer, this.title, this.width / 2, 40, 0xFFFFFF);
        int s = PS5ControllerClient.mappingDpad;
        String line = s >= 1 && s <= 4 ? STEPS[s - 1] : "Saved";
        drawCenteredText(matrices, this.textRenderer, new LiteralText(line), this.width / 2, 70, 0xFFFF55);
        drawCenteredText(matrices, this.textRenderer, new LiteralText("Sticks are not used for this"), this.width / 2, 90, 0xAAAAAA);
        super.render(matrices, mouseX, mouseY, delta);
    }
}
