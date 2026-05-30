package dev.dediamondpro.resourcify.gui

import com.cleanroommc.modularui.api.drawable.IDrawable
import com.cleanroommc.modularui.screen.ModularScreen
import com.cleanroommc.modularui.screen.viewport.GuiContext
import com.cleanroommc.modularui.theme.WidgetTheme
import dev.dediamondpro.resourcify.VintageResourcify
import dev.dediamondpro.resourcify.util.ClientGuiTasks
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Gui
import net.minecraft.util.ResourceLocation
import org.lwjgl.opengl.GL11

const val CLOSE_BUTTON_TOP = 6
const val CLOSE_BUTTON_RIGHT = 10
const val CLOSE_BUTTON_SIZE = 16

class CloseButtonDrawable(private val hovered: Boolean) : IDrawable {
    override fun draw(context: GuiContext, x: Int, y: Int, width: Int, height: Int, widgetTheme: WidgetTheme) {
        applyColor(widgetTheme.color)
        GL11.glEnable(GL11.GL_TEXTURE_2D)
        GL11.glEnable(GL11.GL_BLEND)
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        Minecraft.getMinecraft().textureManager.bindTexture(CROSS_TEXTURE)
        GL11.glColor4f(1f, 1f, 1f, 1f)
        Gui.func_146110_a(
            x + (width - ICON_SIZE) / 2,
            y + (height - ICON_SIZE) / 2,
            if (hovered) ICON_SIZE.toFloat() else 0f,
            0f,
            ICON_SIZE,
            ICON_SIZE,
            TEXTURE_WIDTH.toFloat(),
            ICON_SIZE.toFloat(),
        )
    }

    companion object {
        private const val ICON_SIZE = CLOSE_BUTTON_SIZE
        private const val TEXTURE_WIDTH = 32
        private val CROSS_TEXTURE = ResourceLocation(VintageResourcify.MODID, "cross.png")
    }
}

fun closeLikeEscape(reason: String = "unspecified") {
    val screen = ModularScreen.getCurrent() ?: return
    val mcScreen = Minecraft.getMinecraft().currentScreen

    ClientGuiTasks.runNextClientTick {
        if (Minecraft.getMinecraft().currentScreen !== mcScreen || ModularScreen.getCurrent() !== screen) {
            return@runNextClientTick
        }
        closeLikeEscapeNow(reason, screen)
    }
}

private fun closeLikeEscapeNow(reason: String, screen: ModularScreen) {
    try {
        val panelManager = screen.panelManager
        val topPanel = panelManager.topMostPanel
        if (panelManager.isMainPanel(topPanel)) {
            screen.openParentOnClose(true)
        }
        panelManager.closeTopPanel()
    } catch (t: Throwable) {
        VintageResourcify.LOG.error("Failed to close Resourcify screen ({})", reason, t)
    }
}
