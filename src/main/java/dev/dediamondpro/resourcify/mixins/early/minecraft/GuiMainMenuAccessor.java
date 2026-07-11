package dev.dediamondpro.resourcify.mixins.early.minecraft;

import net.minecraft.client.gui.GuiMainMenu;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GuiMainMenu.class)
public interface GuiMainMenuAccessor {

    @Invoker("renderSkybox")
    void resourcify$renderSkybox(int mouseX, int mouseY, float partialTicks);
}
