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

import com.cleanroommc.modularui.screen.CustomModularScreen
import com.cleanroommc.modularui.screen.ModularPanel
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext
import com.cleanroommc.modularui.widgets.TextWidget
import dev.dediamondpro.resourcify.VintageResourcify
import dev.dediamondpro.resourcify.services.ProjectType

// v1 placeholder browse screen. The real search/grid/filters land in
// follow-up commits inside this same package.
class BrowseScreen(private val type: ProjectType) : CustomModularScreen(VintageResourcify.MODID) {

    override fun buildUI(context: ModularGuiContext): ModularPanel {
        return ModularPanel.defaultPanel("vintage-resourcify-browse", 256, 192)
            .child(
                TextWidget("Resourcify $type browser (coming soon)").top(8).left(8)
            )
    }
}
