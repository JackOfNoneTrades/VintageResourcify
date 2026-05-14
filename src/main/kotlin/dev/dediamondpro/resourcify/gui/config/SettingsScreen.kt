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

package dev.dediamondpro.resourcify.gui.config

import com.cleanroommc.modularui.api.drawable.IKey
import com.cleanroommc.modularui.api.widget.IWidget
import com.cleanroommc.modularui.screen.ModularPanel
import com.cleanroommc.modularui.screen.ModularScreen
import com.cleanroommc.modularui.widgets.ButtonWidget
import com.cleanroommc.modularui.widgets.TextWidget
import dev.dediamondpro.resourcify.VintageResourcify
import dev.dediamondpro.resourcify.config.Config
import net.minecraft.util.EnumChatFormatting

private class SimpleButton : ButtonWidget<SimpleButton>()

class SettingsScreen : ModularScreen(VintageResourcify.MODID, { _ ->
    val themeButton = SimpleButton().size(120, 20).top(40).left(10)
    fun refreshLabel() {
        themeButton.overlay(IKey.str("Markdown theme: ${Config.instance.markdownTheme}"))
    }
    refreshLabel()
    themeButton.onMousePressed { btn ->
        if (btn == 0) {
            Config.instance.markdownTheme =
                if (Config.instance.markdownTheme.equals("dark", ignoreCase = true)) "light" else "dark"
            Config.save()
            refreshLabel()
            true
        } else false
    }

    ModularPanel.defaultPanel("vintage-resourcify-settings")
        .full()
        .child(
            TextWidget(IKey.str("VintageResourcify Settings").style(EnumChatFormatting.BOLD).scale(1.5f))
                .top(8).left(10)
        )
        .child(themeButton)
        .child(
            TextWidget(IKey.str("Markdown rendering palette for project descriptions."))
                .top(64).left(10).right(10)
        )
})
