package dev.dediamondpro.resourcify.mixins.early.angelica;

import net.coderbot.iris.gui.element.shaderselection.ShaderPackEntry;
import net.coderbot.iris.gui.screen.ShaderPackScreen;
import net.minecraft.client.renderer.Tessellator;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.dediamondpro.resourcify.gui.pack.PackOverlayRenderer;

/**
 * Draw the platform badge near the right edge of an Iris shader pack entry.
 * Shaders have no thumbnail to anchor to, so we put the badge inside the row
 * just inside the right padding.
 */
@Mixin(ShaderPackEntry.class)
public class MixinIrisShaderPackEntry {

    @Inject(method = "drawEntry", at = @At("TAIL"), remap = false)
    private void resourcify$drawPlatformBadge(ShaderPackScreen screen, int index, int x, int y, int listWidth,
        Tessellator tessellator, int mouseX, int mouseY, boolean isMouseOver, CallbackInfo ci) {
        String packName = ((ShaderPackEntry) (Object) this).getPackName();
        java.io.File folder = PackOverlayRenderer.INSTANCE.shaderpacksFolder();
        java.io.File file = PackOverlayRenderer.INSTANCE.shaderPackFile(folder, packName);
        if (file == null) return;
        String platform = PackOverlayRenderer.INSTANCE.lookupPlatform(folder, file);
        if (platform == null) return;
        int badge = 10;
        // Iris paints the row's label via drawCenteredString(name, ..., y, ..)
        // which places ~9px-tall text at [y, y+9]. Align the 10px badge to
        // that text band rather than the full slotHeight, since slot
        // rendering has internal padding and the visible row is shorter
        // than the 20px slot.
        int xRight = x + listWidth - badge - 4;
        int yMid = y - 1;
        PackOverlayRenderer.INSTANCE.drawBadge(platform, xRight, yMid, badge, mouseX, mouseY);
    }
}
