package dev.dediamondpro.resourcify.mixins.early.swansong;

import java.util.List;

import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.EnumChatFormatting;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.dediamondpro.resourcify.gui.pack.PackOverlayRenderer;
import dev.dediamondpro.resourcify.util.SwanSongHelper;

@Mixin(targets = "com.ventooth.swansong.gui.GuiShaders$GuiSlotShaders")
public abstract class MixinSwanSongGuiSlotShaders {

    @Shadow(remap = false)
    private List<String> shaderPackNames;

    private boolean resourcify$drawingHoveredEntry;

    @Inject(method = "elementClicked", at = @At("HEAD"), cancellable = true, remap = false)
    private void resourcify$playSelectSoundOnShaderPackClick(int index, boolean doubleClicked, int mouseX, int mouseY,
        CallbackInfo ci) {
        if (PackOverlayRenderer.INSTANCE.isDeleteButtonAt(mouseX, mouseY)) {
            ci.cancel();
            return;
        }
        if (this.shaderPackNames == null || index < 0 || index >= this.shaderPackNames.size()) return;
        PackOverlayRenderer.INSTANCE.playEntrySelectSound();
    }

    @Inject(method = "drawSlot", at = @At("HEAD"))
    private void resourcify$markHoveredEntry(int index, int posX, int posY, int contentY, Tessellator tess, int mouseX,
        int mouseY, CallbackInfo ci) {
        this.resourcify$drawingHoveredEntry = this.resourcify$isMouseOverEntry(posX, posY, mouseX, mouseY);
        if (this.shaderPackNames == null || index < 0
            || index >= this.shaderPackNames.size()
            || !this.resourcify$drawingHoveredEntry) {
            return;
        }
        PackOverlayRenderer.INSTANCE.markShaderEntryHovered(this, this.shaderPackNames.get(index));
    }

    @ModifyArg(
        method = "drawSlot",
        at = @At(
            value = "INVOKE",
            target = "Lcom/ventooth/swansong/gui/GuiShaders;access$000(Lcom/ventooth/swansong/gui/GuiShaders;Ljava/lang/String;III)V",
            remap = false),
        index = 1)
    private String resourcify$boldHoveredEntryLabel(String label) {
        if (!this.resourcify$drawingHoveredEntry) return label;
        return EnumChatFormatting.BOLD + label;
    }

    @Inject(method = "drawSlot", at = @At("RETURN"))
    private void resourcify$clearHoveredEntry(int index, int posX, int posY, int contentY, Tessellator tess, int mouseX,
        int mouseY, CallbackInfo ci) {
        this.resourcify$drawingHoveredEntry = false;
    }

    @Inject(method = "drawSlot", at = @At("TAIL"))
    private void resourcify$drawPlatformBadge(int index, int posX, int posY, int contentY, Tessellator tess, int mouseX,
        int mouseY, CallbackInfo ci) {
        if (this.shaderPackNames == null || index < 0 || index >= this.shaderPackNames.size()) return;
        String packName = this.shaderPackNames.get(index);
        java.io.File folder = SwanSongHelper.INSTANCE.getShaderpacksFolder();
        java.io.File file = PackOverlayRenderer.INSTANCE.shaderPackFile(folder, packName);
        if (file == null) return;

        int cross = 12;
        int badge = 10;
        int listWidth = ((GuiSlot) (Object) this).getListWidth();
        int xCross = posX + listWidth - cross - 8;
        int xBadge = xCross - badge - 6;
        int yBadge = posY + 1;
        int yCross = posY;
        String platform = PackOverlayRenderer.INSTANCE.lookupPlatform(folder, file);
        if (platform != null) {
            PackOverlayRenderer.INSTANCE.drawBadge(platform, xBadge, yBadge, badge, mouseX, mouseY);
        }

        boolean isMouseOver = this.resourcify$isMouseOverEntry(posX, posY, mouseX, mouseY);
        if (isMouseOver) {
            PackOverlayRenderer.INSTANCE
                .drawDeleteButton(folder, file, packName, xCross, yCross, cross, mouseX, mouseY);
        }
    }

    private boolean resourcify$isMouseOverEntry(int posX, int posY, int mouseX, int mouseY) {
        int listWidth = ((GuiSlot) (Object) this).getListWidth();
        int rowHeight = 16;
        return mouseX >= posX && mouseX < posX + listWidth && mouseY >= posY && mouseY < posY + rowHeight;
    }
}
