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

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiScreen
import java.io.File

object ShaderGuiHelper {

    private enum class Host {
        IRIS,
        SWANSONG,
    }

    @Volatile private var preferredHost: Host? = null

    fun isPresent(): Boolean = IrisHelper.isPresent() || SwanSongHelper.isPresent()

    fun isShaderPackScreen(screen: GuiScreen): Boolean {
        return hostFor(screen) != null
    }

    fun getShaderScreenParent(screen: GuiScreen): GuiScreen? {
        return when (hostFor(screen)) {
            Host.SWANSONG -> SwanSongHelper.getShaderScreenParent(screen)
            Host.IRIS -> IrisHelper.getShaderScreenParent(screen)
            null -> null
        }
    }

    fun reload(): Boolean {
        return when (selectHost()) {
            Host.SWANSONG -> SwanSongHelper.reload()
            Host.IRIS -> IrisHelper.reload()
            null -> false
        }
    }

    fun enableShaderPack(file: File): Boolean {
        return when (selectHost()) {
            Host.SWANSONG -> SwanSongHelper.enableShaderPack(file)
            Host.IRIS -> IrisHelper.enableShaderPack(file)
            null -> false
        }
    }

    fun isShaderPackEnabled(file: File): Boolean {
        return when (selectHost()) {
            Host.SWANSONG -> SwanSongHelper.isShaderPackEnabled(file)
            Host.IRIS -> IrisHelper.isShaderPackEnabled(file)
            null -> false
        }
    }

    fun getShaderpacksFolder(screen: GuiScreen? = null): File {
        return when (screen?.let { hostFor(it) } ?: selectHost()) {
            Host.SWANSONG -> SwanSongHelper.getShaderpacksFolder()
            Host.IRIS -> IrisHelper.getShaderpacksFolder()
            null -> File(Minecraft.getMinecraft().mcDataDir, "shaderpacks")
        }
    }

    fun openShaderPackScreen(parent: GuiScreen?): Boolean {
        return when (selectHost()) {
            Host.SWANSONG -> SwanSongHelper.openShaderPackScreen(parent)
            Host.IRIS -> IrisHelper.openShaderPackScreen(parent)
            null -> false
        }
    }

    private fun hostFor(screen: GuiScreen): Host? {
        if (SwanSongHelper.isShaderPackScreen(screen)) {
            preferredHost = Host.SWANSONG
            return Host.SWANSONG
        }
        if (IrisHelper.isShaderPackScreen(screen)) {
            preferredHost = Host.IRIS
            return Host.IRIS
        }
        return null
    }

    private fun selectHost(): Host? {
        preferredHost?.let { preferred ->
            if (isHostPresent(preferred)) return preferred
        }
        return when {
            SwanSongHelper.isPresent() -> Host.SWANSONG
            IrisHelper.isPresent() -> Host.IRIS
            else -> null
        }
    }

    private fun isHostPresent(host: Host): Boolean {
        return when (host) {
            Host.SWANSONG -> SwanSongHelper.isPresent()
            Host.IRIS -> IrisHelper.isPresent()
        }
    }
}
