/*
 * This file is part of Resourcify
 * Copyright (C) 2023-2024 DeDiamondPro
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

package dev.dediamondpro.resourcify.gui.projectpage

import com.cleanroommc.modularui.api.drawable.IKey
import com.cleanroommc.modularui.api.widget.IWidget
import com.cleanroommc.modularui.screen.ModularPanel
import com.cleanroommc.modularui.screen.ModularScreen
import com.cleanroommc.modularui.widgets.ButtonWidget
import com.cleanroommc.modularui.widgets.ListWidget
import com.cleanroommc.modularui.widgets.TextWidget
import com.cleanroommc.modularui.widgets.layout.Flow
import dev.dediamondpro.resourcify.VintageResourcify
import dev.dediamondpro.resourcify.platform.Platform
import dev.dediamondpro.resourcify.services.IProject
import dev.dediamondpro.resourcify.services.IVersion
import dev.dediamondpro.resourcify.util.DownloadManager
import dev.dediamondpro.resourcify.util.MarkdownRenderer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.GuiScreenResourcePacks
import net.minecraft.util.EnumChatFormatting
import java.io.File

private class SimpleButton : ButtonWidget<SimpleButton>()
private class SimpleList : ListWidget<IWidget, SimpleList>()

// See BrowseScreen's commit 8a9f9e5 for why all state lives in the lambda
// closure rather than in instance fields.
class ProjectScreen(
    project: IProject,
    packsFolder: File,
    sourceParent: GuiScreen?,
) : ModularScreen(VintageResourcify.MODID, { _ ->
    val descriptionList = SimpleList()
        .child(TextWidget(IKey.str("Loading description...")))
    val versionsList = SimpleList()
        .child(TextWidget(IKey.str("Loading versions...")))

    fun loadVersions() {
        project.getVersions().thenAccept { versions ->
            Minecraft.getMinecraft().func_152344_a {
                versionsList.removeAll()
                val mcVersion = Platform.getMcVersion()
                val matching = versions.filter { it.getMinecraftVersions().contains(mcVersion) }
                if (matching.isEmpty()) {
                    versionsList.child(TextWidget(IKey.str("No $mcVersion versions available")))
                    return@func_152344_a
                }
                matching.forEach { version: IVersion ->
                    val row = Flow.row()
                        .height(22).widthRel(1f).margin(0, 0, 0, 3)
                        .child(
                            TextWidget(IKey.str(version.getName()))
                                .heightRel(1f).left(4).widthRel(0.62f)
                        )
                        .child(
                            SimpleButton()
                                .size(56, 16).right(2).top(3)
                                .overlay(IKey.str("Install"))
                                .onMousePressed { btn ->
                                    if (btn == 0) {
                                        install(version, packsFolder, sourceParent)
                                        true
                                    } else false
                                }
                        )
                    versionsList.child(row)
                }
            }
        }
    }

    fun loadDescription() {
        // Width: panel is full-screen; description column gets 60% of the
        // content area. Account for the list's scrollbar and outer padding.
        // We don't know exact pixel count here so use a conservative cap;
        // the wrap target is just used to break lines, not to clip.
        val width = (Minecraft.getMinecraft().displayWidth * 0.55 / 2).toInt().coerceAtLeast(200)
        project.getDescription().thenAccept { rawMd ->
            Minecraft.getMinecraft().func_152344_a {
                descriptionList.removeAll()
                if (rawMd.isBlank()) {
                    descriptionList.child(TextWidget(IKey.str("(no description)")))
                    return@func_152344_a
                }
                MarkdownRenderer.render(rawMd, width).forEach { descriptionList.child(it) }
            }
        }
    }

    loadVersions()
    loadDescription()

    val header = TextWidget(IKey.str(project.getName()).style(EnumChatFormatting.BOLD).scale(1.5f))
        .top(6).left(10)
    val authorLine = TextWidget(IKey.str("by ${project.getAuthor()}").style(EnumChatFormatting.GRAY))
        .top(22).left(10)
    val summary = TextWidget(IKey.str(project.getSummary()))
        .top(34).left(10).right(10)

    // Absolute positioning for the two columns. Flow.row was forcing both
    // children into the same cell, which left description and versions
    // overlapping. Anchoring left=description / right=versions guarantees
    // separation regardless of game window size.
    descriptionList
        .top(54).left(10).widthRel(0.58f).bottom(10)
    val versionsHeader = TextWidget(IKey.str("Versions").style(EnumChatFormatting.BOLD))
        .top(54).right(10).widthRel(0.38f).height(12)
    versionsList
        .top(70).right(10).widthRel(0.38f).bottom(10)

    ModularPanel.defaultPanel("vintage-resourcify-project")
        .full()
        .child(header)
        .child(authorLine)
        .child(summary)
        .child(descriptionList)
        .child(versionsHeader)
        .child(versionsList)
})

private fun install(version: IVersion, packsFolder: File, sourceParent: GuiScreen?) {
    val url = version.getDownloadUrl() ?: run {
        VintageResourcify.LOG.warn("No download URL for version {}", version.getName())
        return
    }
    if (!packsFolder.exists()) packsFolder.mkdirs()
    val target = File(packsFolder, version.getFileName())
    VintageResourcify.LOG.info("Installing {} -> {}", url, target)
    // Release any existing zip handle for this filename so the download can
    // overwrite (matters on Windows) and so MC drops the cached IResourcePack.
    if (target.exists()) Platform.closeResourcePack(target)
    DownloadManager.download(target, version.getSha1(), url, false) {
        VintageResourcify.LOG.info("Install complete: {}", target.name)
        // DownloadManager fires the callback off the main thread; bounce to
        // it before touching Minecraft's resource manager.
        Minecraft.getMinecraft().func_152344_a {
            Platform.reloadResourcePack(target)
            Platform.reloadResources()
            // Pop back to a freshly-initialized resource pack screen so the
            // user sees the new pack in the available list without manually
            // re-navigating Options -> Resource Packs.
            if (sourceParent != null) {
                Minecraft.getMinecraft().displayGuiScreen(GuiScreenResourcePacks(sourceParent))
            }
        }
    }
}
