package dev.dediamondpro.resourcify.mixins.early.minecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenResourcePacks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.dediamondpro.resourcify.gui.pack.PackOverlayRenderer;
import dev.dediamondpro.resourcify.gui.pack.PackScreensAddition;
import dev.dediamondpro.resourcify.services.ProjectType;

@Mixin(GuiScreenResourcePacks.class)
public class MixinGuiScreenResourcePacks {

    @Inject(method = "drawScreen", at = @At("HEAD"))
    private void resourcify$resetDeleteRegions(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        // Clear the previous frame's delete-button hit list so this frame
        // can be populated fresh as each row draws itself.
        PackOverlayRenderer.INSTANCE.beginFrame();
    }

    @Inject(method = "drawScreen", at = @At("TAIL"))
    private void resourcify$drawAddButton(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        PackScreensAddition.INSTANCE.onRender(
            ProjectType.RESOURCE_PACK,
            Minecraft.getMinecraft()
                .getResourcePackRepository()
                .getDirResourcepacks(),
            (GuiScreen) (Object) this);
        PackOverlayRenderer.INSTANCE.endFrame();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void resourcify$clickAddOrDelete(int mouseX, int mouseY, int mouseButton, CallbackInfo ci) {
        GuiScreen parent = ((PackScreenAccessor) (Object) this).getParentScreen();
        boolean deleted = PackOverlayRenderer.INSTANCE.handleDeleteClick(mouseX, mouseY, mouseButton, () -> {
            // Re-display a fresh resource-pack screen so the deleted entry
            // disappears from the list immediately. Captured parent because
            // the call may run after the GuiYesNo dialog replaces `this`.
            Minecraft.getMinecraft()
                .displayGuiScreen(new GuiScreenResourcePacks(parent));
            return kotlin.Unit.INSTANCE;
        });
        if (deleted) {
            ci.cancel();
            return;
        }
        PackScreensAddition.INSTANCE.onMouseClick(
            mouseX,
            mouseY,
            mouseButton,
            ProjectType.RESOURCE_PACK,
            Minecraft.getMinecraft()
                .getResourcePackRepository()
                .getDirResourcepacks(),
            (GuiScreen) (Object) this);
    }
}
