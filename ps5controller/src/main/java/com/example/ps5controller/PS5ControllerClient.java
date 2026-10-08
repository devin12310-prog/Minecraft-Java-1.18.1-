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
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
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

public class PS5ControllerClient implements ClientModInitializer {
    public static class Pad { public volatile boolean active, jump, sneak; public volatile float forward, sideways; }
    public static final Pad PAD = new Pad();
    public static final String[] ACT = {"Jump","Sneak","Attack","Use","Inventory","Swap","Drop","Look","Hotbar next","Hotbar prev","Sprint","Pause"};
    public static final int[] BINDS = new int[ACT.length];
    public static volatile int listening = -1, mapping = 0;
    public static String padName = "None";
    public static boolean connected;
    public static int selected = -1;
    public static float deadzone = 0.18f, lookSpeed = 1500f, menuSpeed = 1000f;
    public static boolean invertY, showGuide = true, guideFull = true, showStatus = true;
    public static int mapUp = 102, mapDown = 104, mapLeft = 108, mapRight = 101;
    public static void applyDefault() { int[] d = {0,9,20,21,3,2,1,10,5,4,10,7}; System.arraycopy(d,0,BINDS,0,BINDS.length); save(); }
    public static void applyPs3() { applyDefault(); }
    public static void usePad(int id) { selected = id; }
    public static void cycleDeadzone(){ deadzone = deadzone>=0.3f?0.1f:Math.round((deadzone+0.04f)*100f)/100f; save(); }
    public static void cycleLook(){ lookSpeed = lookSpeed>=2200?800:lookSpeed+200; save(); }
    public static void cycleMenu(){ menuSpeed = menuSpeed>=1600?600:menuSpeed+200; save(); }
    public static void toggleInvert(){ invertY=!invertY; save(); }
    public static void toggleGuide(){ showGuide=!showGuide; save(); }
    public static void toggleGuideSize(){ guideFull=!guideFull; save(); }
    public static void toggleStatus(){ showStatus=!showStatus; save(); }
    public static void saveMap() { save(); }
    public static void setBind(int a, int b) { if (a>=0 && a<BINDS.length) BINDS[a]=b; save(); }
    public static String buttonName(int b) {
        switch (b) {
            case 0: return "Cross"; case 1: return "Circle"; case 2: return "Square"; case 3: return "Triangle";
            case 4: return "L1"; case 5: return "R1"; case 6: return "Create"; case 7: return "Options"; case 8: return "Touchpad"; case 9: return "L3"; case 10: return "R3";
            case 20: return "L2"; case 21: return "R2"; default: return "Button "+b;
        }
    }
    public static int edgeButton() {
        if (l2() && !prevL2) return 20; if (r2() && !prevR2) return 21;
        for (int i = 0; i < 11; i++) if (bt[i] && !prev[i]) return i; return -1;
    }
    public static int dpadEdge() {
        for (int i = 11; i <= 14; i++) if (bt[i] && !prev[i]) return i;
        if (lastHat != 0 && lastHat != prevHat) return 100 + lastHat; return -1;
    }
    private static final int CROSS=0,CIRCLE=1,SQUARE=2,TRIANGLE=3,L1=4,R1=5,OPTIONS=7,L3=9,R3=10;
    private static GLFWGamepadState state;
    private static final float[] ax = new float[6];
    private static final boolean[] bt = new boolean[15], prev = new boolean[15];
    private static boolean on, sprint, mapped, leftHeld, prevL2, prevR2;
    private static int jid=-1, focus, lastHat, prevHat;
    private static float restL=9f, restR=9f;
    private static long lastLook, lastMenu, lastNav, noDropUntil;
    private static final String SDL = "030000004c050000e60c000000000000,PS5 Controller,a:b1,b:b2,x:b0,y:b3,back:b8,start:b9,leftstick:b10,rightstick:b11,leftshoulder:b4,rightshoulder:b5,dpup:h0.1,dpright:h0.2,dpdown:h0.4,dpleft:h0.8,lefttrigger:a3,righttrigger:a4,leftx:a0,lefty:a1,rightx:a2,righty:a5,platform:Windows,\n";
    @Override public void onInitializeClient() {
        load();
        ClientTickEvents.START_CLIENT_TICK.register(PS5ControllerClient::tick);
        WorldRenderEvents.START.register(ctx -> look(MinecraftClient.getInstance()));
        HudRenderCallback.EVENT.register((m,d) -> hud(m));
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof OptionsScreen)
                Screens.getButtons(screen).add(new ButtonWidget(screen.width / 2 + 5, screen.height - 28, 150, 20, new LiteralText("Controller"), b -> client.setScreen(new ControllerScreen(screen))));
            ScreenEvents.afterRender(screen).register((s, matrices, mx, my, dt) -> menu(MinecraftClient.getInstance(), s, matrices));
        });
    }
    private static void tick(MinecraftClient mc) {
        boolean found = read(); on = found; connected = found;
        PAD.active = found && mc.player != null && mc.currentScreen == null;
        if (!PAD.active) { if (listening < 0 && mapping == 0) copyPrev(); return; }
        float lx=dz(ax[0]), ly=dz(ax[1]), mag=(float)Math.hypot(lx,ly);
        if (mag>0) { float out=Math.min(1f,mag/0.85f); PAD.forward=-(ly/mag)*out; PAD.sideways=-(lx/mag)*out; } else PAD.forward=PAD.sideways=0;
        PAD.jump = held(BINDS[0]); PAD.sneak = held(BINDS[1]);
        key(mc.options.keySneak, held(BINDS[1]), heldPrev(BINDS[1]));
        key(mc.options.keyAttack, held(BINDS[2]), heldPrev(BINDS[2]));
        key(mc.options.keyUse, held(BINDS[3]), heldPrev(BINDS[3]));
        edge(mc.options.keyInventory, BINDS[4]); edge(mc.options.keySwapHands, BINDS[5]);
        if (System.currentTimeMillis()>noDropUntil) edge(mc.options.keyDrop, BINDS[6]);
        edge(mc.options.keyTogglePerspective, BINDS[7]);
        if (edge(BINDS[8])) mc.player.getInventory().selectedSlot=(mc.player.getInventory().selectedSlot+1)%9;
        if (edge(BINDS[9])) mc.player.getInventory().selectedSlot=(mc.player.getInventory().selectedSlot+8)%9;
        if (edge(BINDS[10])) sprint=!sprint; if (PAD.forward<=0.1f) sprint=false;
        mc.options.keySprint.setPressed(sprint);
        if (edge(BINDS[11])) mc.openPauseMenu(false);
        copyPrev();
    }
    private static void look(MinecraftClient mc) {
        long n=System.nanoTime(); float dt=lastLook==0?0:Math.min(0.1f,(n-lastLook)/1_000_000_000f); lastLook=n;
        if (mc.player==null || mc.currentScreen!=null || !read()) return;
        float rx=dz(ax[2]), ry=dz(ax[3]); if (invertY) ry=-ry;
        if (rx!=0 || ry!=0) mc.player.changeLookDirection(rx*lookSpeed*dt, ry*lookSpeed*dt);
    }
    private static void hud(MatrixStack m) {
        MinecraftClient mc=MinecraftClient.getInstance();
        if (mc.player==null || mc.currentScreen!=null) return;
        if (showStatus) { String t=connected ? "PS5: "+padName : "PS5: not connected"; mc.textRenderer.drawWithShadow(m,t,mc.getWindow().getScaledWidth()-mc.textRenderer.getWidth(t)-4,4,connected?0x55FF55:0xFF5555); }
        if (showGuide) { String[] lines = guideFull ? new String[]{"Cross Jump","L2 Use","R2 Mine","Triangle Inventory","Circle Drop","Square Swap","L3 Sprint","R3 Sneak","L1/R1 Hotbar","Options Pause"} : new String[]{"Cross Jump","L2 Use","R2 Mine","Triangle Inventory"}; int y = mc.getWindow().getScaledHeight()-12-lines.length*10; for (String line : lines) { mc.textRenderer.drawWithShadow(m, line, 4, y, 0xFFFFFF); y += 10; } }
    }
    public static java.util.List<String> pads() {
        java.util.List<String> out=new java.util.ArrayList<>();
        for (int id=0; id<=GLFW.GLFW_JOYSTICK_LAST; id++) { if (!GLFW.glfwJoystickPresent(id)) continue; String n=GLFW.glfwGetJoystickName(id); out.add(id+": "+(n==null?"Controller":n)+(id==selected?"  [using]":"")); }
        return out;
    }
    private static void menu(MinecraftClient mc, Screen screen, MatrixStack matrices) {
        if (listening >= 0 || mapping > 0) return;
        if (mc.currentScreen!=screen || !read()) return;
        long handle=mc.getWindow().getHandle(); double[] cx={0}, cy={0}; GLFW.glfwGetCursorPos(handle,cx,cy);
        long n=System.nanoTime(); float dt=lastMenu==0?0:Math.min(0.1f,(n-lastMenu)/1_000_000_000f); lastMenu=n;
        touch(handle, cx, cy); float mx=dz(ax[0]), my=dz(ax[1]);
        if (mx!=0 || my!=0) { int[] ww={1}, wh={1}; GLFW.glfwGetWindowSize(handle,ww,wh); cx[0]=clamp(cx[0]+mx*menuSpeed*dt,0,ww[0]-1); cy[0]=clamp(cy[0]+my*menuSpeed*dt,0,wh[0]-1); GLFW.glfwSetCursorPos(handle,cx[0],cy[0]); }
        double sx=cx[0]*screen.width/mc.getWindow().getWidth(), sy=cy[0]*screen.height/mc.getWindow().getHeight();
        int dir=dpad();
        if (dir!=0 && System.currentTimeMillis()-lastNav>170) {
            if (screen instanceof HandledScreen) moveSlot(mc,(HandledScreen<?>)screen,dir);
            else if (list(screen) && (dir==1||dir==2)) screen.keyPressed(dir==1?GLFW.GLFW_KEY_UP:GLFW.GLFW_KEY_DOWN,0,0);
            else moveButton(mc,screen,dir);
            lastNav=System.currentTimeMillis(); GLFW.glfwGetCursorPos(handle,cx,cy);
            sx=cx[0]*screen.width/mc.getWindow().getWidth(); sy=cy[0]*screen.height/mc.getWindow().getHeight();
        }
        if (bt[CROSS] && !prev[CROSS]) { if (list(screen)) screen.keyPressed(GLFW.GLFW_KEY_ENTER,0,0); screen.mouseClicked(sx,sy,0); leftHeld=true; }
        if (!bt[CROSS] && prev[CROSS] && leftHeld) { screen.mouseReleased(sx,sy,0); leftHeld=false; }
        if (screen instanceof HandledScreen && held(BINDS[4]) && !heldPrev(BINDS[4])) quick(mc,(HandledScreen<?>)screen,sx,sy);
        if (screen instanceof HandledScreen && bt[SQUARE] && !prev[SQUARE]) half(mc,(HandledScreen<?>)screen,sx,sy);
        if (bt[CIRCLE] && !prev[CIRCLE]) { screen.keyPressed(GLFW.GLFW_KEY_ESCAPE,0,0); noDropUntil=System.currentTimeMillis()+500; }
        if (screen instanceof HandledScreen) { Slot slot=slotAt((HandledScreen<?>)screen,sx,sy); if (slot!=null && slot.hasStack()) screen.renderTooltip(matrices, slot.getStack().getName(), (int)sx, (int)sy); }
        drawCross(mc, matrices, sx, sy);
        if (showGuide) mc.textRenderer.drawWithShadow(matrices, screen instanceof HandledScreen ? "Cross Select  Triangle Move stack  Square Half  Circle Back  D-pad Slots" : "Cross Select  Circle Back  Left stick Mouse  D-pad Move", 4, screen.height-12, 0xFFFFFF);
        copyPrev();
    }
    private static void drawCross(MinecraftClient mc, MatrixStack m, double x, double y) {
        m.push(); m.translate(x, y, 0); m.scale(2.4f, 2.4f, 1f);
        mc.textRenderer.draw(m, "+", -3f, -4f, 0xFF000000);
        mc.textRenderer.draw(m, "+", -2.4f, -4f, 0xFFFFFFFF);
        m.pop();
    }
    private static int dpad() {
        if (hit(mapRight) || lastHat == 1 || (bt[11] && !bt[12] && !bt[13])) return 4;
        if (hit(mapUp) || lastHat == 2 || bt[12]) return 1;
        if (hit(mapDown) || lastHat == 4 || bt[13]) return 2;
        if (hit(mapLeft) || lastHat == 8 || bt[14]) return 3; return 0;
    }
    private static boolean hit(int code) { if (code>=100) return lastHat==(code-100); return code>=0 && code<bt.length && bt[code]; }
    private static void moveButton(MinecraftClient mc, Screen screen, int dir) {
        List<ClickableWidget> list=new ArrayList<>();
        for (Element e: screen.children()) if (e instanceof ClickableWidget && ((ClickableWidget)e).visible) list.add((ClickableWidget)e);
        if (list.isEmpty()) return;
        focus = dir==3 || dir==1 ? focus-1 : focus+1; if (focus<0) focus=list.size()-1; if (focus>=list.size()) focus=0;
        ClickableWidget w=list.get(focus);
        GLFW.glfwSetCursorPos(mc.getWindow().getHandle(), (w.x+w.getWidth()/2.0)*mc.getWindow().getWidth()/screen.width, (w.y+w.getHeight()/2.0)*mc.getWindow().getHeight()/screen.height);
    }
    private static void moveSlot(MinecraftClient mc, HandledScreen<?> screen, int dir) {
        int left=(screen.width-176)/2, top=top(screen); double[] cx={0}, cy={0}; GLFW.glfwGetCursorPos(mc.getWindow().getHandle(),cx,cy);
        double sx=cx[0]*screen.width/mc.getWindow().getWidth(), sy=cy[0]*screen.height/mc.getWindow().getHeight();
        Slot cur=slotAt(screen,sx,sy); double ox=cur==null?sx:left+cur.x+8, oy=cur==null?sy:top+cur.y+8, best=1e9; Slot pick=null;
        for (Slot slot: screen.getScreenHandler().slots) { double dx=left+slot.x+8-ox, dy=top+slot.y+8-oy; boolean ok=dir==1?dy<-2:dir==2?dy>2:dir==3?dx<-2:dx>2; if (!ok) continue; double score=Math.abs(dir<3?dy:dx); if (score<best) { best=score; pick=slot; } }
        if (pick==null) return;
        GLFW.glfwSetCursorPos(mc.getWindow().getHandle(), (left+pick.x+8)*mc.getWindow().getWidth()/screen.width, (top+pick.y+8)*mc.getWindow().getHeight()/screen.height);
    }
    private static void half(MinecraftClient mc, HandledScreen<?> screen, double sx, double sy) { Slot slot=slotAt(screen,sx,sy); if (slot!=null && mc.player!=null && mc.interactionManager!=null) mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, slot.id, 1, SlotActionType.PICKUP, mc.player); }
    private static void quick(MinecraftClient mc, HandledScreen<?> screen, double sx, double sy) { Slot slot=slotAt(screen,sx,sy); if (slot!=null && mc.player!=null && mc.interactionManager!=null) mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, slot.id, 0, SlotActionType.QUICK_MOVE, mc.player); }
    private static Slot slotAt(HandledScreen<?> screen, double sx, double sy) { int left=(screen.width-176)/2, top=top(screen); Slot best=null; double bd=16; for (Slot slot: screen.getScreenHandler().slots) { double d=Math.hypot(sx-(left+slot.x+8), sy-(top+slot.y+8)); if (d<bd) { bd=d; best=slot; } } return best; }
    private static int top(HandledScreen<?> screen) { int max=0; for (Slot s: screen.getScreenHandler().slots) if (s.y>max) max=s.y; return (screen.height-(max+26))/2; }
    private static boolean list(Screen screen) { for (Element e: screen.children()) if (e.getClass().getName().contains("WorldList")||e.getClass().getName().contains("EntryList")) return true; return false; }
    private static void touch(long handle, double[] cx, double[] cy) { FloatBuffer a; try { a=GLFW.glfwGetJoystickAxes(jid); } catch (Throwable t) { return; } if (a==null || a.remaining()<8) return; float tx=a.get(6), ty=a.get(7); if (Math.abs(tx)<0.08f && Math.abs(ty)<0.08f) return; int[] ww={1}, wh={1}; GLFW.glfwGetWindowSize(handle,ww,wh); cx[0]=clamp(cx[0]+tx*40,0,ww[0]-1); cy[0]=clamp(cy[0]+ty*40,0,wh[0]-1); GLFW.glfwSetCursorPos(handle,cx[0],cy[0]); }
    private static boolean read() {
        if (state==null) state=GLFWGamepadState.malloc();
        if (!mapped) { mapped=true; try (MemoryStack st=MemoryStack.stackPush()) { GLFW.glfwUpdateGamepadMappings(st.UTF8(SDL)); } catch (Throwable ignored) {} }
        int start = selected>=0 && GLFW.glfwJoystickPresent(selected) ? selected : 0;
        int end = selected>=0 && GLFW.glfwJoystickPresent(selected) ? selected : GLFW.GLFW_JOYSTICK_LAST;
        for (int id=start; id<=end; id++) {
            if (!GLFW.glfwJoystickPresent(id)) continue;
            if (GLFW.glfwJoystickIsGamepad(id) && GLFW.glfwGetGamepadState(id, state)) {
                for (int i=0;i<6;i++) ax[i]=state.axes(i); for (int i=0;i<15;i++) bt[i]=state.buttons(i)==GLFW.GLFW_PRESS;
            } else {
                FloatBuffer a=GLFW.glfwGetJoystickAxes(id); ByteBuffer b=GLFW.glfwGetJoystickButtons(id);
                if (a==null||b==null||a.remaining()<4||b.remaining()<12) continue;
                ax[0]=a.get(0); ax[1]=a.get(1); ax[2]=a.remaining()>2?a.get(2):0; ax[3]=a.remaining()>5?a.get(5):0; ax[4]=a.remaining()>3?a.get(3):-1; ax[5]=a.remaining()>4?a.get(4):-1;
                for (int i=0;i<15;i++) bt[i]=false;
                bt[CROSS]=b.get(1)!=0; bt[CIRCLE]=b.get(2)!=0; bt[SQUARE]=b.get(0)!=0; bt[TRIANGLE]=b.get(3)!=0; bt[L1]=b.get(4)!=0; bt[R1]=b.get(5)!=0; bt[OPTIONS]=b.get(9)!=0;
                if (b.remaining()>10) bt[L3]=b.get(10)!=0; if (b.remaining()>11) bt[R3]=b.get(11)!=0;
            }
            hats(id); jid=id; String n=GLFW.glfwGetJoystickName(id); padName=n==null?"PS5 Controller":n; if (restL>2f) { restL=ax[4]; restR=ax[5]; } return true;
        }
        return false;
    }
    private static void hats(int id) { lastHat=0; try { ByteBuffer h=GLFW.glfwGetJoystickHats(id); if (h==null) return; for (int i=0;i<h.remaining();i++) { int hat=h.get(i)&0xFF; if (hat!=0) lastHat=hat; if ((hat&1)!=0) bt[11]=true; if ((hat&2)!=0) bt[12]=true; if ((hat&4)!=0) bt[13]=true; if ((hat&8)!=0) bt[14]=true; } } catch (Throwable ignored) {} }
    private static boolean l2(){return ax[4]-restL>0.4f;} private static boolean r2(){return ax[5]-restR>0.4f;}
    private static boolean held(int b){return b==20?l2():b==21?r2():b>=0&&b<bt.length&&bt[b];}
    private static boolean heldPrev(int b){return b==20?prevL2:b==21?prevR2:b>=0&&b<prev.length&&prev[b];}
    private static boolean edge(int b){return held(b)&&!heldPrev(b);}
    private static void edge(KeyBinding kb,int b){key(kb,held(b),heldPrev(b));}
    private static void key(KeyBinding kb, boolean down, boolean was) { if (down && !was) { KeyBindingAccessor a=(KeyBindingAccessor)kb; a.ps5$setTimesPressed(a.ps5$getTimesPressed()+1); } if (down!=was) kb.setPressed(down); }
    private static void copyPrev(){ System.arraycopy(bt,0,prev,0,15); prevL2=l2(); prevR2=r2(); prevHat=lastHat; }
    private static double clamp(double v,double a,double b){return Math.max(a,Math.min(b,v));}
    private static float dz(float v){float a=Math.abs(v); return a<deadzone?0:Math.copySign((a-deadzone)/(1-deadzone),v);}
    private static Path file(){return FabricLoader.getInstance().getConfigDir().resolve("ps5binds3.txt");}
    private static void load(){ applyDefault(); try { if (!Files.exists(file())) return; String raw=Files.readString(file()).trim(); String[] map=raw.contains("|")?raw.substring(raw.indexOf('|')+1).split(","):new String[0]; String[] binds=(raw.contains("|")?raw.substring(0,raw.indexOf('|')):raw).split(","); for (int i=0;i<BINDS.length && i<binds.length;i++) BINDS[i]=Integer.parseInt(binds[i].trim()); if (map.length>=4){ mapUp=Integer.parseInt(map[0]); mapDown=Integer.parseInt(map[1]); mapLeft=Integer.parseInt(map[2]); mapRight=Integer.parseInt(map[3]); } } catch (Exception ignored) {} }
    private static void save(){ try { StringBuilder sb=new StringBuilder(); for (int i=0;i<BINDS.length;i++){ if(i>0)sb.append(','); sb.append(BINDS[i]); } sb.append('|').append(mapUp).append(',').append(mapDown).append(',').append(mapLeft).append(',').append(mapRight); Files.writeString(file(), sb.toString()); } catch (Exception ignored) {} }
}
