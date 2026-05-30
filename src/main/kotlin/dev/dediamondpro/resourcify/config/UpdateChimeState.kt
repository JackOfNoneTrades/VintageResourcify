package dev.dediamondpro.resourcify.config

import dev.dediamondpro.resourcify.VintageResourcify
import java.io.File

object UpdateChimeState {
    const val RESOURCE_PACKS = "resourcePacks"
    const val SHADER_PACKS = "shaderPacks"

    private val stateFile: File
        get() = File(Config.getConfigDirectory(), ".updates")

    @Synchronized
    fun shouldPlay(group: String, hash: String?): Boolean {
        val nextHash = hash?.takeIf { it.isNotBlank() } ?: ""
        val state = load()
        val previousHash = state[group].orEmpty()
        if (previousHash == nextHash) {
            return false
        }

        if (nextHash.isBlank()) {
            state.remove(group)
        } else {
            state[group] = nextHash
        }
        save(state)
        return nextHash.isNotBlank()
    }

    private fun load(): MutableMap<String, String> {
        val file = stateFile
        if (!file.exists()) return mutableMapOf()

        return try {
            file.readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .mapNotNull { line ->
                    val separator = line.indexOf('=')
                    if (separator <= 0) null else line.substring(0, separator) to line.substring(separator + 1)
                }
                .toMap()
                .toMutableMap()
        } catch (e: Throwable) {
            VintageResourcify.LOG.warn("Could not read update chime state from {}", file, e)
            mutableMapOf()
        }
    }

    private fun save(state: Map<String, String>) {
        val file = stateFile
        try {
            file.parentFile?.mkdirs()
            file.writeText(
                state.entries
                    .sortedBy { it.key }
                    .joinToString(separator = "\n", postfix = "\n") { "${it.key}=${it.value}" }
            )
        } catch (e: Throwable) {
            VintageResourcify.LOG.warn("Could not write update chime state to {}", file, e)
        }
    }
}
