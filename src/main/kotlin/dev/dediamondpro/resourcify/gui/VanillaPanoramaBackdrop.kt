package dev.dediamondpro.resourcify.gui

import cpw.mods.fml.common.Loader
import dev.dediamondpro.resourcify.mixins.early.minecraft.GuiMainMenuAccessor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.GuiMainMenu
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.renderer.OpenGlHelper
import net.minecraft.util.ResourceLocation
import org.fentanylsolutions.fentlib.gui.PanoramaOverlayRenderer
import org.lwjgl.opengl.GL11

/** Animated title panorama shared by all Resourcify screens. */
object VanillaPanoramaBackdrop {

    private const val CLEAR_MY_BACKGROUND_MOD_ID = "clearmybackground"
    private const val FALLBACK_DIM_COLOR = 0x40000000
    private val CLEAR_MY_BACKGROUND_OVERLAY =
        ResourceLocation(CLEAR_MY_BACKGROUND_MOD_ID, "textures/gui/menu_background.png")

    private val clearMyBackgroundLoaded by lazy { Loader.isModLoaded(CLEAR_MY_BACKGROUND_MOD_ID) }
    private val mainMenu by lazy { GuiMainMenu() }
    private var scaledWidth = -1
    private var scaledHeight = -1

    fun draw(partialTicks: Float) {
        val mc = Minecraft.getMinecraft()
        ensureInitialized(mc)
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT)
        try {
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT or GL11.GL_DEPTH_BUFFER_BIT)
            GL11.glDisable(GL11.GL_STENCIL_TEST)
            GL11.glDisable(GL11.GL_LIGHTING)
            (mainMenu as GuiMainMenuAccessor).`resourcify$renderSkybox`(0, 0, partialTicks)
            drawOverlay(mc)
        } finally {
            GL11.glPopAttrib()
        }
    }

    fun update() {
        // ClearMyBackground adopts this GuiMainMenu instance from initGui and
        // advances its timer from Minecraft.runTick. Advancing it here as well
        // would make the panorama run at double speed.
        if (!clearMyBackgroundLoaded) {
            mainMenu.updateScreen()
        }
    }

    private fun ensureInitialized(mc: Minecraft) {
        val resolution = ScaledResolution(mc, mc.displayWidth, mc.displayHeight)
        val width = resolution.scaledWidth
        val height = resolution.scaledHeight
        if (width == scaledWidth && height == scaledHeight) return
        mainMenu.setWorldAndResolution(mc, width, height)
        scaledWidth = width
        scaledHeight = height
    }

    private fun drawOverlay(mc: Minecraft) {
        val milkyPanorama = PanoramaOverlayRenderer.drawMilkyPanorama(scaledWidth, scaledHeight)
        if (!clearMyBackgroundLoaded) {
            if (!milkyPanorama) {
                Gui.drawRect(0, 0, scaledWidth, scaledHeight, FALLBACK_DIM_COLOR)
            }
            return
        }

        val blend = GL11.glIsEnabled(GL11.GL_BLEND)
        GL11.glEnable(GL11.GL_BLEND)
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO)
        GL11.glColor4f(1f, 1f, 1f, 1f)
        mc.textureManager.bindTexture(CLEAR_MY_BACKGROUND_OVERLAY)
        Gui.func_146110_a(0, 0, 0f, 0f, scaledWidth, scaledHeight, 32f, 32f)
        if (!blend) GL11.glDisable(GL11.GL_BLEND)
    }
}
