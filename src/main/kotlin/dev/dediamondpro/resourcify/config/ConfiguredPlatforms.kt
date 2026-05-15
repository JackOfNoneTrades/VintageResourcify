package dev.dediamondpro.resourcify.config

import com.google.gson.annotations.SerializedName
import dev.dediamondpro.resourcify.VintageResourcify
import dev.dediamondpro.resourcify.util.fromJson
import dev.dediamondpro.resourcify.util.toJson
import java.io.File
import java.net.URI
import java.util.Locale

data class ConfiguredModrinthPlatform(
    val name: String = "",
    @SerializedName("api_url") val apiUrl: String = "",
    @SerializedName("icon_filename") val iconFilename: String? = null,
    val enabled: Boolean? = true,
)

object ConfiguredPlatforms {
    private val defaultPlatforms = listOf(
        ConfiguredModrinthPlatform(
            name = "67minecraft",
            apiUrl = "https://67.fentanylsolutions.org/api/v2",
            iconFilename = "67.png",
            enabled = false,
        )
    )

    private val platformsFile: File
        get() = File(Config.getConfigDirectory(), "platforms.json")
    val iconsDirectory: File
        get() = File(Config.getConfigDirectory(), "icons")

    @Volatile
    private var platformsById: Map<String, ConfiguredModrinthPlatform> = emptyMap()

    fun load(): List<ConfiguredModrinthPlatform> {
        ensureFiles()
        val parsed = try {
            platformsFile.readText().fromJson<List<ConfiguredModrinthPlatform>>() ?: emptyList()
        } catch (t: Throwable) {
            VintageResourcify.LOG.warn("Could not read configured platforms from {}", platformsFile, t)
            emptyList()
        }
        platformsById = parsed
            .filter { it.name.isNotBlank() && it.apiUrl.isNotBlank() }
            .associateBy { platformId(it.name) }
        return parsed
    }

    fun enabledPlatforms(): List<ConfiguredModrinthPlatform> {
        return platformsById.values.filter { it.enabled != false }
    }

    fun displayName(platformId: String): String? {
        return platformsById[platformId.lowercase(Locale.ROOT)]?.name
    }

    fun iconFile(platformId: String): File? {
        val filename = platformsById[platformId.lowercase(Locale.ROOT)]
            ?.iconFilename
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val file = File(iconsDirectory, filename)
        return try {
            val iconsRoot = iconsDirectory.canonicalFile
            val icon = file.canonicalFile
            if (icon.path.startsWith(iconsRoot.path + File.separator)) icon else null
        } catch (_: Throwable) {
            null
        }
    }

    fun platformId(name: String): String {
        return name.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "configured_modrinth" }
    }

    fun normalizedApiUrl(apiUrl: String): String {
        return apiUrl.trim().trimEnd('/')
    }

    fun browserBaseUrl(apiUrl: String): String {
        val normalized = normalizedApiUrl(apiUrl)
        return try {
            val uri = URI(normalized)
            val host = uri.host ?: return normalized
            val browserHost = host.removePrefix("api.")
            val scheme = uri.scheme ?: "https"
            "$scheme://$browserHost"
        } catch (_: Throwable) {
            normalized
        }
    }

    private fun ensureFiles() {
        Config.getConfigDirectory()
        if (!iconsDirectory.exists()) iconsDirectory.mkdirs()
        copyDefaultIcon("67.png")
        if (!platformsFile.exists()) {
            platformsFile.writeText(defaultPlatforms.toJson())
        }
    }

    private fun copyDefaultIcon(filename: String) {
        val target = File(iconsDirectory, filename)
        if (target.exists()) return
        try {
            ConfiguredPlatforms::class.java
                .getResourceAsStream("/assets/${VintageResourcify.MODID}/platform/$filename")
                ?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
        } catch (t: Throwable) {
            VintageResourcify.LOG.warn("Could not copy default platform icon {}", filename, t)
        }
    }
}
