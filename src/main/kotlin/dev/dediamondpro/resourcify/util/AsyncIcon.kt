/*
 * This file is part of Resourcify
 * Copyright (C) 2024 DeDiamondPro
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License Version 3 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package dev.dediamondpro.resourcify.util

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext
import com.cleanroommc.modularui.theme.WidgetThemeEntry
import com.cleanroommc.modularui.widget.Widget
import dev.dediamondpro.resourcify.VintageResourcify
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Gui
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.util.ResourceLocation
import org.lwjgl.opengl.GL11
import java.awt.image.BufferedImage
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

/**
 * Fixed-size square icon for project thumbnails in the browse list and
 * project header. Async-fetches the URL and binds a DynamicTexture on the
 * first draw; until ready, draws nothing (the surrounding row reserves its
 * space via the explicit .size() on this widget).
 */
class AsyncIcon(private val url: URL?, private val sizePx: Int) : Widget<AsyncIcon>() {

    private var requested = false
    private var texture: ResourceLocation? = null
    private var imgW = 0
    private var imgH = 0
    private var failed = false

    init {
        size(sizePx, sizePx)
    }

    private var loggedFirstDraw = false

    override fun draw(context: ModularGuiContext, widgetTheme: WidgetThemeEntry<*>) {
        if (!loggedFirstDraw) {
            loggedFirstDraw = true
            VintageResourcify.LOG.info("AsyncIcon.draw first call url={}", url)
        }
        if (url == null || failed) return
        ensureRequested()
        val rl = texture ?: return
        // Gui.func_152125_a uses Tessellator and inherits the surrounding GL
        // state. When this is the first draw in a frame's child traversal
        // (e.g. project header icon as the panel's first child, or the first
        // image in a SimpleList) GL_TEXTURE_2D / GL_BLEND can still be off
        // from a prior pass, which renders the textured quad as flat white.
        // Enable defensively so the icon is independent of draw order.
        GL11.glEnable(GL11.GL_TEXTURE_2D)
        GL11.glEnable(GL11.GL_BLEND)
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
        Minecraft.getMinecraft().textureManager.bindTexture(rl)
        GL11.glColor4f(1f, 1f, 1f, 1f)
        Gui.func_152125_a(
            0, 0, 0f, 0f, imgW, imgH, sizePx, sizePx, imgW.toFloat(), imgH.toFloat(),
        )
    }

    private fun ensureRequested() {
        if (requested || url == null) return
        requested = true
        // Skip the wsrv.nl resize proxy: it can return a 44x44 image whose
        // pixel data is empty/transparent for some PNGs (e.g. Faithful 32x's
        // 256x256 icon), even though the host (cdn.modrinth.com) is already
        // in the trusted list and the format is ImageIO-readable. Fetching
        // the original and letting GL handle the downscale is more reliable
        // and the icons are tiny anyway.
        try {
            url.getImageAsync().thenAccept { img ->
                if (img == null) {
                    VintageResourcify.LOG.warn("AsyncIcon image null for {}", url)
                    failed = true
                    return@thenAccept
                }
                VintageResourcify.LOG.info(
                    "AsyncIcon got image {}x{} for {}", img.width, img.height, url,
                )
                Minecraft.getMinecraft()
                    .func_152344_a { adoptImage(img) }
            }
        } catch (e: Exception) {
            VintageResourcify.LOG.warn("Failed to load icon {}", url, e)
            failed = true
        }
    }

    private fun adoptImage(img: BufferedImage) {
        try {
            val dt = DynamicTexture(img)
            val name = "vresourcify_icon_${idCounter.incrementAndGet()}"
            texture = Minecraft.getMinecraft().textureManager.getDynamicTextureLocation(name, dt)
            imgW = img.width
            imgH = img.height
        } catch (e: Exception) {
            VintageResourcify.LOG.warn("Failed to register icon texture for {}", url, e)
            failed = true
        }
    }

    companion object {
        private val idCounter = AtomicInteger()
    }
}
