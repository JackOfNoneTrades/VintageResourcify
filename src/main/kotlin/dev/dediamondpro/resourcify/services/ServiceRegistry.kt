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

import dev.dediamondpro.resourcify.VintageResourcify
import dev.dediamondpro.resourcify.config.Config
import dev.dediamondpro.resourcify.config.ConfiguredPlatforms
import dev.dediamondpro.resourcify.services.curseforge.CurseForgeService
import dev.dediamondpro.resourcify.services.modrinth.ModrinthApiService
import dev.dediamondpro.resourcify.services.modrinth.ModrinthService

object ServiceRegistry {
    private val services = mutableListOf<IService>()
    private val configuredServices = mutableListOf<IService>()

    init {
        registerService(ModrinthService)
        registerService(CurseForgeService)
        loadConfiguredServices()
    }

    fun getAllServices(): List<IService> {
        return services
    }

    fun getServices(projectType: ProjectType): List<IService> {
        return services.filter { it.isProjectTypeSupported(projectType) }
    }

    fun getService(name: String, projectType: ProjectType): IService? {
        return getServices(projectType).firstOrNull { it.getName() == name }
    }

    fun getDefaultService(projectType: ProjectType): IService {
        return getService(Config.instance.defaultService, projectType) ?: getServices(projectType).first()
    }

    fun registerService(service: IService) {
        services.add(service)
    }

    fun loadConfiguredServices() {
        services.removeAll(configuredServices)
        configuredServices.clear()
        val platforms = ConfiguredPlatforms.load()
        for (platform in ConfiguredPlatforms.enabledPlatforms()) {
            val platformId = ConfiguredPlatforms.platformId(platform.name)
            if (services.any { it.getPlatformId() == platformId }) {
                VintageResourcify.LOG.warn("Skipping configured platform {} because platform id {} is already registered", platform.name, platformId)
                continue
            }
            val service = ModrinthApiService(
                platform.name,
                ConfiguredPlatforms.normalizedApiUrl(platform.apiUrl),
                ConfiguredPlatforms.browserBaseUrl(platform.apiUrl),
                platformId,
            )
            configuredServices.add(service)
            registerService(service)
        }
        VintageResourcify.LOG.info(
            "Loaded {} configured platform(s) from platforms.json",
            platforms.count { it.enabled != false && it.name.isNotBlank() && it.apiUrl.isNotBlank() },
        )
    }
}
