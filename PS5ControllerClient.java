package com.example.ps5controller;

import com.example.ps5controller.mixin.KeyBindingAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
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
import java.util.Comparator;
import java.util.List;

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
    public static final int L2 = 20, R2 = 21;

    public static final int ACT_JUMP = 0, ACT_SNEAK = 1, ACT_ATTACK = 2, ACT_USE = 3,
            ACT_INVENTORY = 4, ACT_SWAP = 5, ACT_DROP = 6, ACT_PERSPECTIVE = 7,
            ACT_TAB = 8, ACT_HOTBAR_NEXT = 9, ACT_HOTBAR_PREV = 10, ACT_SPRINT = 11,
            ACT_PAUSE = 12, ACT_MENU_BACK = 13;
    public static final String[] ACT_NAMES = {
            "Jump", "Sneak", "Attack", "Use / place", "Inventory", "Swap hands",
            "Drop", "Perspective", "Player list", "Hotbar next", "Hotbar prev",
            "Sprint", "Pause", "Menu back"
    };
    private static final int[] NORMAL = {
            CROSS, L3, R2, L2, TRIANGLE, SQUARE, CIRCLE, R3, CREATE,
            R1, L1, R3, OPTIONS, CIRCLE
    };
    private static final int[] STICK_CROUCH = {
            CROSS, L3, R2, L2, TRIANGLE, SQUARE, DPAD_UP, DPAD_DOWN, CREATE,
            R1, L1, R3, OPTIONS, CIRCLE
    };
    public static final int[] BINDS = new int[ACT_NAMES.length];
    public static volatile int listeningAction = -1;
    public static volatile int mappingDpad = 0; // 0 off, 1 up, 2 down, 3 left, 4 right
    public static int mapUp = DPAD_UP, mapDown = DPAD_DOWN, mapLeft = DPAD_LEFT, mapRight = DPAD_RIGHT;

    private static final String DUALSENSE_MAPPINGS =
            "030000004c050000e60c000000000000,PS5 Controller,a:b1,b:b2,x:b0,y:b3,back:b8,guide:b13,"
            + "start:b9,leftstick:b10,rightstick:b11,leftshoulder:b4,rightshoulder:b5,"
            + "dpup:h0.1,dpright:h0.2,dpdown:h0.4,dpleft:h0.8,lefttrigger:a3,righttrigger:a4,"
            + "leftx:a0,lefty:a1,rightx:a2,righty:a5,platform:Windows,\n"
            + "030000004c050000e60c000011010000,PS5 Controller,a:b1,b:b2,x:b0,y:b3,back:b8,guide:b13,"
            + "start:b9,leftstick:b10,rightstick:b11,leftshoulder:b4,rightshoulder:b5,"
            + "dpup:h0.1,dpright:h0.2,dpdown:h0.4,dpleft:h0.8,lefttrigger:a3,righttrigger:a4,"
            + "leftx:a0,lefty:a1,rightx:a2,righty:a5,platform:Linux,\n";

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

    private static int menuFocusIndex = 0;
    private static long lastDpadNavMs;
    private static long suppressDropUntil;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.START_CLIENT_TICK.register(PS5ControllerClient::tick);
        WorldRenderEvents.START.register(ctx -> frameLook(MinecraftClient.getInstance()));
        HudRenderCallback.EVENT.register(PS5ControllerClient::hud);
        loadPref();
        loadBinds();
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof TitleScreen || screen instanceof GameMenuScreen) {
                ButtonWidget button = new ButtonWidget(4, 4, 170, 20, new LiteralText(statusLabel()),
                        b -> client.setScreen(new ControllerScreen(screen)));
                Screens.getButtons(screen).add(button);
            }
            ScreenEvents.afterRender(screen).register((s, matrices, mouseX, mouseY, tickDelta) ->
                    menuFrame(MinecraftClient.getInstance(), s, matrices));
        });
    }

    private static String statusLabel() {
        if (!wasActive) return "Controller: none";
        String n = padName.length() > 22 ? padName.substring(0, 22) : padName;
        return "Controller: " + n;
    }

    private static boolean pressed(ByteBuffer b, int i) {
        return i < b.remaining() && b.get(i) != 0;
    }

    private static boolean looksLikeDualSense(int jid) {
        String nm = GLFW.glfwGetJoystickName(jid);
        if (nm == null) return false;
        String l = nm.toLowerCase();
        return l.contains("wireless controller") || l.contains("dualsense") || l.contains("ps5") || l.contains("dual sense");
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

    private static void applyHat(int hat) {
        if (hat == GLFW.GLFW_HAT_CENTERED || hat == 0) return;
        if ((hat & GLFW.GLFW_HAT_UP) != 0 || hat == 1) bt[DPAD_UP] = true;
        if ((hat & GLFW.GLFW_HAT_RIGHT) != 0 || hat == 3) bt[DPAD_RIGHT] = true;
        if ((hat & GLFW.GLFW_HAT_DOWN) != 0 || hat == 5) bt[DPAD_DOWN] = true;
        if ((hat & GLFW.GLFW_HAT_LEFT) != 0 || hat == 7) bt[DPAD_LEFT] = true;
        if (hat == 2) { bt[DPAD_UP] = true; bt[DPAD_RIGHT] = true; }
        if (hat == 4) { bt[DPAD_RIGHT] = true; bt[DPAD_DOWN] = true; }
        if (hat == 6) { bt[DPAD_DOWN] = true; bt[DPAD_LEFT] = true; }
        if (hat == 8) { bt[DPAD_LEFT] = true; bt[DPAD_UP] = true; }
    }

    private static void applyHatAxes(FloatBuffer a) {
        if (a == null) return;
        if (bt[DPAD_UP] || bt[DPAD_DOWN] || bt[DPAD_LEFT] || bt[DPAD_RIGHT]) return;
        int n = a.remaining();
        // Only the last two axes. Scanning every pair also saw the sticks and set up+down together.
        if (n < 2) return;
        float hx = a.get(n - 2);
        float hy = a.get(n - 1);
        if (Math.abs(hx) < 0.6f && Math.abs(hy) < 0.6f) return;
        if (Math.abs(hy) >= Math.abs(hx)) {
            if (hy < -0.6f) bt[DPAD_UP] = true;
            if (hy > 0.6f) bt[DPAD_DOWN] = true;
        } else {
            if (hx < -0.6f) bt[DPAD_LEFT] = true;
            if (hx > 0.6f) bt[DPAD_RIGHT] = true;
        }
    }

    private static void fillDpadFromRaw(int jid) {
        try {
            ByteBuffer h = GLFW.glfwGetJoystickHats(jid);
            if (h != null) {
                for (int i = 0; i < h.remaining(); i++) applyHat(h.get(i) & 0xFF);
            }
        } catch (Throwable ignored) { }
        // Axes are the sticks. D-pad comes from hats and buttons only.
        try {
            ByteBuffer b = GLFW.glfwGetJoystickButtons(jid);
            if (b != null) {
                int n = b.remaining();
                if (n > 11 && pressed(b, 11)) bt[DPAD_UP] = true;
                if (n > 12 && pressed(b, 12)) bt[DPAD_RIGHT] = true;
                if (n > 13 && pressed(b, 13)) bt[DPAD_DOWN] = true;
                if (n > 14 && pressed(b, 14)) bt[DPAD_LEFT] = true;
                if (n > 15 && pressed(b, 15)) bt[DPAD_UP] = true;
                if (n > 16 && pressed(b, 16)) bt[DPAD_RIGHT] = true;
                if (n > 17 && pressed(b, 17)) bt[DPAD_DOWN] = true;
                if (n > 18 && pressed(b, 18)) bt[DPAD_LEFT] = true;
            }
        } catch (Throwable ignored) { }
    }

    private static boolean tryJid(int jid) {
        if (!GLFW.glfwJoystickPresent(jid)) return false;
        if (GLFW.glfwJoystickIsGamepad(jid) && state != null && GLFW.glfwGetGamepadState(jid, state)) {
            for (int i = 0; i < 6; i++) ax[i] = state.axes(i);
            for (int i = 0; i < 15; i++) bt[i] = state.buttons(i) == GLFW.GLFW_PRESS;
            fillDpadFromRaw(jid);
            activeJid = jid;
            return true;
        }
        if (!looksLikeDualSense(jid)) return false;
        FloatBuffer a = GLFW.glfwGetJoystickAxes(jid);
        ByteBuffer b = GLFW.glfwGetJoystickButtons(jid);
        if (a == null || b == null || a.remaining() < 4 || b.remaining() < 12) return false;
        ax[0] = a.get(0); ax[1] = a.get(1);
        ax[2] = a.remaining() > 2 ? a.get(2) : 0f;
        ax[3] = a.remaining() > 5 ? a.get(5) : (a.remaining() > 3 ? a.get(3) : 0f);
        ax[4] = a.remaining() > 3 ? a.get(3) : -1f;
        ax[5] = a.remaining() > 4 ? a.get(4) : -1f;
        Arrays.fill(bt, false);
        bt[CROSS] = pressed(b, 1); bt[CIRCLE] = pressed(b, 2);
        bt[SQUARE] = pressed(b, 0); bt[TRIANGLE] = pressed(b, 3);
        bt[L1] = pressed(b, 4); bt[R1] = pressed(b, 5);
        bt[CREATE] = pressed(b, 8); bt[OPTIONS] = pressed(b, 9);
        bt[L3] = pressed(b, 10); bt[R3] = pressed(b, 11);
        fillDpadFromRaw(jid);
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
        preferredName = name; wasActive = false; restL = restR = Float.MAX_VALUE; savePref();
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
        try { Files.writeString(prefFile(), preferredName == null ? "" : preferredName); } catch (Exception ignored) { }
    }

    private static Path bindFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("ps5binds.txt");
    }

    public static String buttonName(int b) {
        switch (b) {
            case CROSS: return "Cross";
            case CIRCLE: return "Circle";
            case SQUARE: return "Square";
            case TRIANGLE: return "Triangle";
            case L1: return "L1";
            case R1: return "R1";
            case CREATE: return "Create";
            case OPTIONS: return "Options";
            case L3: return "L3";
            case R3: return "R3";
            case DPAD_UP: return "D-pad up";
            case DPAD_RIGHT: return "D-pad right";
            case DPAD_DOWN: return "D-pad down";
            case DPAD_LEFT: return "D-pad left";
            case L2: return "L2";
            case R2: return "R2";
            default: return "Button " + b;
        }
    }

    public static void applyNormal() { System.arraycopy(NORMAL, 0, BINDS, 0, BINDS.length); saveBinds(); }
    public static void applyStickCrouch() { System.arraycopy(STICK_CROUCH, 0, BINDS, 0, BINDS.length); saveBinds(); }

    public static void setBind(int action, int button) {
        if (action < 0 || action >= BINDS.length) return;
        BINDS[action] = button;
        saveBinds();
    }

    private static void saveBinds() {
        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < BINDS.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(BINDS[i]);
            }
            sb.append('|').append(mapUp).append(',').append(mapDown).append(',').append(mapLeft).append(',').append(mapRight);
            Files.writeString(bindFile(), sb.toString());
        } catch (Exception ignored) { }
    }

    private static void loadBinds() {
        System.arraycopy(NORMAL, 0, BINDS, 0, BINDS.length);
        try {
            Path f = bindFile();
            if (!Files.exists(f)) return;
            String raw = Files.readString(f).trim();
            String binds = raw;
            if (raw.contains("|")) {
                binds = raw.substring(0, raw.indexOf('|'));
                String[] m = raw.substring(raw.indexOf('|') + 1).split(",");
                if (m.length >= 4) {
                    mapUp = Integer.parseInt(m[0].trim());
                    mapDown = Integer.parseInt(m[1].trim());
                    mapLeft = Integer.parseInt(m[2].trim());
                    mapRight = Integer.parseInt(m[3].trim());
                }
            }
            String[] parts = binds.split(",");
            for (int i = 0; i < BINDS.length && i < parts.length; i++) {
                BINDS[i] = Integer.parseInt(parts[i].trim());
            }
        } catch (Exception ignored) { }
    }

    private static boolean held(int button, boolean l2, boolean r2) {
        if (button == L2) return l2;
        if (button == R2) return r2;
        return button >= 0 && button < bt.length && bt[button];
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

    private static boolean heldPrev(int button) {
        if (button == L2) return prevL2;
        if (button == R2) return prevR2;
        return button >= 0 && button < prev.length && prev[button];
    }

    private static boolean edge(int button, boolean l2, boolean r2) {
        return held(button, l2, r2) && !heldPrev(button);
    }

    private static void keyEdge(KeyBinding kb, int button, boolean l2, boolean r2) {
        key(kb, held(button, l2, r2), heldPrev(button));
    }

    public static int pressedBindButton(boolean l2, boolean r2) {
        if (l2 && !prevL2) return L2;
        if (r2 && !prevR2) return R2;
        for (int i = 0; i < bt.length; i++) if (bt[i] && !prev[i]) return i;
        return -1;
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
        lastFrame = n; dt = Math.min(dt, 0.1f);
        if (mc.player == null || mc.currentScreen != null || !wasActive) return;
        if (!readPad()) return;
        float rx = dz(ax[2]), ry = dz(ax[3]);
        float yaw = curve(rx) * LOOK_SPEED * dt;
        float pitch = curve(ry) * LOOK_SPEED * dt;
        if (yaw != 0f || pitch != 0f) mc.player.changeLookDirection(yaw, pitch);
    }

    private static List<ClickableWidget> collectWidgets(Screen screen) {
        List<ClickableWidget> widgets = new ArrayList<>();
        for (Element e : screen.children()) {
            if (e instanceof ClickableWidget) {
                ClickableWidget w = (ClickableWidget) e;
                if (w.visible && w.active) widgets.add(w);
            }
        }
        widgets.sort(Comparator.comparingInt((ClickableWidget w) -> w.y).thenComparingInt(w -> w.x));
        return widgets;
    }

    private static boolean padBit(int index) {
        return index >= 0 && index < bt.length && bt[index];
    }

    private static int dpadDir() {
        boolean up = padBit(mapUp);
        boolean down = padBit(mapDown);
        boolean left = padBit(mapLeft);
        boolean right = padBit(mapRight);
        if (up && down) up = down = false;
        if (left && right) left = right = false;
        if (up) return 1;
        if (down) return 2;
        if (left) return 3;
        if (right) return 4;
        return 0;
    }

    public static int rawDpadEdge() {
        for (int i = 11; i <= 14 && i < bt.length; i++) if (bt[i] && !prev[i]) return i;
        return -1;
    }

    private static boolean listScreen(Screen screen) {
        for (Element e : screen.children()) {
            String n = e.getClass().getName();
            if (n.contains("WorldList") || n.contains("EntryList")) return true;
        }
        return false;
    }

    private static void applyMenuDpadFocus(MinecraftClient mc, Screen screen) {
        List<ClickableWidget> widgets = new ArrayList<>();
        for (ClickableWidget w : collectWidgets(screen)) {
            if (w.getWidth() >= 10 && w.getHeight() >= 10) widgets.add(w);
        }
        if (widgets.isEmpty()) return;
        int dir = dpadDir();
        if (dir == 0) return;
        long now = System.currentTimeMillis();
        // One step only. 220ms blocks the double-fire from render + hat bounce.
        if (now - lastDpadNavMs < 180) return;
        if (menuFocusIndex < 0 || menuFocusIndex >= widgets.size()) menuFocusIndex = 0;
        if (listScreen(screen) && (dir == 1 || dir == 2)) {
            screen.keyPressed(dir == 1 ? GLFW.GLFW_KEY_UP : GLFW.GLFW_KEY_DOWN, 0, 0);
            lastDpadNavMs = now;
            return;
        }
        ClickableWidget cur = widgets.get(menuFocusIndex);
        double cx = cur.x + cur.getWidth() / 2.0;
        double cy = cur.y + cur.getHeight() / 2.0;
        int best = -1;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < widgets.size(); i++) {
            if (i == menuFocusIndex) continue;
            ClickableWidget w = widgets.get(i);
            double wx = w.x + w.getWidth() / 2.0;
            double wy = w.y + w.getHeight() / 2.0;
            double dx = wx - cx;
            double dy = wy - cy;
            boolean ok = false;
            double primary = 0, secondary = 0;
            if (dir == 1) { ok = dy < -4; primary = -dy; secondary = Math.abs(dx); }
            else if (dir == 2) { ok = dy > 4; primary = dy; secondary = Math.abs(dx); }
            else if (dir == 3) { ok = dx < -4; primary = -dx; secondary = Math.abs(dy); }
            else { ok = dx > 4; primary = dx; secondary = Math.abs(dy); }
            if (!ok) continue;
            if (secondary > primary * 1.6 + 24) continue;
            double score = primary + secondary * 0.35;
            if (score < bestScore) { bestScore = score; best = i; }
        }
        if (best < 0) {
            if (dir == 3 || dir == 1) best = (menuFocusIndex - 1 + widgets.size()) % widgets.size();
            else best = (menuFocusIndex + 1) % widgets.size();
        }
        menuFocusIndex = best;
        lastDpadNavMs = now;
        ClickableWidget w = widgets.get(best);
        double scaleX = (double) mc.getWindow().getWidth() / (double) mc.getWindow().getScaledWidth();
        double scaleY = (double) mc.getWindow().getHeight() / (double) mc.getWindow().getScaledHeight();
        double px = (w.x + w.getWidth() / 2.0) * scaleX;
        double py = (w.y + w.getHeight() / 2.0) * scaleY;
        GLFW.glfwSetCursorPos(mc.getWindow().getHandle(), px, py);
    }

    private static int guiTop(HandledScreen<?> screen) {
        int maxY = 0;
        for (Slot slot : screen.getScreenHandler().slots) if (slot.y > maxY) maxY = slot.y;
        return (screen.height - (maxY + 26)) / 2;
    }

    private static Slot slotAt(HandledScreen<?> screen, double sx, double sy) {
        int left = (screen.width - 176) / 2;
        int top = guiTop(screen);
        Slot best = null;
        double bestD = 18;
        for (Slot slot : screen.getScreenHandler().slots) {
            double cx = left + slot.x + 8;
            double cy = top + slot.y + 8;
            double d = Math.hypot(sx - cx, sy - cy);
            if (d < bestD) { bestD = d; best = slot; }
        }
        return best;
    }

    private static void moveInventoryCursor(MinecraftClient mc, HandledScreen<?> screen, int dir) {
        int left = (screen.width - 176) / 2;
        int top = guiTop(screen);
        double[] cx = new double[1], cy = new double[1];
        GLFW.glfwGetCursorPos(mc.getWindow().getHandle(), cx, cy);
        double sx = cx[0] * screen.width / (double) mc.getWindow().getWidth();
        double sy = cy[0] * screen.height / (double) mc.getWindow().getHeight();
        Slot cur = slotAt(screen, sx, sy);
        double ox = cur == null ? sx : left + cur.x + 8;
        double oy = cur == null ? sy : top + cur.y + 8;
        Slot best = null;
        double bestScore = Double.MAX_VALUE;
        for (Slot slot : screen.getScreenHandler().slots) {
            double wx = left + slot.x + 8;
            double wy = top + slot.y + 8;
            double dx = wx - ox, dy = wy - oy;
            if (Math.hypot(dx, dy) < 4) continue;
            boolean ok = false; double primary = 0, secondary = 0;
            if (dir == 1) { ok = dy < -2; primary = -dy; secondary = Math.abs(dx); }
            else if (dir == 2) { ok = dy > 2; primary = dy; secondary = Math.abs(dx); }
            else if (dir == 3) { ok = dx < -2; primary = -dx; secondary = Math.abs(dy); }
            else if (dir == 4) { ok = dx > 2; primary = dx; secondary = Math.abs(dy); }
            if (!ok || secondary > primary * 1.8 + 20) continue;
            double score = primary + secondary * 0.4;
            if (score < bestScore) { bestScore = score; best = slot; }
        }
        if (best == null) return;
        double scaleX = (double) mc.getWindow().getWidth() / (double) screen.width;
        double scaleY = (double) mc.getWindow().getHeight() / (double) screen.height;
        GLFW.glfwSetCursorPos(mc.getWindow().getHandle(), (left + best.x + 8) * scaleX, (top + best.y + 8) * scaleY);
    }

    private static void quickMoveStack(MinecraftClient mc, HandledScreen<?> screen, double sx, double sy) {
        Slot slot = slotAt(screen, sx, sy);
        if (slot == null || mc.player == null || mc.interactionManager == null) return;
        mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, slot.id, 0, net.minecraft.screen.slot.SlotActionType.QUICK_MOVE, mc.player);
    }

    private static void menuFrame(MinecraftClient mc, Screen screen, MatrixStack matrices) {
        long n = System.nanoTime();
        float dt = lastMenuFrame == 0 ? 0f : (n - lastMenuFrame) / 1_000_000_000f;
        lastMenuFrame = n; dt = Math.min(dt, 0.1f);
        if (mc.getOverlay() != null || mc.currentScreen != screen) return;
        if (!readPad()) return;

        long handle = mc.getWindow().getHandle();
        double[] cx = new double[1]; double[] cy = new double[1];
        GLFW.glfwGetCursorPos(handle, cx, cy);

        float mx = dz(ax[0]), my = dz(ax[1]);
        if (mx != 0f || my != 0f) {
            int[] ww = new int[1]; int[] wh = new int[1];
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
            mLeftHeld = mRightHeld = false; scrollAcc = 0; menuFocusIndex = 0;
            mLastSx = sx; mLastSy = sy;
            return;
        }

        boolean inventory = screen instanceof HandledScreen;
        int dir = dpadDir();
        if (inventory && dir != 0 && System.currentTimeMillis() - lastDpadNavMs >= 160) {
            moveInventoryCursor(mc, (HandledScreen<?>) screen, dir);
            lastDpadNavMs = System.currentTimeMillis();
        } else if (!inventory) {
            applyMenuDpadFocus(mc, screen);
        }

        if (inventory && bt[TRIANGLE] && !mprev[TRIANGLE]) {
            quickMoveStack(mc, (HandledScreen<?>) screen, sx, sy);
        }

        if (bt[CROSS] && !mprev[CROSS]) {
            if (listScreen(screen)) screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
            screen.mouseClicked(sx, sy, 0);
            mLeftHeld = true;
        }
        if (!bt[CROSS] && mprev[CROSS] && mLeftHeld) { screen.mouseReleased(sx, sy, 0); mLeftHeld = false; }
        if (bt[SQUARE] && !mprev[SQUARE]) { screen.mouseClicked(sx, sy, 1); mRightHeld = true; }
        if (!bt[SQUARE] && mprev[SQUARE] && mRightHeld) { screen.mouseReleased(sx, sy, 1); mRightHeld = false; }

        if (mLeftHeld && (sx != mLastSx || sy != mLastSy)) {
            screen.mouseDragged(sx, sy, 0, sx - mLastSx, sy - mLastSy);
        }
        mLastSx = sx; mLastSy = sy;

        boolean back = held(BINDS[ACT_MENU_BACK], ax[4] - restL > TRIGGER_PULL, ax[5] - restR > TRIGGER_PULL);
        boolean backWas = heldPrev(BINDS[ACT_MENU_BACK]);
        if ((back && !backWas) || (bt[OPTIONS] && !mprev[OPTIONS])) {
            screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            // Same press was also Drop, so closing inventory threw an item.
            prev[CIRCLE] = true;
            prev[OPTIONS] = true;
            mprev[CIRCLE] = true;
            mprev[OPTIONS] = true;
            suppressDropUntil = System.currentTimeMillis() + 400;
        }

        // D-pad menu nav is cursor-only. Arrow keyPressed was also moving focus, so one tap skipped a widget.

        float ry = dz(ax[3]);
        scrollAcc += -curve(ry) * dt * 12.0;
        while (scrollAcc >= 1.0) { screen.mouseScrolled(sx, sy, 1.0); scrollAcc -= 1.0; }
        while (scrollAcc <= -1.0) { screen.mouseScrolled(sx, sy, -1.0); scrollAcc += 1.0; }

        drawHover(mc, screen, matrices, sx, sy);
        System.arraycopy(bt, 0, mprev, 0, 15);
    }

    private static void drawHover(MinecraftClient mc, Screen screen, MatrixStack matrices, double sx, double sy) {
        if (screen instanceof HandledScreen) {
            Slot slot = slotAt((HandledScreen<?>) screen, sx, sy);
            if (slot != null && slot.hasStack()) {
                screen.renderTooltip(matrices, slot.getStack().getName(), (int) sx, (int) sy);
                return;
            }
        }
        ClickableWidget over = null;
        for (ClickableWidget w : collectWidgets(screen)) {
            if (sx >= w.x && sx <= w.x + w.getWidth() && sy >= w.y && sy <= w.y + w.getHeight()) over = w;
        }
        if (over != null && over.getMessage() != null) {
            drawCentered(mc, matrices, over.getMessage().getString(), screen.width / 2, 6);
        }
    }

    private static void drawCentered(MinecraftClient mc, MatrixStack matrices, String text, int x, int y) {
        mc.textRenderer.drawWithShadow(matrices, text, x - mc.textRenderer.getWidth(text) / 2, y, 0xFFFFFF);
    }

    private static void hud(MatrixStack matrices, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.currentScreen != null || mc.options.hudHidden) return;
        String text = wasActive ? "Controller: Connected" : "Controller: Not connected";
        int color = wasActive ? 0x55FF55 : 0xFF5555;
        int w = mc.textRenderer.getWidth(text);
        mc.textRenderer.drawWithShadow(matrices, text, mc.getWindow().getScaledWidth() - w - 4, 4, color);
    }

    private static void tick(MinecraftClient mc) {
        boolean found = readPad();
        if (found && !wasActive) {
            wasActive = true; warnedUnsupported = false; padName = nameOf(activeJid);
            restL = restR = Float.MAX_VALUE; pendingToast = "connected";
        } else if (!found && wasActive) {
            wasActive = false; pendingToast = "disconnected";
        } else if (!found && !warnedUnsupported) {
            for (int jid = GLFW.GLFW_JOYSTICK_1; jid <= GLFW.GLFW_JOYSTICK_LAST; jid++) {
                if (GLFW.glfwJoystickPresent(jid)) {
                    String nm = GLFW.glfwGetJoystickName(jid);
                    padName = nm != null ? nm : "Unknown device";
                    warnedUnsupported = true; pendingToast = "unsupported";
                    break;
                }
            }
        }
        if (pendingToast != null && mc.getOverlay() == null) {
            switch (pendingToast) {
                case "connected":
                    SystemToast.add(mc.getToastManager(), SystemToast.Type.TUTORIAL_HINT,
                            new LiteralText("Controller connected"), new LiteralText(padName)); break;
                case "unsupported":
                    SystemToast.add(mc.getToastManager(), SystemToast.Type.TUTORIAL_HINT,
                            new LiteralText("Controller not supported"), new LiteralText(padName)); break;
                default:
                    SystemToast.add(mc.getToastManager(), SystemToast.Type.TUTORIAL_HINT,
                            new LiteralText("Controller disconnected"), null);
            }
            pendingToast = null;
        }
        if (!found) { PAD.active = false; return; }
        PAD.active = true;
        boolean[] now = bt.clone();
        restL = Math.min(restL, ax[4]); restR = Math.min(restR, ax[5]);
        boolean l2 = ax[4] - restL > TRIGGER_PULL;
        boolean r2 = ax[5] - restR > TRIGGER_PULL;
        float lx = dz(ax[0]), ly = dz(ax[1]);
        boolean inGame = mc.player != null && mc.currentScreen == null;
        if (!inGame) {
            PAD.forward = PAD.sideways = 0f; PAD.jump = PAD.sneak = false;
            key(mc.options.keyAttack, false, prevR2); key(mc.options.keyUse, false, prevL2);
            key(mc.options.keySneak, false, prev[L3]);
            if (sprintApplied) { mc.options.keySprint.setPressed(false); sprintApplied = false; }
            sprintOn = false;
            System.arraycopy(now, 0, prev, 0, 15); prevL2 = false; prevR2 = false;
            return;
        }
        float mag = (float) Math.hypot(lx, ly);
        if (mag > 0f) {
            float out = Math.min(1f, mag / 0.9f);
            PAD.forward = -(ly / mag) * out; PAD.sideways = -(lx / mag) * out;
        } else { PAD.forward = PAD.sideways = 0f; }
        PAD.jump = now[CROSS]; PAD.sneak = now[L3];
        boolean sneak = held(BINDS[ACT_SNEAK], l2, r2);
        boolean atk = held(BINDS[ACT_ATTACK], l2, r2);
        boolean use = held(BINDS[ACT_USE], l2, r2);
        boolean sneakWas = heldPrev(BINDS[ACT_SNEAK]);
        boolean atkWas = heldPrev(BINDS[ACT_ATTACK]);
        boolean useWas = heldPrev(BINDS[ACT_USE]);
        PAD.jump = held(BINDS[ACT_JUMP], l2, r2);
        PAD.sneak = sneak;
        key(mc.options.keySneak, sneak, sneakWas);
        key(mc.options.keyAttack, atk, atkWas);
        key(mc.options.keyUse, use, useWas);
        keyEdge(mc.options.keySwapHands, BINDS[ACT_SWAP], l2, r2);
        keyEdge(mc.options.keyInventory, BINDS[ACT_INVENTORY], l2, r2);
        if (System.currentTimeMillis() > suppressDropUntil) keyEdge(mc.options.keyDrop, BINDS[ACT_DROP], l2, r2);
        keyEdge(mc.options.keyTogglePerspective, BINDS[ACT_PERSPECTIVE], l2, r2);
        keyEdge(mc.options.keyPlayerList, BINDS[ACT_TAB], l2, r2);
        if (edge(BINDS[ACT_HOTBAR_NEXT], l2, r2)) mc.player.getInventory().selectedSlot = (mc.player.getInventory().selectedSlot + 1) % 9;
        if (edge(BINDS[ACT_HOTBAR_PREV], l2, r2)) mc.player.getInventory().selectedSlot = (mc.player.getInventory().selectedSlot + 8) % 9;
        if (edge(BINDS[ACT_SPRINT], l2, r2)) sprintOn = !sprintOn;
        if (PAD.forward <= 0.1f) sprintOn = false;
        if (sprintOn || sprintApplied) { mc.options.keySprint.setPressed(sprintOn); sprintApplied = sprintOn; }
        if (edge(BINDS[ACT_PAUSE], l2, r2)) mc.openPauseMenu(false);
        System.arraycopy(now, 0, prev, 0, 15); prevL2 = l2; prevR2 = r2;
    }
}
