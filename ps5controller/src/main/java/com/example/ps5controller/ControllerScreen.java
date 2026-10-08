package com.example.ps5controller;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ControllerScreen {
    private static int selectedIndex = 0;
    private static long lastNavigationTime = 0;
    private static final long NAVIGATION_DELAY = 150; // milliseconds

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

        long currentTime = System.currentTimeMillis();
        boolean navigationPressed = false;

        if (ControllerInput.isDpadUp()) {
            if (currentTime - lastNavigationTime > NAVIGATION_DELAY) {
                moveSelection(widgets, -1);
                lastNavigationTime = currentTime;
            }
            navigationPressed = true;
        }

        if (ControllerInput.isDpadDown()) {
            if (currentTime - lastNavigationTime > NAVIGATION_DELAY) {
                moveSelection(widgets, 1);
                lastNavigationTime = currentTime;
            }
            navigationPressed = true;
        }

        if (ControllerInput.isDpadLeft()) {
            if (currentTime - lastNavigationTime > NAVIGATION_DELAY) {
                moveSelection(widgets, -1);
                lastNavigationTime = currentTime;
            }
            navigationPressed = true;
        }

        if (ControllerInput.isDpadRight()) {
            if (currentTime - lastNavigationTime > NAVIGATION_DELAY) {
                moveSelection(widgets, 1);
                lastNavigationTime = currentTime;
            }
            navigationPressed = true;
        }

        // Handle button press to click selected widget
        if (ControllerInput.isButtonCross()) {
            ClickableWidget selected = widgets.get(selectedIndex);
            selected.onPress();
        }

        if (!navigationPressed) {
            refreshSelection(widgets);
        }
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

        // Unfocus all widgets first
        for (ClickableWidget widget : widgets) {
            widget.setFocused(false);
        }

        // Focus the selected widget
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
                .sorted(Comparator.comparingInt(widget -> widget.y)
                        .thenComparingInt(widget -> widget.x))
                .forEach(widgets::add);
        return widgets;
    }
}
