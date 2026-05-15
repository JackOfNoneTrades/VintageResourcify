package dev.dediamondpro.resourcify.mixins.early.swansong;

import net.minecraft.client.gui.GuiScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.dediamondpro.resourcify.gui.pack.PackOverlayRenderer;
import dev.dediamondpro.resourcify.gui.pack.PackScreensAddition;
import dev.dediamondpro.resourcify.services.ProjectType;
import dev.dediamondpro.resourcify.util.SwanSongHelper;

@Mixin(targets = "com.ventooth.swansong.gui.GuiShaders")
public class MixinSwanSongGuiShaders {

    @Inject(method = "drawScreen", at = @At("HEAD"))
    private void resourcify$resetDeleteRegions(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        PackOverlayRenderer.INSTANCE.beginFrame();
    }

    @Inject(method = "drawScreen", at = @At("TAIL"))
    private void resourcify$drawAddButton(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        GuiScreen self = (GuiScreen) (Object) this;
        PackScreensAddition.INSTANCE
            .onRender(ProjectType.IRIS_SHADER, SwanSongHelper.INSTANCE.getShaderpacksFolder(), self, mouseX, mouseY);
        PackOverlayRenderer.INSTANCE.endFrame();
    }

}
