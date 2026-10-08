package com.example.ps5controller;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

import java.util.List;

/** Picker plus a button into the remap screen. */
public class ControllerScreen extends Screen {
    private final Screen parent;

    public ControllerScreen(Screen parent) {
        super(new LiteralText("Controller"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 110;
        int y = 56;

        String autoLabel = (PS5ControllerClient.isAuto() ? "> " : "") + "Automatic";
        this.addDrawableChild(new ButtonWidget(x, y, 220, 20, new LiteralText(autoLabel), b -> {
            PS5ControllerClient.setPreferredName(null);
            this.init(this.client, this.width, this.height);
        }));
        y += 24;

        List<Integer> pads = PS5ControllerClient.usablePads();
        int shown = 0;
        for (int jid : pads) {
            if (shown >= 6) break;
            final String name = PS5ControllerClient.nameOf(jid);
            boolean selected = !PS5ControllerClient.isAuto() && name.equals(PS5ControllerClient.getPreferredName());
            String label = (selected ? "> " : "") + (name.length() > 28 ? name.substring(0, 28) : name);
            this.addDrawableChild(new ButtonWidget(x, y, 220, 20, new LiteralText(label), b -> {
                PS5ControllerClient.setPreferredName(name);
                this.init(this.client, this.width, this.height);
            }));
            y += 24;
            shown++;
        }

        this.addDrawableChild(new ButtonWidget(x, this.height - 56, 220, 20, new LiteralText("Change controls"), b ->
                this.client.setScreen(new ControlsScreen(this))));
        this.addDrawableChild(new ButtonWidget(x, this.height - 32, 220, 20, new LiteralText("Done"), b -> this.onClose()));
    }

    @Override
    public void onClose() {
        this.client.setScreen(parent);
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        drawCenteredText(matrices, this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);
        String status = PS5ControllerClient.isActive()
                ? "In use: " + PS5ControllerClient.activeName()
                : "No controller in use. Pair one over Bluetooth.";
        drawCenteredText(matrices, this.textRenderer, new LiteralText(status), this.width / 2, 36, 0xAAAAAA);
        super.render(matrices, mouseX, mouseY, delta);
    }
}
