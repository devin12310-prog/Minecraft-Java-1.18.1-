package com.example.ps5controller;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;

public final class ControllerInput {
    private ControllerInput() {
    }

    public static boolean isGamepadConnected() {
        for (int joystick = GLFW.GLFW_JOYSTICK_1; joystick <= GLFW.GLFW_JOYSTICK_4; joystick++) {
            if (GLFW.glfwJoystickPresent(joystick)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isDpadUp() {
        return readButton(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_UP);
    }

    public static boolean isDpadDown() {
        return readButton(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_DOWN);
    }

    public static boolean isDpadLeft() {
        return readButton(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_LEFT);
    }

    public static boolean isDpadRight() {
        return readButton(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_RIGHT);
    }

    private static boolean readButton(int buttonId) {
        if (!isGamepadConnected()) {
            return false;
        }

        for (int joystick = GLFW.GLFW_JOYSTICK_1; joystick <= GLFW.GLFW_JOYSTICK_4; joystick++) {
            if (!GLFW.glfwJoystickPresent(joystick)) {
                continue;
            }

            GLFWGamepadState state = GLFWGamepadState.create();
            if (GLFW.glfwGetGamepadState(joystick, state)) {
                return state.buttons(buttonId) == GLFW.GLFW_PRESS;
            }
        }

        return false;
    }
}
