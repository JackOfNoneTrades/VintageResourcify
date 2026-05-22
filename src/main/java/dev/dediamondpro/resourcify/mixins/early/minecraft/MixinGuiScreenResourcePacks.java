package dev.dediamondpro.resourcify.mixins.early.minecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiOptionButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraft.client.resources.I18n;

import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.dediamondpro.resourcify.gui.pack.PackOverlayRenderer;
import dev.dediamondpro.resourcify.gui.pack.PackScreensAddition;
import dev.dediamondpro.resourcify.services.ProjectType;

@Mixin(GuiScreenResourcePacks.class)
public class MixinGuiScreenResourcePacks extends GuiScreen {

    private static final int DONE_BUTTON_ID = 1;
    private static final int CANCEL_BUTTON_ID = -33701;
    private static final int ACTION_BUTTON_WIDTH = 72;
    private static final int ACTION_BUTTON_GAP = 6;

    @Inject(method = "initGui", at = @At("TAIL"))
    private void resourcify$addCancelButton(CallbackInfo ci) {
        int cancelX = this.width / 2 + 4;
        int doneX = cancelX + ACTION_BUTTON_WIDTH + ACTION_BUTTON_GAP;

        for (GuiButton button : this.buttonList) {
            if (button.id == DONE_BUTTON_ID) {
                button.xPosition = doneX;
                button.width = ACTION_BUTTON_WIDTH;
                break;
            }
        }

        this.buttonList.add(
            new GuiOptionButton(
                CANCEL_BUTTON_ID,
                cancelX,
                this.height - 48,
                ACTION_BUTTON_WIDTH,
                20,
                I18n.format("gui.cancel", new Object[0])));
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    private void resourcify$cancelLikeEscape(GuiButton button, CallbackInfo ci) {
        if (button.enabled && button.id == CANCEL_BUTTON_ID) {
            this.keyTyped('\0', Keyboard.KEY_ESCAPE);
            ci.cancel();
        }
    }

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
            (GuiScreen) (Object) this,
            mouseX,
            mouseY);
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
        if (PackScreensAddition.INSTANCE.onMouseClick(
            mouseX,
            mouseY,
            mouseButton,
            ProjectType.RESOURCE_PACK,
            Minecraft.getMinecraft()
                .getResourcePackRepository()
                .getDirResourcepacks(),
            (GuiScreen) (Object) this)) {
            ci.cancel();
        }
    }

}
