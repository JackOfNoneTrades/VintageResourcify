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
import net.minecraft.client.gui.GuiConfirmOpenLink
import net.minecraft.client.gui.GuiScreen
import java.net.URI

/**
 * Cross-environment URL opener. Java 8's `java.awt.Desktop.browse(...)` is
 * the canonical entry point, but on macOS + LWJGL3 (via lwjgl3ify) AWT can
 * fail or hang because of display-thread / Cocoa restrictions. We follow
 * Catalogue-Vintage's fallback chain - Desktop, lwjgl3ify's redirected
 * Desktop, lwjgl3 Sys shim, finally legacy LWJGL2 Sys - so one of them
 * succeeds regardless of the runtime stack.
 */
object UrlOpener {

    // GuiChat's protocol allowlist is private in 1.7.10; we mirror its
    // contents rather than mixin-accessor for one field. http/https covers
    // all Modrinth + CurseForge links we encounter.
    private val ALLOWED_SCHEMES = setOf("http", "https")

    fun openLinkPrompted(url: String?, returnTo: GuiScreen?) {
        if (url == null) return
        val uri = try {
            URI(url)
        } catch (e: Exception) {
            VintageResourcify.LOG.warn("Refusing to open malformed URL: {}", url, e)
            return
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme == null || scheme !in ALLOWED_SCHEMES) {
            VintageResourcify.LOG.warn("Refusing to open URL with unsupported scheme: {}", url)
            return
        }
        val mc = Minecraft.getMinecraft()
        VintageResourcify.LOG.info(
            "UrlOpener.openLinkPrompted url={} chatLinksPrompt={} returnTo={}",
            url, mc.gameSettings.chatLinksPrompt, returnTo?.javaClass?.simpleName,
        )
        if (mc.gameSettings.chatLinksPrompt) {
            mc.func_152344_a {
                VintageResourcify.LOG.info("UrlOpener showing confirm for {}", url)
                mc.displayGuiScreen(
                    GuiConfirmOpenLink(
                        { result, callbackId ->
                            VintageResourcify.LOG.info(
                                "UrlOpener confirm result={} callbackId={} url={}",
                                result, callbackId, url,
                            )
                            if (result) openUri(uri)
                            mc.displayGuiScreen(returnTo)
                        },
                        url, 0, false,
                    )
                )
            }
        } else {
            openUri(uri)
        }
    }

    private fun openUri(uri: URI) {
        // 1. Standard JDK Desktop.
        try {
            java.awt.Desktop.getDesktop().browse(uri)
            return
        } catch (t: Throwable) {
            VintageResourcify.LOG.warn("Desktop.browse failed, trying lwjgl3ify shim: {}", uri, t)
        }
        // 2. lwjgl3ify's Cocoa-safe Desktop redirect.
        try {
            val cls = Class.forName("me.eigenraven.lwjgl3ify.redirects.Desktop")
            val instance = cls.getMethod("getDesktop").invoke(null)
            cls.getMethod("browse", URI::class.java).invoke(instance, uri)
            return
        } catch (t: Throwable) {
            VintageResourcify.LOG.warn("lwjgl3ify Desktop redirect failed: {}", uri, t)
        }
        // 3. LWJGL3 Sys shim.
        try {
            val cls = Class.forName("org.lwjglx.Sys")
            val ok = cls.getMethod("openURL", String::class.java).invoke(null, uri.toString())
            if (ok is Boolean && ok) return
        } catch (t: Throwable) {
            VintageResourcify.LOG.warn("org.lwjglx.Sys.openURL failed: {}", uri, t)
        }
        // 4. Legacy LWJGL2 Sys.
        try {
            val cls = Class.forName("org.lwjgl.Sys")
            cls.getMethod("openURL", String::class.java).invoke(null, uri.toString())
            return
        } catch (t: Throwable) {
            VintageResourcify.LOG.error("All URL open methods failed: {}", uri, t)
        }
    }
}
