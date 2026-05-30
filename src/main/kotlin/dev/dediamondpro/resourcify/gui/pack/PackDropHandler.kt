package dev.dediamondpro.resourcify.gui.pack

import dev.dediamondpro.resourcify.VintageResourcify
import dev.dediamondpro.resourcify.gui.ResourcifyStyle
import dev.dediamondpro.resourcify.services.ProjectType
import dev.dediamondpro.resourcify.util.ShaderGuiHelper
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.GuiScreenResourcePacks
import net.minecraft.client.resources.I18n
import org.fentanylsolutions.fentlib.util.drop.DropListener
import org.fentanylsolutions.fentlib.util.drop.GuiTransitionScheduler
import org.fentanylsolutions.fentlib.util.drop.WindowDropTarget
import java.io.File

object PackDropHandler : DropListener {

    @Volatile private var dragMode = DragMode.NONE

    fun register() {
        GuiTransitionScheduler.register()
        WindowDropTarget.register()
        WindowDropTarget.addListener(this)
    }

    override fun onDragBegin() {
        dragMode = DragMode.NONE
        val context = currentPackContext() ?: return
        dragMode = DragMode.PACK_FILE
        PackDropOverlay.show(context.type, context.folder, context.screen)
    }

    override fun onDragPosition(sdlX: Float, sdlY: Float) {
        PackDropOverlay.updateDropPosition(sdlX, sdlY)
    }

    override fun onDropFile(filePath: String, sdlX: Float, sdlY: Float) {
        if (dragMode == DragMode.NONE && !PackDropOverlay.isOpen()) return
        dragMode = DragMode.PACK_FILE
        PackDropOverlay.updateDropPosition(sdlX, sdlY)
        PackDropOverlay.setDroppedFile(filePath)
        VintageResourcify.LOG.info("[PackDropHandler] DROP_FILE x={} y={} file={}", sdlX, sdlY, filePath)
    }

    override fun onDragComplete(result: WindowDropTarget.DropResult) {
        val mode = dragMode
        dragMode = DragMode.NONE
        if (mode == DragMode.PACK_FILE || PackDropOverlay.isOpen()) {
            PackDropOverlay.complete()
        }
    }

    private fun currentPackContext(): DropContext? {
        val mc = Minecraft.getMinecraft()
        val screen = mc.currentScreen ?: return null
        return when {
            screen is GuiScreenResourcePacks -> DropContext(
                screen,
                ProjectType.RESOURCE_PACK,
                mc.resourcePackRepository.dirResourcepacks,
            )
            ShaderGuiHelper.isShaderPackScreen(screen) -> DropContext(
                screen,
                ProjectType.IRIS_SHADER,
                ShaderGuiHelper.getShaderpacksFolder(screen),
            )
            else -> null
        }
    }

    private data class DropContext(
        val screen: GuiScreen,
        val type: ProjectType,
        val folder: File,
    )

    private enum class DragMode {
        NONE,
        PACK_FILE,
    }
}

private object PackDropOverlay {

    private const val PANEL_WIDTH = 260
    private const val PANEL_HEIGHT = 96
    private const val PANEL_MARGIN = 10
    private const val PANEL_PADDING = 10
    private const val ZONE_HEIGHT = 44
    private const val OVERLAY_SCRIM = 0x66000000

    @Volatile private var overlayOpen = false
    @Volatile private var pendingFilePath: String? = null
    @Volatile private var sdlX = 0f
    @Volatile private var sdlY = 0f
    @Volatile private var hovered = false

    private var activeType: ProjectType? = null
    private var activeFolder: File? = null
    private var parentScreen: GuiScreen? = null

    fun isOpen(): Boolean = overlayOpen

    fun show(type: ProjectType, folder: File, parent: GuiScreen) {
        if (overlayOpen) return
        pendingFilePath = null
        hovered = false
        activeType = type
        activeFolder = folder
        parentScreen = parent
        overlayOpen = true
        Minecraft.getMinecraft().displayGuiScreen(OverlayScreen(parent, type))
    }

    fun dismiss() {
        overlayOpen = false
        val mc = Minecraft.getMinecraft()
        if (mc.currentScreen is OverlayScreen) {
            mc.displayGuiScreen(parentScreen)
        }
        parentScreen = null
    }

    fun updateDropPosition(x: Float, y: Float) {
        sdlX = x
        sdlY = y
    }

    fun setDroppedFile(path: String) {
        pendingFilePath = path
    }

    fun complete() {
        val screen = Minecraft.getMinecraft().currentScreen as? OverlayScreen
        val accepted = screen?.isDropInside() ?: hovered
        val filePath = pendingFilePath
        val type = activeType
        val folder = activeFolder

        pendingFilePath = null
        activeType = null
        activeFolder = null
        dismiss()

        if (filePath.isNullOrEmpty() || type == null || folder == null) return
        if (!accepted) {
            VintageResourcify.LOG.info("[PackDropOverlay] Dropped outside zone, ignoring")
            return
        }

        GuiTransitionScheduler.nextTick {
            PackScreensAddition.importPackFile(type, folder, File(filePath))
        }
    }

    private fun isShader(type: ProjectType): Boolean {
        return type == ProjectType.IRIS_SHADER || type == ProjectType.OPTIFINE_SHADER
    }

    private fun title(type: ProjectType): String {
        return if (isShader(type)) {
            I18n.format("resourcify.drop.shader.title")
        } else {
            I18n.format("resourcify.drop.resource.title")
        }
    }

    private fun panelBounds(width: Int, height: Int): Bounds {
        val panelW = PANEL_WIDTH.coerceAtMost((width - PANEL_MARGIN * 2).coerceAtLeast(120))
        val panelH = PANEL_HEIGHT.coerceAtMost((height - PANEL_MARGIN * 2).coerceAtLeast(72))
        return Bounds((width - panelW) / 2, (height - panelH) / 2, panelW, panelH)
    }

    private fun zoneBounds(width: Int, height: Int): Bounds {
        val panel = panelBounds(width, height)
        val zoneW = (panel.width - PANEL_PADDING * 2).coerceAtLeast(60)
        val zoneH = ZONE_HEIGHT.coerceAtMost((panel.height - 42).coerceAtLeast(24))
        return Bounds(panel.x + PANEL_PADDING, panel.y + panel.height - PANEL_PADDING - zoneH, zoneW, zoneH)
    }

    private fun isInside(bounds: Bounds, guiX: Float, guiY: Float): Boolean {
        return guiX >= bounds.x && guiX < bounds.x + bounds.width && guiY >= bounds.y && guiY < bounds.y + bounds.height
    }

    private data class Bounds(val x: Int, val y: Int, val width: Int, val height: Int)

    private class OverlayScreen(
        private val parent: GuiScreen,
        private val type: ProjectType,
    ) : GuiScreen() {

        override fun setWorldAndResolution(mc: Minecraft, width: Int, height: Int) {
            super.setWorldAndResolution(mc, width, height)
            parent.setWorldAndResolution(mc, width, height)
        }

        override fun updateScreen() {
            parent.updateScreen()
            hovered = isDropInside()
            if (!overlayOpen) {
                mc.displayGuiScreen(parent)
            }
        }

        override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
            parent.drawScreen(-10_000, -10_000, partialTicks)
            val style = ResourcifyStyle.palette("default")
            Gui.drawRect(0, 0, width, height, OVERLAY_SCRIM)

            val panel = panelBounds(width, height)
            Gui.drawRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, style.panel)

            fontRendererObj.drawString(title(type), panel.x + PANEL_PADDING, panel.y + 8, style.textPrimary, false)

            val bounds = zoneBounds(width, height)
            Gui.drawRect(
                bounds.x,
                bounds.y,
                bounds.x + bounds.width,
                bounds.y + bounds.height,
                if (hovered) style.buttonHover else style.panelInset,
            )
            if (hovered) {
                Gui.drawRect(bounds.x, bounds.y + bounds.height - 1, bounds.x + bounds.width, bounds.y + bounds.height, style.accent)
            }
            drawCenteredString(
                fontRendererObj,
                I18n.format("resourcify.drop.subtitle"),
                width / 2,
                bounds.y + (bounds.height - fontRendererObj.FONT_HEIGHT) / 2,
                if (hovered) style.textPrimary else style.textSecondary,
            )

            super.drawScreen(mouseX, mouseY, partialTicks)
        }

        override fun keyTyped(typedChar: Char, keyCode: Int) {
            if (keyCode == org.lwjgl.input.Keyboard.KEY_ESCAPE) {
                dismiss()
                return
            }
            super.keyTyped(typedChar, keyCode)
        }

        fun isDropInside(): Boolean {
            val gui = WindowDropTarget.sdlToGuiCoords(sdlX, sdlY)
            return isInside(zoneBounds(width, height), gui[0], gui[1])
        }
    }
}
