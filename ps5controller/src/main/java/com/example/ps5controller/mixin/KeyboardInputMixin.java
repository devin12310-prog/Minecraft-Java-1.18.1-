package com.example.ps5controller.mixin;

import com.example.ps5controller.PS5ControllerClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class KeyboardInputMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void ps5$applyStick(boolean slowDown, CallbackInfo ci) {
        PS5ControllerClient.Pad p = PS5ControllerClient.PAD;
        if (!p.active) return;
        Input in = (Input) (Object) this;

        if (p.forward != 0f || p.sideways != 0f) {
            float f = p.forward, s = p.sideways;
            if (slowDown) { f *= 0.3f; s *= 0.3f; }
            in.movementForward = f;
            in.movementSideways = s;
            in.pressingForward = p.forward > 0.1f;
            in.pressingBack = p.forward < -0.1f;
            in.pressingLeft = p.sideways > 0.1f;
            in.pressingRight = p.sideways < -0.1f;
        }
        in.jumping |= p.jump;
        in.sneaking |= p.sneak;
    }
}
