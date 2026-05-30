package dev.dediamondpro.resourcify.mixins.early.angelica;

import java.util.List;

import net.coderbot.iris.gui.element.ShaderPackSelectionList;
import net.coderbot.iris.gui.element.shaderselection.BaseEntry;
import net.coderbot.iris.gui.element.shaderselection.ShaderPackEntry;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.dediamondpro.resourcify.gui.pack.PackOverlayRenderer;

@Mixin(ShaderPackSelectionList.class)
public class MixinIrisShaderPackSelectionList {

    @Shadow(remap = false)
    @Final
    private List<BaseEntry> entries;

    @Inject(method = "elementClicked(IZIII)Z", at = @At("HEAD"), remap = false)
    private void resourcify$playSelectSoundOnShaderPackClick(int index, boolean doubleClick, int mouseX, int mouseY,
        int mouseButton, CallbackInfoReturnable<Boolean> cir) {
        if (mouseButton != 0 || index < 0 || index >= this.entries.size()) return;
        if (this.entries.get(index) instanceof ShaderPackEntry) {
            PackOverlayRenderer.INSTANCE.playEntrySelectSound();
        }
    }
}
