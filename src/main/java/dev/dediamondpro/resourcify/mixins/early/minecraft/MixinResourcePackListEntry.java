package dev.dediamondpro.resourcify.mixins.early.minecraft;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.ResourcePackListEntry;
import net.minecraft.client.resources.ResourcePackListEntryFound;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.dediamondpro.resourcify.gui.pack.PackOverlayRenderer;

/**
 * Overlay the platform badge in the bottom-right corner of the pack icon for
 * any resource pack we installed. Entries the user dropped in manually have
 * no index record and stay un-badged.
 */
@Mixin(ResourcePackListEntry.class)
public abstract class MixinResourcePackListEntry {

    @Inject(method = "drawEntry", at = @At("TAIL"))
    private void resourcify$drawPlatformBadge(int slotIndex, int x, int y, int listWidth, int slotHeight,
        Tessellator tessellator, int mouseX, int mouseY, boolean isSelected, CallbackInfo ci) {
        Object self = this;
        if (!(self instanceof ResourcePackListEntryFound)) return;
        ResourcePackRepositoryEntryAccessor entry = (ResourcePackRepositoryEntryAccessor) ((ResourcePackListEntryFound) self)
            .func_148318_i();
        java.io.File file = entry.getResourcePackFile();
        if (file == null) return;
        String platform = PackOverlayRenderer.INSTANCE
            .lookupPlatform(PackOverlayRenderer.INSTANCE.resourcePacksFolder(), file);
        if (platform == null) return;
        int badge = 10;
        // Vanilla draws the 32x32 pack icon at the row's top-left (x, y).
        // Anchor the badge to its bottom-right corner.
        PackOverlayRenderer.INSTANCE.drawBadge(platform, x + 32 - badge, y + 32 - badge, badge, mouseX, mouseY);
    }
}
