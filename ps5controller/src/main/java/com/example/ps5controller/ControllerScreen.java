package com.example.ps5controller;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import org.lwjgl.glfw.GLFW;

public class ControllerScreen extends Screen {
    private final Screen parent;
    private int page;
    public ControllerScreen(Screen parent) { super(new LiteralText("Controller")); this.parent = parent; }

    @Override protected void init() {
        int x = this.width / 2 - 100, y = 78;
        this.addDrawableChild(new ButtonWidget(this.width / 2 - 155, 28, 74, 20, new LiteralText("Status"), b -> { page = 0; this.init(client, width, height); }));
        this.addDrawableChild(new ButtonWidget(this.width / 2 - 77, 28, 74, 20, new LiteralText("Feel"), b -> { page = 1; this.init(client, width, height); }));
        this.addDrawableChild(new ButtonWidget(this.width / 2 + 1, 28, 74, 20, new LiteralText("HUD"), b -> { page = 2; this.init(client, width, height); }));
        this.addDrawableChild(new ButtonWidget(this.width / 2 + 79, 28, 74, 20, new LiteralText("Binds"), b -> { page = 3; this.init(client, width, height); }));
        if (page == 0) {
            int i = 0;
            for (int id = 0; id <= GLFW.GLFW_JOYSTICK_LAST && i < 4; id++) {
                if (!GLFW.glfwJoystickPresent(id)) continue;
                final int pick = id;
                String n = GLFW.glfwGetJoystickName(id);
                if (n == null) n = "Controller";
                if (n.length() > 18) n = n.substring(0, 18);
                this.addDrawableChild(new ButtonWidget(x, y + 36 + i * 22, 200, 20, new LiteralText((pick == PS5ControllerClient.selected ? "* " : "") + n), b -> { PS5ControllerClient.usePad(pick); this.init(client, width, height); }));
                i++;
            }
        } else if (page == 1) {
            this.addDrawableChild(cycle(x, y, "Deadzone", () -> String.valueOf(PS5ControllerClient.deadzone), PS5ControllerClient::cycleDeadzone));
            this.addDrawableChild(cycle(x, y + 24, "Look speed", () -> String.valueOf(PS5ControllerClient.lookSpeed), PS5ControllerClient::cycleLook));
            this.addDrawableChild(cycle(x, y + 48, "Menu speed", () -> String.valueOf(PS5ControllerClient.menuSpeed), PS5ControllerClient::cycleMenu));
            this.addDrawableChild(onOff(x, y + 72, "Invert look", () -> PS5ControllerClient.invertY, PS5ControllerClient::toggleInvert));
        } else if (page == 2) {
            this.addDrawableChild(onOff(x, y, "Button guide", () -> PS5ControllerClient.showGuide, PS5ControllerClient::toggleGuide));
            this.addDrawableChild(onOff(x, y + 24, "Full guide", () -> PS5ControllerClient.guideFull, PS5ControllerClient::toggleGuideSize));
        } else if (page == 3) {
            this.addDrawableChild(new ButtonWidget(x, y, 200, 20, new LiteralText("Map every button"), b -> this.client.setScreen(new ControlsScreen(this))));
            this.addDrawableChild(new ButtonWidget(x, y + 24, 200, 20, new LiteralText("Map D-pad, all 4"), b -> this.client.setScreen(new DpadMapScreen(this))));
            this.addDrawableChild(new ButtonWidget(x, y + 48, 200, 20, new LiteralText("Load PS5 layout"), b -> { PS5ControllerClient.applyDefault(); b.setMessage(new LiteralText("PS5 layout loaded")); }));
            this.addDrawableChild(new ButtonWidget(x, y + 72, 200, 20, new LiteralText("Load Xbox layout"), b -> { PS5ControllerClient.applyXbox(); b.setMessage(new LiteralText("Xbox layout loaded")); }));
            this.addDrawableChild(new ButtonWidget(x, y + 96, 200, 20, new LiteralText("Load Legion Go layout"), b -> { PS5ControllerClient.applyLegion(); b.setMessage(new LiteralText("Legion Go layout loaded")); }));
        }
        this.addDrawableChild(new ButtonWidget(this.width / 2 - 100, this.height - 28, 200, 20, new LiteralText("Done"), b -> this.onClose()));
    }

    private ButtonWidget onOff(int x, int y, String name, java.util.function.BooleanSupplier get, Runnable toggle) {
        return new ButtonWidget(x, y, 200, 20, new LiteralText(name + ": " + (get.getAsBoolean() ? "On" : "Off")), b -> {
            toggle.run();
            b.setMessage(new LiteralText(name + ": " + (get.getAsBoolean() ? "On" : "Off")));
        });
    }

    private ButtonWidget cycle(int x, int y, String name, java.util.function.Supplier<String> get, Runnable cycle) {
        return new ButtonWidget(x, y, 200, 20, new LiteralText(name + ": " + get.get()), b -> {
            cycle.run();
            b.setMessage(new LiteralText(name + ": " + get.get()));
        });
    }

    @Override public void onClose() { this.client.setScreen(parent); }

    @Override public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        drawCenteredText(matrices, this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFF);
        if (page == 0) {
            String status = PS5ControllerClient.connected ? "Connected" : "Not connected";
            drawCenteredText(matrices, this.textRenderer, new LiteralText(status), this.width / 2, 52, PS5ControllerClient.connected ? 0x55FF55 : 0xFF5555);
            drawCenteredText(matrices, this.textRenderer, new LiteralText(PS5ControllerClient.padName), this.width / 2, 64, 0xFFFFFF);
            drawCenteredText(matrices, this.textRenderer, new LiteralText("Battery: " + PS5ControllerClient.battery), this.width / 2, 76, 0xFFFFFF);
        }
        super.render(matrices, mouseX, mouseY, delta);
    }
}
