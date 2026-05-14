package dev.dediamondpro.resourcify.gui.config;

import net.minecraft.client.gui.GuiScreen;

import com.cleanroommc.modularui.factory.ClientGUI;

/**
 * Forge's IModGuiFactory contract wants a Class<? extends GuiScreen> with a
 * (GuiScreen parent) constructor. Our actual settings UI is a MUI2
 * ModularScreen, which doesn't fit that contract. This thin GuiScreen
 * immediately hands off to ClientGUI.open() so the player lands on the MUI2
 * screen and the bridge instance is never visible.
 */
public class SettingsGuiBridge extends GuiScreen {

    private final GuiScreen parent;

    public SettingsGuiBridge(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        ClientGUI.open(new SettingsScreen());
    }
}
