package com.example.ps5controller;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

public class ControllerScreen extends Screen {
    private final Screen parent;
    private int page;
    public ControllerScreen(Screen parent) { super(new LiteralText("Controller")); this.parent = parent; }

    @Override protected void init() {
        int x = this.width / 2 - 110, y = 96;
        this.addDrawableChild(new ButtonWidget(this.width / 2 - 155, 28, 74, 20, new LiteralText("Status"), b -> { page = 0; this.init(client, width, height); }));
        this.addDrawableChild(new ButtonWidget(this.width / 2 - 77, 28, 74, 20, new LiteralText("Feel"), b -> { page = 1; this.init(client, width, height); }));
        this.addDrawableChild(new ButtonWidget(this.width / 2 + 1, 28, 74, 20, new LiteralText("HUD"), b -> { page = 2; this.init(client, width, height); }));
        this.addDrawableChild(new ButtonWidget(this.width / 2 + 79, 28, 74, 20, new LiteralText("Binds"), b -> { page = 3; this.init(client, width, height); }));
        if (page == 1) {
            this.addDrawableChild(new ButtonWidget(x, y, 220, 20, new LiteralText("Deadzone: " + PS5ControllerClient.deadzone), b -> PS5ControllerClient.cycleDeadzone()));
            this.addDrawableChild(new ButtonWidget(x, y + 24, 220, 20, new LiteralText("Look speed: " + PS5ControllerClient.lookSpeed), b -> PS5ControllerClient.cycleLook()));
            this.addDrawableChild(new ButtonWidget(x, y + 48, 220, 20, new LiteralText("Menu speed: " + PS5ControllerClient.menuSpeed), b -> PS5ControllerClient.cycleMenu()));
            this.addDrawableChild(new ButtonWidget(x, y + 72, 220, 20, new LiteralText("Invert look: " + (PS5ControllerClient.invertY ? "On" : "Off")), b -> PS5ControllerClient.toggleInvert()));
        } else if (page == 2) {
            this.addDrawableChild(new ButtonWidget(x, y, 220, 20, new LiteralText("Button guide: " + (PS5ControllerClient.showGuide ? "On" : "Off")), b -> PS5ControllerClient.toggleGuide()));
            this.addDrawableChild(new ButtonWidget(x, y + 24, 220, 20, new LiteralText("Guide: " + (PS5ControllerClient.guideFull ? "Full" : "Minimal")), b -> PS5ControllerClient.toggleGuideSize()));
            this.addDrawableChild(new ButtonWidget(x, y + 48, 220, 20, new LiteralText("Connection line: " + (PS5ControllerClient.showStatus ? "On" : "Off")), b -> PS5ControllerClient.toggleStatus()));
        } else if (page == 3) {
            this.addDrawableChild(new ButtonWidget(x, y, 220, 20, new LiteralText("Map every button"), b -> this.client.setScreen(new ControlsScreen(this))));
            this.addDrawableChild(new ButtonWidget(x, y + 24, 220, 20, new LiteralText("Map D-pad, all 4"), b -> this.client.setScreen(new DpadMapScreen(this))));
            this.addDrawableChild(new ButtonWidget(x, y + 48, 220, 20, new LiteralText("Reset DualSense layout"), b -> PS5ControllerClient.applyDefault()));
        }
        this.addDrawableChild(new ButtonWidget(x, this.height - 28, 220, 20, new LiteralText("Done"), b -> this.onClose()));
    }

    @Override public void onClose() { this.client.setScreen(parent); }

    @Override public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        drawCenteredText(matrices, this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFF);
        if (page == 0) {
            String status = PS5ControllerClient.connected ? "Connected: " + PS5ControllerClient.padName : "No controller connected";
            drawCenteredText(matrices, this.textRenderer, new LiteralText(status), this.width / 2, 54, PS5ControllerClient.connected ? 0x55FF55 : 0xFF5555);
            int y = 68;
            java.util.List<String> pads = PS5ControllerClient.pads();
            if (pads.isEmpty()) drawCenteredText(matrices, this.textRenderer, new LiteralText("Turn the DualSense on, then reopen"), this.width / 2, y, 0xAAAAAA);
            else for (String p : pads) { drawCenteredText(matrices, this.textRenderer, new LiteralText(p), this.width / 2, y, 0xFFFFFF); y += 10; }
            drawCenteredText(matrices, this.textRenderer, new LiteralText("Cross Circle Square Triangle  L1 R1 L2 R2"), this.width / 2, y + 8, 0xAAAAAA);
            drawCenteredText(matrices, this.textRenderer, new LiteralText("L3 R3  Create Options  Touchpad  Mute  D-pad"), this.width / 2, y + 18, 0xAAAAAA);
        }
        super.render(matrices, mouseX, mouseY, delta);
    }
}
