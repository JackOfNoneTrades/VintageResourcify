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

package dev.dediamondpro.resourcify.services

import dev.dediamondpro.resourcify.config.Config


enum class ProjectType(
    val displayName: String,
    val plusX: (Int) -> Int = { screenWidth: Int ->
        (
                screenWidth / 2
                + 184
        )
    },
    val plusY: (Int) -> Int = { 10 },
    val updateX: (Int) -> Int = { plusX.invoke(it) - 28 },
    val updateY: (Int) -> Int = plusY,
    val hasUpdateButton: Boolean = true,
    val shouldExtract: Boolean = false,
) {
    RESOURCE_PACK("resourcify.type.resource_packs"),
    // 1.8.9 only
    AYCY_RESOURCE_PACK("resourcify.type.resource_packs", plusX = { it - 30 }),
    DATA_PACK("resourcify.type.data_packs", hasUpdateButton = false),
    IRIS_SHADER("resourcify.type.shaders", { it / 2 + 134 }, { 6 }),
    OPTIFINE_SHADER("resourcify.type.shaders", plusX = { it - 30 }),
    WORLD("resourcify.type.world", { it / 2 + 134 }, {
        10
    }, hasUpdateButton = false, shouldExtract = true),
    // Used for when there is a link to a project but we don't know what type it is
    UNKNOWN("");

    fun isEnabled(): Boolean {
        return when (this) {
            RESOURCE_PACK, AYCY_RESOURCE_PACK -> Config.instance.resourcePacksEnabled
            DATA_PACK -> Config.instance.dataPacksEnabled
            IRIS_SHADER, OPTIFINE_SHADER -> Config.instance.shaderPacksEnabled
            WORLD -> Config.instance.worldsEnabled
            UNKNOWN -> false
        }
    }

}