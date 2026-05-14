package dev.dediamondpro.resourcify.mixins.early.minecraft;

import net.minecraft.client.gui.GuiScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.dediamondpro.resourcify.gui.pack.PackScreensAddition;

@Mixin(GuiScreen.class)
public class MixinGuiScreen {

    @Inject(method = "keyTyped", at = @At("HEAD"), cancellable = true)
    private void resourcify$keyTyped(char typedChar, int keyCode, CallbackInfo ci) {
        if (PackScreensAddition.INSTANCE.onKeyTyped(keyCode)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleMouseInput", at = @At("HEAD"), cancellable = true)
    private void resourcify$handleMouseInput(CallbackInfo ci) {
        if (PackScreensAddition.INSTANCE.onMouseInput()) {
            ci.cancel();
        }
    }
}
