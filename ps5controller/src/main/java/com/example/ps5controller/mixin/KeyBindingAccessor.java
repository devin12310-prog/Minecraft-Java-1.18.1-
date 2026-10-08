package com.example.ps5controller.mixin;

import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyBinding.class)
public interface KeyBindingAccessor {
    @Accessor("timesPressed")
    int ps5$getTimesPressed();

    @Accessor("timesPressed")
    void ps5$setTimesPressed(int value);
}
