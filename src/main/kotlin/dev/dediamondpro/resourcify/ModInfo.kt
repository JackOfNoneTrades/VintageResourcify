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

package dev.dediamondpro.resourcify

import dev.dediamondpro.resourcify.Tags

// Upstream populates these via blossom token replacement at compile time
// (@NAME@/@ID@/@VER@). We resolve them at runtime from the Tags class that
// the GTNH buildscript already generates from gradle.properties, which keeps
// the rest of the codebase using ModInfo.* unchanged from upstream.
object ModInfo {
    const val NAME: String = "VintageResourcify"
    const val ID: String = VintageResourcify.MODID
    val VERSION: String = Tags.VERSION
}
