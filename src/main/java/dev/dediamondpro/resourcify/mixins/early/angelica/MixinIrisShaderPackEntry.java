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
        if (isMouseOver) {
            PackOverlayRenderer.INSTANCE.markShaderEntryHovered(screen, packName);
        }
        java.io.File folder = PackOverlayRenderer.INSTANCE.shaderpacksFolder();
        java.io.File file = PackOverlayRenderer.INSTANCE.shaderPackFile(folder, packName);
        if (file == null) return;
        int cross = 16;
        int badge = 10;
        // Always reserve cross-width on the right so badge position stays
        // stable whether or not the row is hovered.
        int xCross = x + listWidth - cross - 2;
        int xBadge = xCross - badge - 4;
        int yMid = y - 1;
        String platform = PackOverlayRenderer.INSTANCE.lookupPlatform(folder, file);
        if (platform != null) {
            PackOverlayRenderer.INSTANCE.drawBadge(platform, xBadge, yMid, badge, mouseX, mouseY);
        }
        if (isMouseOver) {
            // Iris draws the row label at y; the 16px cross visually
            // centers on the ~9px text band when offset upward by half
            // the diff.
            int yCross = y - 4;
            PackOverlayRenderer.INSTANCE
                .drawDeleteButton(folder, file, packName, xCross, yCross, cross, mouseX, mouseY);
        }
    }
}
