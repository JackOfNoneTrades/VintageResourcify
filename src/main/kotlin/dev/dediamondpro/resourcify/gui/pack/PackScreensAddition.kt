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

import dev.dediamondpro.resourcify.VintageResourcify
import dev.dediamondpro.resourcify.services.ProjectType
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Gui
import java.io.File

// Placeholder while task 7 ports the browser screen onto MUI2. The mixin
// invokes onRender every frame and onMouseClick on every click; here we just
// draw a marker box at top-right and log clicks inside its bounds, so that
// task 5 can be verified at runtime without depending on the MUI2 rewrite.
object PackScreensAddition {

    private const val BUTTON_SIZE = 20

    fun onRender(type: ProjectType) {
        val (x, y) = buttonOrigin() ?: return
        Gui.drawRect(x, y, x + BUTTON_SIZE, y + BUTTON_SIZE, 0xFFE91E63.toInt())
    }

    fun onMouseClick(mouseX: Int, mouseY: Int, button: Int, type: ProjectType, folder: File) {
        if (button != 0) return
        val (x, y) = buttonOrigin() ?: return
        if (mouseX !in x..(x + BUTTON_SIZE)) return
        if (mouseY !in y..(y + BUTTON_SIZE)) return
        VintageResourcify.LOG.info(
            "PackScreensAddition click: type={} folder={} (MUI2 browser pending in task 7)",
            type,
            folder,
        )
    }

    private fun buttonOrigin(): Pair<Int, Int>? {
        val mc = Minecraft.getMinecraft()
        val screen = mc.currentScreen ?: return null
        val width = screen.width
        return (width - BUTTON_SIZE - 4) to 4
    }
}
