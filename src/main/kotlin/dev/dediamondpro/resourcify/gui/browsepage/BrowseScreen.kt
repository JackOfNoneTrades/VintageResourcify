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

package dev.dediamondpro.resourcify.gui.browsepage

import com.cleanroommc.modularui.api.drawable.IKey
import com.cleanroommc.modularui.screen.ModularPanel
import com.cleanroommc.modularui.screen.ModularScreen
import com.cleanroommc.modularui.widgets.TextWidget
import com.cleanroommc.modularui.widgets.layout.Flow
import dev.dediamondpro.resourcify.VintageResourcify
import dev.dediamondpro.resourcify.platform.Platform
import dev.dediamondpro.resourcify.services.IProject
import dev.dediamondpro.resourcify.services.ProjectType
import dev.dediamondpro.resourcify.services.ServiceRegistry
import dev.dediamondpro.resourcify.util.MultiThreading
import net.minecraft.client.Minecraft

// MUI2's ModularScreen constructor invokes the panel-builder lambda before
// any Kotlin property initializers run on the subclass, so we can't store
// per-instance state as constructor-arg-backed fields. Instead, all state is
// captured in the lambda closure: the `type` constructor argument is read
// directly off the stack when the lambda is constructed, and the results
// container is a local var that the async callback closes over.
class BrowseScreen(type: ProjectType) : ModularScreen(VintageResourcify.MODID, { _ ->
    val service = ServiceRegistry.getDefaultService(type)
    val resultsColumn: Flow = Flow.column()
        .top(20).left(8).right(8).bottom(8)
        .child(TextWidget(IKey.str("Loading...")))

    fun applyResults(projects: List<IProject>) {
        resultsColumn.removeAll()
        if (projects.isEmpty()) {
            resultsColumn.child(TextWidget(IKey.str("No results")))
            return
        }
        projects.take(20).forEach { project ->
            resultsColumn.child(
                TextWidget(IKey.str("- ${project.getName()} by ${project.getAuthor()}"))
            )
        }
    }

    val defaultSortKey = service.getSortOptions().keys.firstOrNull() ?: ""
    MultiThreading.supplyAsync {
        try {
            service.search("", defaultSortKey, listOf(Platform.getMcVersion()), emptyList(), 0, type)
        } catch (e: Exception) {
            VintageResourcify.LOG.warn("Search failed", e)
            null
        }
    }.thenAccept { result ->
        Minecraft.getMinecraft().func_152344_a {
            applyResults(result?.projects ?: emptyList())
        }
    }

    ModularPanel.defaultPanel("vintage-resourcify-browse", 320, 220)
        .child(TextWidget(IKey.str("Resourcify $type browser")).top(6).left(8))
        .child(resultsColumn)
})
