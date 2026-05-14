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

// RGB palettes lifted from GitHub Primer (commit f1c0c83) for the markdown
// content surfaces. We don't expose the full palette - just the bits the
// MarkdownRenderer paints onto.
data class MarkdownTheme(
    val text: Int,
    val heading: Int,
    val muted: Int,
    val link: Int,
    val code: Int,
    val rule: Int,
)

object MarkdownThemes {

    val LIGHT = MarkdownTheme(
        text = 0x1F2328,
        heading = 0x1F2328,
        muted = 0x59636E,
        link = 0x0969DA,
        code = 0xCF222E,
        rule = 0xD1D9E0,
    )

    val DARK = MarkdownTheme(
        text = 0xF0F6FC,
        heading = 0xF0F6FC,
        muted = 0x9198A1,
        link = 0x4493F8,
        code = 0xFF7B72,
        rule = 0x3D444D,
    )

    fun named(name: String): MarkdownTheme = if (name.equals("light", ignoreCase = true)) LIGHT else DARK
}
