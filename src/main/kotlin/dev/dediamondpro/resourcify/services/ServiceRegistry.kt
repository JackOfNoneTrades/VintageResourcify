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

import dev.dediamondpro.resourcify.api.RegistrationResult
import net.minecraft.util.ResourceLocation
import java.util.Collections
import dev.dediamondpro.resourcify.VintageResourcify
import dev.dediamondpro.resourcify.config.Config
import dev.dediamondpro.resourcify.config.ConfiguredPlatforms
import dev.dediamondpro.resourcify.services.curseforge.CurseForgeService
import dev.dediamondpro.resourcify.services.modrinth.ModrinthApiService
import dev.dediamondpro.resourcify.services.modrinth.ModrinthService

object ServiceRegistry {
    private val services = mutableListOf<IService>()
    private val addonIds = mutableSetOf<String>()
    private val icons = mutableMapOf<String, ResourceLocation>()
    private val validId = Regex("[a-z0-9][a-z0-9_-]*")
    private val reservedIds = setOf("modrinth", "curseforge", "curse")
    private val configuredServices = mutableListOf<IService>()

    init {
        services.add(ModrinthService)
        services.add(CurseForgeService)
        loadConfiguredServices()
    }

    @Synchronized
    fun getAllServices(): List<IService> {
        return Collections.unmodifiableList(ArrayList(services))
    }

    @Synchronized
    fun getServiceById(platformId: String): IService? = services.firstOrNull { it.getPlatformId() == platformId }

    @Synchronized
    fun getIcon(platformId: String): ResourceLocation? = icons[platformId]

    @Synchronized
    fun isAddonProvider(platformId: String): Boolean = platformId in addonIds

    fun getServices(projectType: ProjectType): List<IService> {
        return getAllServices().filter { it.isProjectTypeSupported(projectType) }
    }

    fun getService(name: String, projectType: ProjectType): IService? {
        return getServices(projectType).firstOrNull { it.getName() == name }
    }

    fun getDefaultService(projectType: ProjectType): IService {
        return getService(Config.instance.defaultService, projectType) ?: getServices(projectType).first()
    }

    /** Legacy entry point retained for binary compatibility; new addons should use ProviderApi. */
    fun registerService(service: IService) {
        val result = registerProvider(service, null)
        if (result != RegistrationResult.REGISTERED) {
            VintageResourcify.LOG.warn("Provider registration rejected: {}", result)
        }
    }

    @Synchronized
    fun registerProvider(service: IService, icon: ResourceLocation?): RegistrationResult {
        // Check before invoking addon methods. Built-ins are installed privately during initialization.
        if (!DistributionPolicy.allowAddonProviders()) return RegistrationResult.DISABLED_BY_DISTRIBUTION
        return register(service, icon, addon = true)
    }

    private fun register(service: IService, icon: ResourceLocation?, addon: Boolean): RegistrationResult {
        val id = service.getPlatformId()
        if (!validId.matches(id)) return RegistrationResult.INVALID_ID
        if (id in reservedIds) return RegistrationResult.RESERVED_ID
        if (services.any { it.getPlatformId() == id }) return RegistrationResult.DUPLICATE_ID
        services.add(service)
        if (addon) addonIds.add(id)
        if (icon != null) icons[id] = icon
        return RegistrationResult.REGISTERED
    }

    @Synchronized
    fun loadConfiguredServices() {
        services.removeAll(configuredServices)
        configuredServices.clear()
        if (!DistributionPolicy.allowConfiguredPlatforms()) {
            VintageResourcify.LOG.info("Configured platforms are disabled for this distribution.")
            return
        }
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
            val result = register(service, null, addon = false)
            if (result == RegistrationResult.REGISTERED) {
                configuredServices.add(service)
            } else {
                VintageResourcify.LOG.warn("Skipping configured platform {}: {}", platform.name, result)
            }
        }
        VintageResourcify.LOG.info(
            "Loaded {} configured platform(s) from platforms.json",
            platforms.count { it.enabled != false && it.name.isNotBlank() && it.apiUrl.isNotBlank() },
        )
    }
}
