package com.example.ps5controller;

import com.example.ps5controller.mixin.KeyBindingAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.LiteralText;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * PS5 DualSense support over Bluetooth (read through GLFW).
 * L3 = crouch (hold), R3 = sprint (toggle). Menu and game logic are separated.
 */
public class PS5ControllerClient implements ClientModInitializer {

    public static class Pad {
        public volatile boolean active;
        public volatile float forward, sideways;
        public volatile boolean jump, sneak;
    }

    public static final Pad PAD = new Pad();

    private static final float DEADZONE = 0.12f;
    private static final float LOOK_SPEED = 1700f;
    private static final float CURSOR_SPEED = 1300f;
    private static final float TRIGGER_PULL = 0.4f;

    private static final int CROSS = 0, CIRCLE = 1, SQUARE = 2, TRIANGLE = 3, L1 = 4, R1 = 5,
            CREATE = 6, OPTIONS = 7, L3 = 9, R3 = 10,
            DPAD_UP = 11, DPAD_RIGHT = 12, DPAD_DOWN = 13, DPAD_LEFT = 14;

    private static final String DUALSENSE_MAPPINGS =
            "030000004c050000e60c000000000000,PS5 Controller,a:b1,b:b2,x:b0,y:b3,back:b8,guide:b13,"
            + "start:b9,leftstick:b10,rightstick:b11,leftshoulder:b4,rightshoulder:b5,"
            + "dpup:h0.1,dpright:h0.2,dpdown:h0.4,dpleft:h0.8,lefttrigger:a3,righttrigger:a4,"
            + "leftx:a0,lefty:a1,rightx:a2,righty:a5,platform:Windows,\n"
            + "030000004c050000e60c000000016800,PS5 Controller,a:b1,b:b2,x:b0,y:b3,back:b8,guide:b13,"
            + "start:b9,leftstick:b10,rightstick:b11,leftshoulder:b4,rightshoulder:b5,"
            + "dpup:h0.1,dpright:h0.2,dpdown:h0.4,dpleft:h0.8,lefttrigger:a3,righttrigger:a4,"
            + "leftx:a0,lefty:a1,rightx:a2,righty:a5,platform:Windows,\n"
            + "030000004c050000e60c000011010000,PS5 Controller,a:b1,b:b2,x:b0,y:b3,back:b8,guide:b13,"
            + "start:b9,leftstick:b10,rightstick:b11,leftshoulder:b4,rightshoulder:b5,"
            + "dpup:h0.1,dpright:h0.2,dpdown:h0.4,dpleft:h0.8,lefttrigger:a3,righttrigger:a4,"
            + "leftx:a0,lefty:a1,rightx:a2,righty:a5,platform:Linux,\n"
            + "050000004c050000e60c000000010000,PS5 Controller,a:b1,b:b2,x:b0,y:b3,back:b8,guide:b13,"
            + "start:b9,leftstick:b10,rightstick:b11,leftshoulder:b4,rightshoulder:b5,"
            + "dpup:h0.1,dpright:h0.2,dpdown:h0.4,dpleft:h0.8,lefttrigger:a3,righttrigger:a4,"
            + "leftx:a0,lefty:a1,rightx:a2,righty:a5,platform:Linux,\n"
            + "030000004c050000e60c000000000000,PS5 Controller,a:b1,b:b2,x:b0,y:b3,back:b8,guide:b13,"
            + "start:b9,leftstick:b10,rightstick:b11,leftshoulder:b4,rightshoulder:b5,"
            + "dpup:h0.1,dpright:h0.2,dpdown:h0.4,dpleft:h0.8,lefttrigger:a3,righttrigger:a4,"
            + "leftx:a0,lefty:a1,rightx:a2,righty:a5,platform:Mac OS X,\n";

    private static GLFWGamepadState state;
    private static boolean mappingsLoaded;
    private static final float[] ax = new float[6];
    private static final boolean[] bt = new boolean[15];
    private static int activeJid = -1;
    private static String preferredName;
    private static float restL = Float.MAX_VALUE, restR = Float.MAX_VALUE;
    private static boolean wasActive, warnedUnsupported;
    private static String padName = "Gamepad";
    private static String pendingToast;
    private static final boolean[] prev = new boolean[15];
    private static boolean prevL2, prevR2, sprintOn, sprintApplied;
    private static long lastFrame, lastMenuFrame;

    private static final boolean[] mprev = new boolean[15];
    private static boolean mLeftHeld, mRightHeld;
    private static double mLastSx, mLastSy, scrollAcc;
    private static Screen lastScreen;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.START_CLIENT_TICK.register(PS5ControllerClient::tick);
        WorldRenderEvents.START.register(ctx -> frameLook(MinecraftClient.getInstance()));
        HudRenderCallback.EVENT.register(PS5ControllerClient::hud);
        loadPref();
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof TitleScreen || screen instanceof GameMenuScreen) {
                ButtonWidget button = new ButtonWidget(4, 4, 170, 20, new LiteralText(statusLabel()),
                        b -> client.setScreen(new ControllerScreen(screen)));
                Screens.getButtons(screen).add(button);
            }
            ScreenEvents.afterRender(screen).register((s, matrices, mouseX, mouseY, tickDelta) ->
                    menuFrame(MinecraftClient.getInstance(), s));
        });
    }

    private static String statusLabel() {
        if (!wasActive) return "Controller: none";
        String n = padName.length() > 22 ? padName.substring(0, 22) : padName;
        return "Controller: " + n;
    }

    private static boolean pressed(ByteBuffer b, int i) { return b.get(i) != 0; }

    private static boolean looksLikeDualSense(int jid) {
        String nm = GLFW.glfwGetJoystickName(jid);
        if (nm == null) return false;
        String l = nm.toLowerCase();
        return l.contains("wireless controller") || l.contains("dualsense") || l.contains("ps5")
                || l.contains("dual sense");
    }

    private static boolean isUsable(int jid) {
        if (!GLFW.glfwJoystickPresent(jid)) return false;
        return GLFW.glfwJoystickIsGamepad(jid) || looksLikeDualSense(jid);
    }

    private static void loadMappings() {
        if (mappingsLoaded) return;
        mappingsLoaded = true;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            GLFW.glfwUpdateGamepadMappings(stack.UTF8(DUALSENSE_MAPPINGS));
        } catch (Throwable ignored) { }
    }

    private static boolean tryJid(int jid) {
        if (!GLFW.glfwJoystickPresent(jid)) return false;
        if (GLFW.glfwJoystickIsGamepad(jid) && GLFW.glfwGetGamepadState(jid, state)) {
            for (int i = 0; i < 6; i++) ax[i] = state.axes(i);
            for (int i = 0; i < 15; i++) bt[i] = state.buttons(i) == GLFW.GLFW_PRESS;
            activeJid = jid;
            return true;
        }
        if (!looksLikeDualSense(jid)) return false;
        FloatBuffer a = GLFW.glfwGetJoystickAxes(jid);
        ByteBuffer b = GLFW.glfwGetJoystickButtons(jid);
        ByteBuffer h = GLFW.glfwGetJoystickHats(jid);
        if (a == null || b == null || a.remaining() < 6 || b.remaining() < 12) return false;
        ax[0] = a.get(0); ax[1] = a.get(1); ax[2] = a.get(2);
        ax[3] = a.remaining() > 5 ? a.get(5) : a.get(3);
        ax[4] = a.get(3); ax[5] = a.get(4);
        Arrays.fill(bt, false);
        bt[CROSS] = pressed(b, 1); bt[CIRCLE] = pressed(b, 2);
        bt[SQUARE] = pressed(b, 0); bt[TRIANGLE] = pressed(b, 3);
        bt[L1] = pressed(b, 4); bt[R1] = pressed(b, 5);
        bt[CREATE] = pressed(b, 8); bt[OPTIONS] = pressed(b, 9);
        bt[L3] = pressed(b, 10); bt[R3] = pressed(b, 11);
        if (h != null && h.remaining() > 0) {
            int hv = h.get(0) & 0xFF;
            bt[DPAD_UP] = (hv & GLFW.GLFW_HAT_UP) != 0;
            bt[DPAD_RIGHT] = (hv & GLFW.GLFW_HAT_RIGHT) != 0;
            bt[DPAD_DOWN] = (hv & GLFW.GLFW_HAT_DOWN) != 0;
            bt[DPAD_LEFT] = (hv & GLFW.GLFW_HAT_LEFT) != 0;
        }
        activeJid = jid;
        return true;
    }

    private static boolean readPad() {
        if (state == null) state = GLFWGamepadState.malloc();
        loadMappings();
        if (preferredName != null) {
            for (int jid = GLFW.GLFW_JOYSTICK_1; jid <= GLFW.GLFW_JOYSTICK_LAST; jid++) {
                if (isUsable(jid) && preferredName.equals(nameOf(jid)) && tryJid(jid)) return true;
            }
        }
        for (int jid = GLFW.GLFW_JOYSTICK_1; jid <= GLFW.GLFW_JOYSTICK_LAST; jid++) {
            if (tryJid(jid)) return true;
        }
        return false;
    }

    public static String nameOf(int jid) {
        String n = GLFW.glfwGetGamepadName(jid);
        if (n == null) n = GLFW.glfwGetJoystickName(jid);
        return n != null ? n : "Gamepad";
    }

    public static List<Integer> usablePads() {
        List<Integer> out = new ArrayList<>();
        for (int jid = GLFW.GLFW_JOYSTICK_1; jid <= GLFW.GLFW_JOYSTICK_LAST; jid++) {
            if (isUsable(jid)) out.add(jid);
        }
        return out;
    }

    public static boolean isAuto() { return preferredName == null; }
    public static String getPreferredName() { return preferredName; }
    public static boolean isActive() { return wasActive; }
    public static String activeName() { return padName; }

    public static void setPreferredName(String name) {
        preferredName = name;
        wasActive = false;
        restL = restR = Float.MAX_VALUE;
        savePref();
    }

    private static Path prefFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("ps5controller.txt");
    }

    private static void loadPref() {
        try {
            Path f = prefFile();
            if (Files.exists(f)) {
                String t = Files.readString(f).trim();
                preferredName = t.isEmpty() ? null : t;
            }
        } catch (Exception ignored) { }
    }

    private static void savePref() {
        try {
            Files.writeString(prefFile(), preferredName == null ? "" : preferredName);
        } catch (Exception ignored) { }
    }

    private static float dz(float v) {
        float a = Math.abs(v);
        if (a < DEADZONE) return 0f;
        return Math.copySign((a - DEADZONE) / (1f - DEADZONE), v);
    }

    private static float curve(float v) {
        float a = Math.abs(v);
        return Math.copySign(a * a * 0.7f + a * 0.3f, v);
    }

    private static void key(KeyBinding kb, boolean down, boolean was) {
        if (down && !was) {
            KeyBindingAccessor acc = (KeyBindingAccessor) kb;
            acc.ps5$setTimesPressed(acc.ps5$getTimesPressed() + 1);
        }
        if (down != was) kb.setPressed(down);
    }

    private static void frameLook(MinecraftClient mc) {
        long n = System.nanoTime();
        float dt = lastFrame == 0 ? 0f : (n - lastFrame) / 1_000_000_000f;
        lastFrame = n;
        dt = Math.min(dt, 0.1f);
        if (mc.player == null || mc.currentScreen != null || !wasActive) return;
        if (!readPad()) return;
        float rx = dz(ax[2]), ry = dz(ax[3]);
        float yaw = curve(rx) * LOOK_SPEED * dt;
        float pitch = curve(ry) * LOOK_SPEED * dt;
        if (yaw != 0f || pitch != 0f) mc.player.changeLookDirection(yaw, pitch);
    }

    private static void menuFrame(MinecraftClient mc, Screen screen) {
        long n = System.nanoTime();
        float dt = lastMenuFrame == 0 ? 0f : (n - lastMenuFrame) / 1_000_000_000f;
        lastMenuFrame = n;
        dt = Math.min(dt, 0.1f);
        if (mc.getOverlay() != null || mc.currentScreen != screen) return;
        if (!readPad()) return;

        long handle = mc.getWindow().getHandle();
        double[] cx = new double[1];
        double[] cy = new double[1];
        GLFW.glfwGetCursorPos(handle, cx, cy);

        float mx = dz(ax[0]), my = dz(ax[1]);
        if (mx != 0f || my != 0f) {
            int[] ww = new int[1];
            int[] wh = new int[1];
            GLFW.glfwGetWindowSize(handle, ww, wh);
            double nx = cx[0] + curve(mx) * CURSOR_SPEED * dt;
            double ny = cy[0] + curve(my) * CURSOR_SPEED * dt;
            nx = Math.max(0, Math.min(nx, Math.max(1, ww[0]) - 1));
            ny = Math.max(0, Math.min(ny, Math.max(1, wh[0]) - 1));
            GLFW.glfwSetCursorPos(handle, nx, ny);
            cx[0] = nx; cy[0] = ny;
        }

        double sx = cx[0] * mc.getWindow().getScaledWidth() / (double) mc.getWindow().getWidth();
        double sy = cy[0] * mc.getWindow().getScaledHeight() / (double) mc.getWindow().getHeight();

        if (screen != lastScreen) {
            lastScreen = screen;
            System.arraycopy(bt, 0, mprev, 0, 15);
            mLeftHeld = mRightHeld = false;
            scrollAcc = 0;
            mLastSx = sx; mLastSy = sy;
            return;
        }

        if (bt[CROSS] && !mprev[CROSS]) { screen.mouseClicked(sx, sy, 0); mLeftHeld = true; }
        if (!bt[CROSS] && mprev[CROSS] && mLeftHeld) { screen.mouseReleased(sx, sy, 0); mLeftHeld = false; }
        if (bt[SQUARE] && !mprev[SQUARE]) { screen.mouseClicked(sx, sy, 1); mRightHeld = true; }
        if (!bt[SQUARE] && mprev[SQUARE] && mRightHeld) { screen.mouseReleased(sx, sy, 1); mRightHeld = false; }

        if (mLeftHeld && (sx != mLastSx || sy != mLastSy)) {
            screen.mouseDragged(sx, sy, 0, sx - mLastSx, sy - mLastSy);
        }
        mLastSx = sx; mLastSy = sy;

        if ((bt[CIRCLE] && !mprev[CIRCLE]) || (bt[OPTIONS] && !mprev[OPTIONS])) {
            screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        }

        if (bt[TRIANGLE] && !mprev[TRIANGLE] && screen instanceof HandledScreen) {
            InputUtil.Key k = InputUtil.fromTranslationKey(mc.options.keyInventory.getBoundKeyTranslationKey());
            screen.keyPressed(k.getCode(), 0, 0);
        }

        if (bt[DPAD_UP] && !mprev[DPAD_UP]) screen.keyPressed(GLFW.GLFW_KEY_UP, 0, 0);
        if (bt[DPAD_DOWN] && !mprev[DPAD_DOWN]) screen.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
        if (bt[DPAD_LEFT] && !mprev[DPAD_LEFT]) screen.keyPressed(GLFW.GLFW_KEY_LEFT, 0, 0);
        if (bt[DPAD_RIGHT] && !mprev[DPAD_RIGHT]) screen.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0);

        float ry = dz(ax[3]);
        scrollAcc += -curve(ry) * dt * 12.0;
        while (scrollAcc >= 1.0) { screen.mouseScrolled(sx, sy, 1.0); scrollAcc -= 1.0; }
        while (scrollAcc <= -1.0) { screen.mouseScrolled(sx, sy, -1.0); scrollAcc += 1.0; }

        System.arraycopy(bt, 0, mprev, 0, 15);
    }

    private static void hud(MatrixStack matrices, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.currentScreen != null || mc.options.hudHidden || mc.options.debugEnabled) return;
        String text = wasActive ? "Controller: Connected" : "Controller: Not connected";
        int color = wasActive ? 0x55FF55 : 0xFF5555;
        int w = mc.textRenderer.getWidth(text);
        mc.textRenderer.drawWithShadow(matrices, text, mc.getWindow().getScaledWidth() - w - 4, 4, color);
    }

    private static void tick(MinecraftClient mc) {
        boolean found = readPad();

        if (found && !wasActive) {
            wasActive = true;
            warnedUnsupported = false;
            padName = nameOf(activeJid);
            restL = restR = Float.MAX_VALUE;
            pendingToast = "connected";
        } else if (!found && wasActive) {
            wasActive = false;
            pendingToast = "disconnected";
        } else if (!found && !warnedUnsupported) {
            for (int jid = GLFW.GLFW_JOYSTICK_1; jid <= GLFW.GLFW_JOYSTICK_LAST; jid++) {
                if (GLFW.glfwJoystickPresent(jid)) {
                    String nm = GLFW.glfwGetJoystickName(jid);
                    padName = nm != null ? nm : "Unknown device";
                    warnedUnsupported = true;
                    pendingToast = "unsupported";
                    break;
                }
            }
        }

        if (pendingToast != null && mc.getOverlay() == null) {
            switch (pendingToast) {
                case "connected":
                    SystemToast.add(mc.getToastManager(), SystemToast.Type.TUTORIAL_HINT,
                            new LiteralText("Controller connected"), new LiteralText(padName));
                    break;
                case "unsupported":
                    SystemToast.add(mc.getToastManager(), SystemToast.Type.TUTORIAL_HINT,
                            new LiteralText("Controller not supported"), new LiteralText(padName));
                    break;
                default:
                    SystemToast.add(mc.getToastManager(), SystemToast.Type.TUTORIAL_HINT,
                            new LiteralText("Controller disconnected"), null);
            }
            pendingToast = null;
        }

        if (!found) { PAD.active = false; return; }
        PAD.active = true;

        boolean[] now = bt.clone();
        restL = Math.min(restL, ax[4]);
        restR = Math.min(restR, ax[5]);
        boolean l2 = ax[4] - restL > TRIGGER_PULL;
        boolean r2 = ax[5] - restR > TRIGGER_PULL;
        float lx = dz(ax[0]), ly = dz(ax[1]);

        boolean inGame = mc.player != null && mc.currentScreen == null;

        if (!inGame) {
            PAD.forward = PAD.sideways = 0f;
            PAD.jump = PAD.sneak = false;
            key(mc.options.keyAttack, false, prevR2);
            key(mc.options.keyUse, false, prevL2);
            key(mc.options.keySneak, false, prev[L3]);
            if (sprintApplied) { mc.options.keySprint.setPressed(false); sprintApplied = false; }
            sprintOn = false;
            System.arraycopy(now, 0, prev, 0, 15);
            prevL2 = false;
            prevR2 = false;
            return;
        }

        float mag = (float) Math.hypot(lx, ly);
        if (mag > 0f) {
            float out = Math.min(1f, mag / 0.9f);
            PAD.forward = -(ly / mag) * out;
            PAD.sideways = -(lx / mag) * out;
        } else {
            PAD.forward = PAD.sideways = 0f;
        }
        PAD.jump = now[CROSS];
        PAD.sneak = now[L3];
        key(mc.options.keySneak, now[L3], prev[L3]);

        key(mc.options.keyAttack, r2, prevR2);
        key(mc.options.keyUse, l2, prevL2);
        key(mc.options.keySwapHands, now[SQUARE], prev[SQUARE]);
        key(mc.options.keyInventory, now[TRIANGLE], prev[TRIANGLE]);
        key(mc.options.keyDrop, now[DPAD_UP], prev[DPAD_UP]);
        key(mc.options.keyTogglePerspective, now[DPAD_DOWN], prev[DPAD_DOWN]);
        key(mc.options.keyPlayerList, now[CREATE], prev[CREATE]);

        if (now[R1] && !prev[R1]) mc.player.getInventory().selectedSlot = (mc.player.getInventory().selectedSlot + 1) % 9;
        if (now[L1] && !prev[L1]) mc.player.getInventory().selectedSlot = (mc.player.getInventory().selectedSlot + 8) % 9;

        if (now[R3] && !prev[R3]) sprintOn = !sprintOn;
        if (PAD.forward <= 0.1f) sprintOn = false;
        if (sprintOn || sprintApplied) {
            mc.options.keySprint.setPressed(sprintOn);
            sprintApplied = sprintOn;
        }

        if (now[OPTIONS] && !prev[OPTIONS]) mc.openPauseMenu(false);

        System.arraycopy(now, 0, prev, 0, 15);
        prevL2 = l2;
        prevR2 = r2;
    }
}
