package dev.dediamondpro.resourcify.mixins.early.swansong;

import java.util.List;

import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.renderer.Tessellator;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.dediamondpro.resourcify.gui.pack.PackOverlayRenderer;
import dev.dediamondpro.resourcify.util.SwanSongHelper;

@Mixin(targets = "com.ventooth.swansong.gui.GuiShaders$GuiSlotShaders")
public abstract class MixinSwanSongGuiSlotShaders {

    @Shadow(remap = false)
    private List<String> shaderPackNames;

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
        int rowHeight = 16;
        int xCross = posX + listWidth - cross - 8;
        int xBadge = xCross - badge - 6;
        int yBadge = posY + 1;
        int yCross = posY;
        String platform = PackOverlayRenderer.INSTANCE.lookupPlatform(folder, file);
        if (platform != null) {
            PackOverlayRenderer.INSTANCE.drawBadge(platform, xBadge, yBadge, badge, mouseX, mouseY);
        }

        boolean isMouseOver = mouseX >= posX && mouseX < posX + listWidth
            && mouseY >= posY
            && mouseY < posY + rowHeight;
        if (isMouseOver) {
            PackOverlayRenderer.INSTANCE
                .drawDeleteButton(folder, file, packName, xCross, yCross, cross, mouseX, mouseY);
        }
    }
}
