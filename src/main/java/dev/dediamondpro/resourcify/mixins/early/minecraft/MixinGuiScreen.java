package dev.dediamondpro.resourcify.mixins.early.minecraft;

import net.minecraft.client.gui.GuiScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.dediamondpro.resourcify.gui.pack.PackOverlayRenderer;
import dev.dediamondpro.resourcify.gui.pack.PackScreensAddition;
import dev.dediamondpro.resourcify.services.ProjectType;
import dev.dediamondpro.resourcify.util.ShaderGuiHelper;

@Mixin(GuiScreen.class)
public class MixinGuiScreen {

    @Inject(method = "keyTyped", at = @At("HEAD"), cancellable = true)
    private void resourcify$keyTyped(char typedChar, int keyCode, CallbackInfo ci) {
        if (PackScreensAddition.INSTANCE.onKeyTyped(keyCode)) {
            ci.cancel();
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void resourcify$shaderGuiMouseClicked(int mouseX, int mouseY, int mouseButton, CallbackInfo ci) {
        GuiScreen self = (GuiScreen) (Object) this;
        if (!ShaderGuiHelper.INSTANCE.isShaderPackScreen(self)) return;

        GuiScreen shaderParent = ShaderGuiHelper.INSTANCE.getShaderScreenParent(self);
        boolean deleted = PackOverlayRenderer.INSTANCE.handleDeleteClick(mouseX, mouseY, mouseButton, () -> {
            ShaderGuiHelper.INSTANCE.openShaderPackScreen(shaderParent);
            return kotlin.Unit.INSTANCE;
        });
        if (deleted) {
            ci.cancel();
            return;
        }
        if (PackScreensAddition.INSTANCE.onMouseClick(
            mouseX,
            mouseY,
            mouseButton,
            ProjectType.IRIS_SHADER,
            ShaderGuiHelper.INSTANCE.getShaderpacksFolder(self),
            self)) {
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
