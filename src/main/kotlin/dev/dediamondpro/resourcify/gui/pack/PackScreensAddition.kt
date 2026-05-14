/*
 * This file is part of Resourcify
 * Copyright (C) 2023 DeDiamondPro
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

package dev.dediamondpro.resourcify.gui.pack

import com.cleanroommc.modularui.factory.ClientGUI
import dev.dediamondpro.resourcify.VintageResourcify
import dev.dediamondpro.resourcify.gui.browsepage.BrowseScreen
import dev.dediamondpro.resourcify.mixins.early.minecraft.PackScreenAccessor
import dev.dediamondpro.resourcify.platform.Platform
import dev.dediamondpro.resourcify.services.IVersion
import dev.dediamondpro.resourcify.services.ProjectType
import dev.dediamondpro.resourcify.services.ServiceRegistry
import dev.dediamondpro.resourcify.util.DownloadManager
import dev.dediamondpro.resourcify.util.IrisHelper
import dev.dediamondpro.resourcify.util.LocalIndex
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.GuiScreenResourcePacks
import net.minecraft.util.ResourceLocation
import org.lwjgl.opengl.GL11
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Overlay buttons painted on top of the vanilla resource-pack screen and
 * Iris's shader-pack screen via mixins. Renders a "+" (open browser) and a
 * refresh icon (check installed packs for updates) at the top-right of the
 * host screen, plus a transient notification toast underneath.
 */
object PackScreensAddition {

    private const val BUTTON_SIZE = 20
    private const val ICON_SIZE = 16
    private const val BUTTON_GAP = 4
    private val PLUS_TEXTURE = ResourceLocation(VintageResourcify.MODID, "plus.png")
    private val UPDATE_TEXTURE = ResourceLocation(VintageResourcify.MODID, "update.png")

    // Transient bottom-of-button toast. Set by checkUpdates() and cleared by
    // the render path once its expiry passes.
    @Volatile private var toastText: String? = null
    @Volatile private var toastUntil: Long = 0L
    @Volatile private var checkInProgress = false
    // Per-screen-instance set: we prune the local index once when each screen
    // first renders. Identity hashes are fine because the screen object is
    // discarded after close.
    private val prunedScreens = java.util.Collections.newSetFromMap(java.util.WeakHashMap<Any, Boolean>())

    fun onRender(type: ProjectType, folder: File, screen: GuiScreen) {
        pruneIfFirstSeen(screen, folder)
        val (plusX, plusY) = plusOrigin() ?: return
        drawIconButton(plusX, plusY, PLUS_TEXTURE)
        // Only render the update button on screens where update flow makes
        // sense (data packs and worlds have hasUpdateButton=false upstream).
        if (type.hasUpdateButton) {
            val (upX, upY) = updateOrigin() ?: return
            drawIconButton(upX, upY, UPDATE_TEXTURE)
        }
        drawToast(plusY + BUTTON_SIZE + 4)
        // Drain any per-row badge hover tooltip last so it paints above the
        // pack list and our own buttons.
        PackOverlayRenderer.drainPendingTooltip()
    }

    fun onMouseClick(mouseX: Int, mouseY: Int, button: Int, type: ProjectType, folder: File, screen: GuiScreen) {
        if (button != 0) return
        val plus = plusOrigin() ?: return
        if (mouseX in plus.first..(plus.first + BUTTON_SIZE) && mouseY in plus.second..(plus.second + BUTTON_SIZE)) {
            openBrowser(type, folder, screen)
            return
        }
        if (type.hasUpdateButton) {
            val up = updateOrigin() ?: return
            if (mouseX in up.first..(up.first + BUTTON_SIZE) && mouseY in up.second..(up.second + BUTTON_SIZE)) {
                checkUpdates(type, folder, screen)
            }
        }
    }

    private fun pruneIfFirstSeen(screen: GuiScreen, folder: File) {
        synchronized(prunedScreens) {
            if (!prunedScreens.add(screen)) return
        }
        try {
            val removed = LocalIndex.forFolder(folder).prune()
            if (removed > 0) VintageResourcify.LOG.info("Pruned {} stale install index entries in {}", removed, folder)
        } catch (e: Throwable) {
            VintageResourcify.LOG.warn("Index prune failed for {}", folder, e)
        }
    }

    private fun openBrowser(type: ProjectType, folder: File, screen: GuiScreen) {
        val grandparent: GuiScreen? = when {
            screen is PackScreenAccessor -> (screen as PackScreenAccessor).parentScreen
            else -> IrisHelper.getShaderScreenParent(screen)
        }
        ClientGUI.open(BrowseScreen(type, folder, grandparent))
    }

    /**
     * Scan [folder] for installed packs, ask every applicable service for an
     * updated version, and download replacements in-place. UI is updated via
     * the toast so the user gets immediate feedback - all work happens off
     * the main thread.
     */
    private fun checkUpdates(type: ProjectType, folder: File, screen: GuiScreen) {
        if (checkInProgress) return
        checkInProgress = true
        showToast("Checking for updates...", durationMs = 30_000)
        Thread({
            try {
                val index = LocalIndex.forFolder(folder)
                // Only consider files we've installed ourselves; manually-
                // dropped packs are deliberately ignored so the user keeps
                // full control over them.
                val tracked = index.listEntries()
                    .mapNotNull { entry -> File(folder, entry.fileName).takeIf { it.exists() }?.let { it to entry } }
                if (tracked.isEmpty()) {
                    showToast("No tracked packs to check", durationMs = 5_000)
                    return@Thread
                }
                // Group by platform so we can ask each service only about
                // files it actually owns.
                val byPlatform: Map<String, List<File>> = tracked
                    .groupBy({ it.second.platform }) { it.first }
                val merged = mutableMapOf<File, IVersion>()
                for ((platform, files) in byPlatform) {
                    val service = ServiceRegistry.getAllServices()
                        .firstOrNull { it.getPlatformId() == platform && it.isProjectTypeSupported(type) }
                    if (service == null) {
                        VintageResourcify.LOG.warn("No service registered for platform {}", platform)
                        continue
                    }
                    val map = try {
                        service.getUpdates(files, type).join()
                    } catch (e: Throwable) {
                        VintageResourcify.LOG.warn("getUpdates failed for {}", service.getName(), e)
                        continue
                    }
                    for ((file, version) in map) {
                        if (version != null) merged[file] = version
                    }
                }
                if (merged.isEmpty()) {
                    showToast("All packs up to date", durationMs = 5_000)
                    return@Thread
                }
                downloadUpdates(merged, folder, type, screen)
            } finally {
                checkInProgress = false
            }
        }, "Resourcify-UpdateCheck").apply { isDaemon = true }.start()
    }

    private fun downloadUpdates(
        updates: Map<File, IVersion>,
        folder: File,
        type: ProjectType,
        screen: GuiScreen,
    ) {
        val total = updates.size
        val done = AtomicInteger(0)
        val failed = AtomicInteger(0)
        showToast("Updating 0/$total...", durationMs = 60_000)
        for ((oldFile, version) in updates) {
            val url = version.getDownloadUrl() ?: run {
                failed.incrementAndGet()
                onUpdateProgress(done.get(), failed.get(), total, folder, type, screen)
                continue
            }
            val newFile = File(folder, version.getFileName())
            // For resource packs, drop Forge's open file handle so we can
            // overwrite. Shader packs aren't held open by Forge so the
            // close-handle dance is a no-op (and there's no handle anyway).
            if (type == ProjectType.RESOURCE_PACK && oldFile.exists()) {
                try { Platform.closeResourcePack(oldFile) } catch (_: Throwable) {}
            }
            DownloadManager.download(newFile, version.getSha1(), url, false) {
                // Drop the old jar if the new file has a different name. If
                // the names happen to match (same project, version-included
                // filename collision) the download already overwrote it.
                val index = LocalIndex.forFolder(folder)
                val oldEntry = index.lookupByFile(oldFile)
                if (oldFile != newFile && oldFile.exists()) {
                    if (type == ProjectType.RESOURCE_PACK) {
                        try { Platform.closeResourcePack(oldFile) } catch (_: Throwable) {}
                    }
                    if (!oldFile.delete()) {
                        VintageResourcify.LOG.warn("Could not delete old pack {}", oldFile)
                    }
                    index.remove(oldFile.name)
                }
                if (oldEntry != null) {
                    index.record(newFile, oldEntry.platform, oldEntry.projectId)
                }
                done.incrementAndGet()
                onUpdateProgress(done.get(), failed.get(), total, folder, type, screen)
            }
        }
    }

    private fun onUpdateProgress(done: Int, failed: Int, total: Int, folder: File, type: ProjectType, screen: GuiScreen) {
        if (done + failed < total) {
            showToast("Updating ${done + failed}/$total...", durationMs = 60_000)
            return
        }
        val msg = if (failed == 0) "Updated $done pack${if (done == 1) "" else "s"}"
        else "Updated $done, failed $failed"
        showToast(msg, durationMs = 6_000)
        // Force a fresh listing so the new file names show up.
        Minecraft.getMinecraft().func_152344_a {
            if (type == ProjectType.RESOURCE_PACK) {
                Platform.reloadResources()
                val parent = (screen as? PackScreenAccessor)?.parentScreen
                if (parent != null) {
                    Minecraft.getMinecraft().displayGuiScreen(GuiScreenResourcePacks(parent))
                }
            } else if (type == ProjectType.IRIS_SHADER) {
                val parent = IrisHelper.getShaderScreenParent(screen)
                IrisHelper.openShaderPackScreen(parent)
            }
        }
    }

    private fun drawToast(yTop: Int) {
        val text = toastText ?: return
        if (Minecraft.getSystemTime() > toastUntil) {
            toastText = null
            return
        }
        val mc = Minecraft.getMinecraft()
        val fr = mc.fontRenderer ?: return
        val tw = fr.getStringWidth(text)
        val pad = 4
        val boxW = tw + pad * 2
        val boxH = fr.FONT_HEIGHT + pad * 2
        val (plusX, _) = plusOrigin() ?: return
        // Right-anchor under the buttons.
        val x = plusX + BUTTON_SIZE - boxW
        Gui.drawRect(x, yTop, x + boxW, yTop + boxH, 0xCC000000.toInt())
        fr.drawString(text, x + pad, yTop + pad, 0xFFFFFF, false)
    }

    private fun drawIconButton(x: Int, y: Int, texture: ResourceLocation) {
        Gui.drawRect(x, y, x + BUTTON_SIZE, y + BUTTON_SIZE, 0x66000000.toInt())
        Minecraft.getMinecraft().textureManager.bindTexture(texture)
        GL11.glColor4f(1f, 1f, 1f, 1f)
        val iconX = x + (BUTTON_SIZE - ICON_SIZE) / 2
        val iconY = y + (BUTTON_SIZE - ICON_SIZE) / 2
        Gui.func_152125_a(
            iconX, iconY, 0f, 0f, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE.toFloat(), ICON_SIZE.toFloat()
        )
    }

    private fun showToast(text: String, durationMs: Long) {
        toastText = text
        toastUntil = Minecraft.getSystemTime() + durationMs
    }

    private fun plusOrigin(): Pair<Int, Int>? {
        val screen = Minecraft.getMinecraft().currentScreen ?: return null
        return (screen.width - BUTTON_SIZE - 4) to 4
    }

    private fun updateOrigin(): Pair<Int, Int>? {
        val (px, py) = plusOrigin() ?: return null
        return (px - BUTTON_SIZE - BUTTON_GAP) to py
    }
}
