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

import dev.dediamondpro.resourcify.VintageResourcify
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiScreen
import java.io.File
import java.nio.file.Path

/**
 * Soft-dependency bridge to SwanSong's shader pack manager and GUI.
 */
object SwanSongHelper {

    private fun findClass(name: String): Class<*>? {
        return try {
            Class.forName(name, false, SwanSongHelper::class.java.classLoader)
        } catch (_: Throwable) {
            null
        }
    }

    private val shaderScreenClass: Class<out GuiScreen>? by lazy {
        try {
            @Suppress("UNCHECKED_CAST")
            findClass("com.ventooth.swansong.gui.GuiShaders") as? Class<out GuiScreen>
        } catch (_: Throwable) {
            null
        }
    }

    private val shaderPackManagerClass: Class<*>? by lazy {
        findClass("com.ventooth.swansong.resources.ShaderPackManager")
    }

    fun isPresent(): Boolean = shaderPackManagerClass != null

    fun isShaderPackScreen(screen: GuiScreen): Boolean {
        val cls = shaderScreenClass ?: return false
        return cls.isInstance(screen)
    }

    fun reload(): Boolean {
        val cls = shaderPackManagerClass ?: return false
        return try {
            cls.getDeclaredMethod("refreshShaderPackNames").invoke(null)
            val names = cls.getDeclaredMethod("getShaderPackNames").invoke(null) as? List<*> ?: emptyList<String>()
            val current = cls.getDeclaredMethod("getCurrentShaderPackName").invoke(null) as? String
            if (current != null && !names.contains(current)) {
                val disabled = cls.getDeclaredField("DISABLED_SHADER_PACK_NAME").get(null) as? String ?: "(disabled)"
                cls.getDeclaredMethod("setShaderPackByName", String::class.java).invoke(null, disabled)
            }
            true
        } catch (e: Throwable) {
            VintageResourcify.LOG.warn("SwanSong shader pack refresh failed", e)
            false
        }
    }

    fun enableShaderPack(file: File): Boolean {
        val cls = shaderPackManagerClass ?: return false
        return try {
            cls.getDeclaredMethod("refreshShaderPackNames").invoke(null)
            cls.getDeclaredMethod("setShaderPackByName", String::class.java).invoke(null, file.name)
            true
        } catch (e: Throwable) {
            VintageResourcify.LOG.warn("Failed to enable SwanSong shader pack {}", file.name, e)
            false
        }
    }

    fun isShaderPackEnabled(file: File): Boolean {
        val cls = shaderPackManagerClass ?: return false
        return try {
            val current = cls.getDeclaredMethod("getCurrentShaderPackName").invoke(null) as? String
            current == file.name
        } catch (e: Throwable) {
            VintageResourcify.LOG.warn("Failed to check SwanSong shader pack state for {}", file.name, e)
            false
        }
    }

    fun getShaderScreenParent(screen: GuiScreen): GuiScreen? {
        val cls = shaderScreenClass ?: return null
        if (!cls.isInstance(screen)) return null
        return try {
            val f = cls.getDeclaredField("parentGui")
            f.isAccessible = true
            f.get(screen) as? GuiScreen
        } catch (e: Throwable) {
            VintageResourcify.LOG.warn("Could not read GuiShaders.parentGui reflectively", e)
            null
        }
    }

    fun getShaderpacksFolder(): File {
        shaderPackManagerClass?.let { cls ->
            try {
                val result = cls.getDeclaredMethod("resolvePath", String::class.java).invoke(null, "")
                if (result is Path) return result.toFile()
            } catch (e: Throwable) {
                VintageResourcify.LOG.warn("SwanSong ShaderPackManager.resolvePath reflection failed", e)
            }
        }
        return File(Minecraft.getMinecraft().mcDataDir, "shaderpacks")
    }

    fun openShaderPackScreen(parent: GuiScreen?): Boolean {
        val cls = shaderScreenClass ?: return false
        return try {
            val ctor = cls.getDeclaredConstructor(GuiScreen::class.java)
            val screen = ctor.newInstance(parent) as GuiScreen
            Minecraft.getMinecraft().displayGuiScreen(screen)
            true
        } catch (e: Throwable) {
            VintageResourcify.LOG.warn("Failed to open SwanSong GuiShaders reflectively", e)
            false
        }
    }
}
