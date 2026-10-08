package com.example.ps5controller;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ControllerScreen {
    private static int selectedIndex = 0;

    private ControllerScreen() {
    }

    public static void applyDpadNavigation() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.currentScreen == null) {
            selectedIndex = 0;
            return;
        }

        Screen screen = client.currentScreen;
        List<ClickableWidget> widgets = collectWidgets(screen);
        if (widgets.isEmpty()) {
            selectedIndex = 0;
            return;
        }

        if (ControllerInput.isDpadUp()) {
            moveSelection(widgets, -1);
            return;
        }

        if (ControllerInput.isDpadDown()) {
            moveSelection(widgets, 1);
            return;
        }

        if (ControllerInput.isDpadLeft()) {
            moveSelection(widgets, -1);
            return;
        }

        if (ControllerInput.isDpadRight()) {
            moveSelection(widgets, 1);
            return;
        }

        refreshSelection(widgets);
    }

    private static void moveSelection(List<ClickableWidget> widgets, int direction) {
        if (widgets.isEmpty()) {
            return;
        }

        selectedIndex = Math.floorMod(selectedIndex + direction, widgets.size());
        refreshSelection(widgets);
    }

    private static void refreshSelection(List<ClickableWidget> widgets) {
        if (selectedIndex < 0 || selectedIndex >= widgets.size()) {
            selectedIndex = 0;
        }

        ClickableWidget selected = widgets.get(selectedIndex);
        selected.setFocused(true);
        selected.active = true;
        selected.visible = true;
    }

    private static List<ClickableWidget> collectWidgets(Screen screen) {
        List<ClickableWidget> widgets = new ArrayList<>();
        screen.children().stream()
                .filter(ClickableWidget.class::isInstance)
                .map(ClickableWidget.class::cast)
                .filter(widget -> widget.visible && widget.active)
                .sorted(Comparator.comparingInt(widget -> widget.y))
                .forEach(widgets::add);
        return widgets;
    }
}
