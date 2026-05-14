package dev.dediamondpro.resourcify.mixins.early.angelica;

import net.minecraft.client.gui.GuiScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.dediamondpro.resourcify.gui.pack.PackScreensAddition;
import dev.dediamondpro.resourcify.services.ProjectType;
import dev.dediamondpro.resourcify.util.IrisHelper;

/**
 * Adds the Resourcify "+" overlay button to Angelica's Iris shader pack
 * selection screen. Targeted by string so we don't need Angelica at compile
 * time - it's a runtimeOnly dep.
 */
@Mixin(targets = "net.coderbot.iris.gui.screen.ShaderPackScreen")
public class MixinShaderPackScreen {

    @Inject(method = "drawScreen", at = @At("TAIL"), remap = false)
    private void resourcify$drawAddButton(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        PackScreensAddition.INSTANCE
            .onRender(ProjectType.IRIS_SHADER, IrisHelper.INSTANCE.getShaderpacksFolder(), (GuiScreen) (Object) this);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), remap = false)
    private void resourcify$clickAddButton(int mouseX, int mouseY, int mouseButton, CallbackInfo ci) {
        PackScreensAddition.INSTANCE.onMouseClick(
            mouseX,
            mouseY,
            mouseButton,
            ProjectType.IRIS_SHADER,
            IrisHelper.INSTANCE.getShaderpacksFolder(),
            (GuiScreen) (Object) this);
    }
}
