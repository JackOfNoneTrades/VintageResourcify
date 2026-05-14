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

package dev.dediamondpro.resourcify.platform


// Methods that touch the resource pack repository (getSelectedResourcePacks,
// reloadResources, closeResourcePack, enableResourcePack, saveSettings) live
// in upstream's Platform.kt and depend on AbstractResourcePackAccessor +
// UMinecraft. They get added back in the MUI2 GUI rewrite (task 7) using the
// 1.7.10-native ResourcePackRepository APIs and the FentLib-style mixin
// accessor for AbstractResourcePack.
object Platform {
    // The mod targets exactly one MC version. 1.7.10's ForgeVersion has no
    // mcVersion field, and looking it up at runtime would just yield the same
    // string anyway.
    fun getMcVersion(): String = "1.7.10"
}
