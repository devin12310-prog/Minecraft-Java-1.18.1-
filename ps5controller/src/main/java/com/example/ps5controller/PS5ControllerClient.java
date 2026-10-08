package com.example.ps5controller;

import com.example.ps5controller.mixin.KeyBindingAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.LiteralText;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Clean PS3-style DualSense client. Sticks and D-pad are separate. */
public class PS5ControllerClient implements ClientModInitializer {
    public static class Pad {
        public volatile boolean active;
        public volatile float forward, sideways;
        public volatile boolean jump, sneak;
    }

    public static final Pad PAD = new Pad();
    public static final String[] ACT = {
            "Jump", "Sneak", "Attack", "Use", "Inventory", "Swap hands",
            "Drop", "Perspective", "Player list", "Hotbar next", "Hotbar prev", "Sprint", "Pause", "Back"
    };
    public static final int[] BINDS = new int[ACT.length];
    public static volatile int listening = -1;
    public static volatile int mapping = 0;
    public static int mapUp = 12, mapDown = 13, mapLeft = 14, mapRight = 11;

    private static final float DEAD = 0.18f;
    private static final int CROSS = 0, CIRCLE = 1, SQUARE = 2, TRIANGLE = 3, L1 = 4, R1 = 5, CREATE = 6, OPTIONS = 7, L3 = 9, R3 = 10;
    private static final int L2 = 20, R2 = 21;
    private static final String MAP =
            "030000004c050000e60c000000000000,PS5 Controller,a:b1,b:b2,x:b0,y:b3,back:b8,start:b9,guide:b13,"
                    + "leftstick:b10,rightstick:b11,leftshoulder:b4,rightshoulder:b5,dpup:h0.1,dpright:h0.2,dpdown:h0.4,dpleft:h0.8,"
                    + "lefttrigger:a3,righttrigger:a4,leftx:a0,lefty:a1,rightx:a2,righty:a5,platform:Windows,\n";

    private static GLFWGamepadState state;
    private static final float[] ax = new float[6];
    private static final boolean[] bt = new boolean[15];
    private static final boolean[] prev = new boolean[15];
    private static final boolean[] mprev = new boolean[15];
    private static boolean was, sprint, sprintApplied, prevL2, prevR2, mapped;
    private static int jid = -1;
    private static String name = "Gamepad";
    private static float restL = 1f, restR = 1f;
    private static long lastLook, lastMenu, lastNav, noDropUntil;
    private static int focus;
    private static boolean leftHeld;
    private static float touchX = -2f, touchY = -2f;
    private static boolean touchWas;

    @Override
    public void onInitializeClient() {
        load();
        ClientTickEvents.START_CLIENT_TICK.register(PS5ControllerClient::tick);
        WorldRenderEvents.START.register(ctx -> look(MinecraftClient.getInstance()));
        HudRenderCallback.EVENT.register((matrices, delta) -> hud(matrices));
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof TitleScreen || screen instanceof GameMenuScreen) {
                Screens.getButtons(screen).add(new ButtonWidget(4, 4, 120, 20, new LiteralText("Controller"),
                        b -> client.setScreen(new ControllerScreen(screen))));
            }
            ScreenEvents.afterRender(screen).register((s, matrices, mx, my, d) -> menu(MinecraftClient.getInstance(), s, matrices));
        });
    }

    public static void applyPs3() {
        int[] ps3 = {CROSS, L3, R2, L2, TRIANGLE, SQUARE, CIRCLE, R3, CREATE, R1, L1, R3, OPTIONS, CIRCLE};
        System.arraycopy(ps3, 0, BINDS, 0, BINDS.length);
        save();
    }

    public static void setBind(int action, int button) {
        if (action >= 0 && action < BINDS.length) BINDS[action] = button;
        save();
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
            case 11: return "D-pad bit 11";
            case 12: return "D-pad bit 12";
            case 13: return "D-pad bit 13";
            case 14: return "D-pad bit 14";
            case L2: return "L2";
            case R2: return "R2";
            default: return "Button " + b;
        }
    }

    public static int edgeButton() {
        if (l2() && !prevL2) return L2;
        if (r2() && !prevR2) return R2;
        for (int i = 0; i < 11; i++) if (bt[i] && !prev[i]) return i;
        return -1;
    }

    public static int dpadEdge() {
        for (int i = 11; i <= 14; i++) if (bt[i] && !prev[i]) return i;
        return -1;
    }

    private static void tick(MinecraftClient mc) {
        boolean on = read();
        PAD.active = on && mc.currentScreen == null && mc.player != null;
        if (on && !was) { was = true; name = GLFW.glfwGetJoystickName(jid); if (name == null) name = "DualSense"; }
        if (!on) was = false;
        if (!PAD.active) {
            PAD.forward = PAD.sideways = 0f;
            PAD.jump = PAD.sneak = false;
            System.arraycopy(bt, 0, prev, 0, 15);
            prevL2 = l2(); prevR2 = r2();
            return;
        }
        float lx = dz(ax[0]), ly = dz(ax[1]);
        float mag = (float) Math.hypot(lx, ly);
        if (mag > 0f) {
            float out = Math.min(1f, mag / 0.85f);
            PAD.forward = -(ly / mag) * out;
            PAD.sideways = -(lx / mag) * out;
        } else PAD.forward = PAD.sideways = 0f;
        boolean sneak = held(BINDS[1]), atk = held(BINDS[2]), use = held(BINDS[3]);
        PAD.jump = held(BINDS[0]);
        PAD.sneak = sneak;
        key(mc.options.keySneak, sneak, heldPrev(BINDS[1]));
        key(mc.options.keyAttack, atk, heldPrev(BINDS[2]));
        key(mc.options.keyUse, use, heldPrev(BINDS[3]));
        edgeKey(mc.options.keySwapHands, BINDS[5]);
        edgeKey(mc.options.keyInventory, BINDS[4]);
        if (System.currentTimeMillis() > noDropUntil) edgeKey(mc.options.keyDrop, BINDS[6]);
        edgeKey(mc.options.keyTogglePerspective, BINDS[7]);
        edgeKey(mc.options.keyPlayerList, BINDS[8]);
        if (edge(BINDS[9])) mc.player.getInventory().selectedSlot = (mc.player.getInventory().selectedSlot + 1) % 9;
        if (edge(BINDS[10])) mc.player.getInventory().selectedSlot = (mc.player.getInventory().selectedSlot + 8) % 9;
        if (edge(BINDS[11])) sprint = !sprint;
        if (PAD.forward <= 0.1f) sprint = false;
        mc.options.keySprint.setPressed(sprint);
        sprintApplied = sprint;
        if (edge(BINDS[12])) mc.openPauseMenu(false);
        System.arraycopy(bt, 0, prev, 0, 15);
        prevL2 = l2(); prevR2 = r2();
    }

    private static void look(MinecraftClient mc) {
        long n = System.nanoTime();
        float dt = lastLook == 0 ? 0f : (n - lastLook) / 1_000_000_000f;
        lastLook = n;
        if (dt > 0.1f) dt = 0.1f;
        if (mc.player == null || mc.currentScreen != null || !read()) return;
        float rx = dz(ax[2]), ry = dz(ax[3]);
        if (rx != 0f || ry != 0f) mc.player.changeLookDirection(rx * 1400f * dt, ry * 1400f * dt);
    }

    private static void hud(MatrixStack matrices) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.currentScreen != null || mc.options.hudHidden) return;
        String text = was ? "Controller: Connected" : "Controller: Not connected";
        int w = mc.textRenderer.getWidth(text);
        mc.textRenderer.drawWithShadow(matrices, text, mc.getWindow().getScaledWidth() - w - 4, 4, was ? 0x55FF55 : 0xFF5555);
    }

    private static void menu(MinecraftClient mc, Screen screen, MatrixStack matrices) {
        if (mc.currentScreen != screen || !read()) return;
        long handle = mc.getWindow().getHandle();
        double[] cx = new double[1], cy = new double[1];
        GLFW.glfwGetCursorPos(handle, cx, cy);
        long n = System.nanoTime();
        float dt = lastMenu == 0 ? 0f : (n - lastMenu) / 1_000_000_000f;
        lastMenu = n;
        if (dt > 0.1f) dt = 0.1f;
        float mx = dz(ax[0]), my = dz(ax[1]);
        if (mx != 0f || my != 0f) {
            int[] ww = new int[1], wh = new int[1];
            GLFW.glfwGetWindowSize(handle, ww, wh);
            cx[0] = clamp(cx[0] + mx * 900f * dt, 0, ww[0] - 1);
            cy[0] = clamp(cy[0] + my * 900f * dt, 0, wh[0] - 1);
            GLFW.glfwSetCursorPos(handle, cx[0], cy[0]);
        }
        touch(handle, cx, cy);
        double sx = cx[0] * screen.width / (double) mc.getWindow().getWidth();
        double sy = cy[0] * screen.height / (double) mc.getWindow().getHeight();
        int dir = dpadDir();
        if (dir != 0 && System.currentTimeMillis() - lastNav >= 180) {
            if (screen instanceof HandledScreen) moveSlot(mc, (HandledScreen<?>) screen, dir);
            else if (isList(screen) && (dir == 1 || dir == 2)) screen.keyPressed(dir == 1 ? GLFW.GLFW_KEY_UP : GLFW.GLFW_KEY_DOWN, 0, 0);
            else moveButton(mc, screen, dir);
            lastNav = System.currentTimeMillis();
        }
        boolean touchClick = touchDown();
        if (touchClick && !touchWas) { screen.mouseClicked(sx, sy, 0); leftHeld = true; }
        if (!touchClick && touchWas && leftHeld) { screen.mouseReleased(sx, sy, 0); leftHeld = false; }
        touchWas = touchClick;
        if (bt[CROSS] && !mprev[CROSS]) {
            if (isList(screen)) screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
            screen.mouseClicked(sx, sy, 0);
            leftHeld = true;
        }
        if (!bt[CROSS] && mprev[CROSS] && leftHeld) { screen.mouseReleased(sx, sy, 0); leftHeld = false; }
        if (screen instanceof HandledScreen && bt[TRIANGLE] && !mprev[TRIANGLE]) quick((HandledScreen<?>) screen, mc, sx, sy);
        if ((bt[CIRCLE] && !mprev[CIRCLE]) || (bt[OPTIONS] && !mprev[OPTIONS])) {
            screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
            prev[CIRCLE] = true;
            noDropUntil = System.currentTimeMillis() + 500;
        }
        hover(mc, screen, matrices, sx, sy);
        System.arraycopy(bt, 0, mprev, 0, 15);
    }

    private static int dpadDir() {
        boolean up = bit(mapUp), down = bit(mapDown), left = bit(mapLeft), right = bit(mapRight);
        if (bt[11] && bt[14]) return 4;
        if (up && left && !down && !right) return 4;
        if (up && down) return 0;
        if (left && right) return 0;
        if (up) return 1;
        if (down) return 2;
        if (left) return 3;
        if (right) return 4;
        return 0;
    }

    private static void moveButton(MinecraftClient mc, Screen screen, int dir) {
        List<ClickableWidget> widgets = widgets(screen);
        if (widgets.isEmpty()) return;
        if (focus < 0 || focus >= widgets.size()) focus = 0;
        int next = dir == 3 || dir == 1 ? focus - 1 : focus + 1;
        if (next < 0) next = widgets.size() - 1;
        if (next >= widgets.size()) next = 0;
        focus = next;
        ClickableWidget w = widgets.get(focus);
        double scaleX = (double) mc.getWindow().getWidth() / screen.width;
        double scaleY = (double) mc.getWindow().getHeight() / screen.height;
        GLFW.glfwSetCursorPos(mc.getWindow().getHandle(), (w.x + w.getWidth() / 2.0) * scaleX, (w.y + w.getHeight() / 2.0) * scaleY);
    }

    private static void moveSlot(MinecraftClient mc, HandledScreen<?> screen, int dir) {
        int left = (screen.width - 176) / 2;
        int top = top(screen);
        double[] cx = new double[1], cy = new double[1];
        GLFW.glfwGetCursorPos(mc.getWindow().getHandle(), cx, cy);
        double sx = cx[0] * screen.width / (double) mc.getWindow().getWidth();
        double sy = cy[0] * screen.height / (double) mc.getWindow().getHeight();
        Slot cur = slotAt(screen, sx, sy);
        double ox = cur == null ? sx : left + cur.x + 8;
        double oy = cur == null ? sy : top + cur.y + 8;
        Slot best = null;
        double bestScore = 1e9;
        for (Slot slot : screen.getScreenHandler().slots) {
            double wx = left + slot.x + 8, wy = top + slot.y + 8, dx = wx - ox, dy = wy - oy;
            if (Math.hypot(dx, dy) < 4) continue;
            boolean ok = dir == 1 ? dy < -2 : dir == 2 ? dy > 2 : dir == 3 ? dx < -2 : dx > 2;
            if (!ok) continue;
            double score = Math.abs(dir < 3 ? dy : dx) + Math.abs(dir < 3 ? dx : dy) * 0.35;
            if (score < bestScore) { bestScore = score; best = slot; }
        }
        if (best == null) return;
        double scaleX = (double) mc.getWindow().getWidth() / screen.width;
        double scaleY = (double) mc.getWindow().getHeight() / screen.height;
        GLFW.glfwSetCursorPos(mc.getWindow().getHandle(), (left + best.x + 8) * scaleX, (top + best.y + 8) * scaleY);
    }

    private static void quick(HandledScreen<?> screen, MinecraftClient mc, double sx, double sy) {
        Slot slot = slotAt(screen, sx, sy);
        if (slot == null || mc.player == null || mc.interactionManager == null) return;
        mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, slot.id, 0, SlotActionType.QUICK_MOVE, mc.player);
    }

    private static void hover(MinecraftClient mc, Screen screen, MatrixStack matrices, double sx, double sy) {
        if (screen instanceof HandledScreen) {
            Slot slot = slotAt((HandledScreen<?>) screen, sx, sy);
            if (slot != null && slot.hasStack()) {
                screen.renderTooltip(matrices, slot.getStack().getName(), (int) sx, (int) sy);
                return;
            }
        }
        for (ClickableWidget w : widgets(screen)) {
            if (sx >= w.x && sx <= w.x + w.getWidth() && sy >= w.y && sy <= w.y + w.getHeight() && w.getMessage() != null) {
                String text = w.getMessage().getString();
                mc.textRenderer.drawWithShadow(matrices, text, screen.width / 2 - mc.textRenderer.getWidth(text) / 2, 6, 0xFFFFFF);
                return;
            }
        }
    }

    private static Slot slotAt(HandledScreen<?> screen, double sx, double sy) {
        int left = (screen.width - 176) / 2, top = top(screen);
        Slot best = null;
        double bestD = 16;
        for (Slot slot : screen.getScreenHandler().slots) {
            double d = Math.hypot(sx - (left + slot.x + 8), sy - (top + slot.y + 8));
            if (d < bestD) { bestD = d; best = slot; }
        }
        return best;
    }

    private static int top(HandledScreen<?> screen) {
        int max = 0;
        for (Slot slot : screen.getScreenHandler().slots) if (slot.y > max) max = slot.y;
        return (screen.height - (max + 26)) / 2;
    }

    private static List<ClickableWidget> widgets(Screen screen) {
        List<ClickableWidget> out = new ArrayList<>();
        for (Element e : screen.children()) {
            if (e instanceof ClickableWidget) {
                ClickableWidget w = (ClickableWidget) e;
                if (w.visible && w.active && w.getWidth() >= 10) out.add(w);
            }
        }
        return out;
    }

    private static boolean isList(Screen screen) {
        for (Element e : screen.children()) {
            String n = e.getClass().getName();
            if (n.contains("WorldList") || n.contains("EntryList")) return true;
        }
        return false;
    }

    private static void touch(long handle, double[] cx, double[] cy) {
        FloatBuffer a;
        try { a = GLFW.glfwGetJoystickAxes(jid); } catch (Throwable t) { return; }
        if (a == null || a.remaining() < 8) return;
        float tx = a.get(6), ty = a.get(7);
        if (Math.abs(tx) < 0.08f && Math.abs(ty) < 0.08f) { touchX = -2f; return; }
        if (touchX < -1f) { touchX = tx; touchY = ty; return; }
        int[] ww = new int[1], wh = new int[1];
        GLFW.glfwGetWindowSize(handle, ww, wh);
        cx[0] = clamp(cx[0] + (tx - touchX) * ww[0] * 1.3, 0, ww[0] - 1);
        cy[0] = clamp(cy[0] + (ty - touchY) * wh[0] * 1.3, 0, wh[0] - 1);
        touchX = tx; touchY = ty;
        GLFW.glfwSetCursorPos(handle, cx[0], cy[0]);
    }

    private static boolean touchDown() {
        try {
            ByteBuffer b = GLFW.glfwGetJoystickButtons(jid);
            return b != null && b.remaining() > 13 && b.get(13) != 0;
        } catch (Throwable t) { return false; }
    }

    private static boolean read() {
        if (state == null) state = GLFWGamepadState.malloc();
        if (!mapped) {
            mapped = true;
            try (MemoryStack stack = MemoryStack.stackPush()) { GLFW.glfwUpdateGamepadMappings(stack.UTF8(MAP)); }
            catch (Throwable ignored) { }
        }
        for (int id = GLFW.GLFW_JOYSTICK_1; id <= GLFW.GLFW_JOYSTICK_LAST; id++) {
            if (!GLFW.glfwJoystickPresent(id)) continue;
            if (GLFW.glfwJoystickIsGamepad(id) && GLFW.glfwGetGamepadState(id, state)) {
                for (int i = 0; i < 6; i++) ax[i] = state.axes(i);
                for (int i = 0; i < 15; i++) bt[i] = state.buttons(i) == GLFW.GLFW_PRESS;
                hats(id);
                jid = id;
                if (restL > 2f) { restL = ax[4]; restR = ax[5]; }
                return true;
            }
        }
        return false;
    }

    private static void hats(int id) {
        try {
            ByteBuffer h = GLFW.glfwGetJoystickHats(id);
            if (h == null) return;
            for (int i = 0; i < h.remaining(); i++) {
                int hat = h.get(i) & 0xFF;
                if ((hat & 1) != 0) bt[11] = true;
                if ((hat & 2) != 0) bt[12] = true;
                if ((hat & 4) != 0) bt[13] = true;
                if ((hat & 8) != 0) bt[14] = true;
            }
        } catch (Throwable ignored) { }
    }

    private static boolean bit(int i) { return i >= 0 && i < bt.length && bt[i]; }
    private static boolean l2() { return ax[4] - restL > 0.4f; }
    private static boolean r2() { return ax[5] - restR > 0.4f; }
    private static boolean held(int b) { return b == L2 ? l2() : b == R2 ? r2() : bit(b); }
    private static boolean heldPrev(int b) { return b == L2 ? prevL2 : b == R2 ? prevR2 : b >= 0 && b < prev.length && prev[b]; }
    private static boolean edge(int b) { return held(b) && !heldPrev(b); }
    private static void edgeKey(KeyBinding kb, int b) { key(kb, held(b), heldPrev(b)); }
    private static void key(KeyBinding kb, boolean down, boolean wasDown) {
        if (down && !wasDown) {
            KeyBindingAccessor acc = (KeyBindingAccessor) kb;
            acc.ps5$setTimesPressed(acc.ps5$getTimesPressed() + 1);
        }
        if (down != wasDown) kb.setPressed(down);
    }
    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
    private static float dz(float v) { float a = Math.abs(v); return a < DEAD ? 0f : Math.copySign((a - DEAD) / (1f - DEAD), v); }

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("ps5binds.txt"); }
    private static void load() {
        int[] ps3 = {CROSS, L3, R2, L2, TRIANGLE, SQUARE, CIRCLE, R3, CREATE, R1, L1, R3, OPTIONS, CIRCLE};
        System.arraycopy(ps3, 0, BINDS, 0, BINDS.length);
        try {
            if (!Files.exists(file())) return;
            String raw = Files.readString(file()).trim();
            String[] map = raw.contains("|") ? raw.substring(raw.indexOf('|') + 1).split(",") : new String[0];
            String[] binds = (raw.contains("|") ? raw.substring(0, raw.indexOf('|')) : raw).split(",");
            for (int i = 0; i < BINDS.length && i < binds.length; i++) BINDS[i] = Integer.parseInt(binds[i].trim());
            if (map.length >= 4) {
                mapUp = Integer.parseInt(map[0].trim());
                mapDown = Integer.parseInt(map[1].trim());
                mapLeft = Integer.parseInt(map[2].trim());
                mapRight = Integer.parseInt(map[3].trim());
            }
        } catch (Exception ignored) { }
    }
    private static void save() {
        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < BINDS.length; i++) { if (i > 0) sb.append(','); sb.append(BINDS[i]); }
            sb.append('|').append(mapUp).append(',').append(mapDown).append(',').append(mapLeft).append(',').append(mapRight);
            Files.writeString(file(), sb.toString());
        } catch (Exception ignored) { }
    }
}
