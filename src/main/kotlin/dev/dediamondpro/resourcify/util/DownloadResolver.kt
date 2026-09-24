package dev.dediamondpro.resourcify.util

import dev.dediamondpro.resourcify.api.AsyncDownloadVersion
import dev.dediamondpro.resourcify.api.DownloadChecksum
import dev.dediamondpro.resourcify.api.DownloadTarget
import dev.dediamondpro.resourcify.services.DistributionPolicy
import dev.dediamondpro.resourcify.services.IVersion
import java.io.File
import java.util.concurrent.CompletableFuture

/** All provider-controlled download resolution runs off the client thread and after the policy check. */
object DownloadResolver {
    fun resolve(platformId: String, version: IVersion): CompletableFuture<DownloadTarget> {
        if (!DistributionPolicy.canDownloadFrom(platformId)) {
            return CompletableFuture<DownloadTarget>().apply {
                completeExceptionally(IllegalStateException(DistributionPolicy.downloadBlockedMessage(platformId)))
            }
        }
        return supplyAsync {
            if (version is AsyncDownloadVersion) {
                version.resolveDownload()
            } else {
                val url = version.getDownloadUrl() ?: error("No download URL for this version")
                val checksum = version.getSha1().takeIf { it.isNotBlank() }
                    ?.let { DownloadChecksum(DownloadChecksum.Algorithm.SHA1, it) }
                CompletableFuture.completedFuture(DownloadTarget.direct(url, checksum))
            }
        }.thenCompose { it }.thenApply { requireNotNull(it) { "Provider returned no download target" } }
    }

    /** Provider filenames must identify a single file directly inside the pack folder. */
    fun targetFile(folder: File, version: IVersion): File {
        val name = version.getFileName()
        require(name.isNotBlank() && name != "." && name != ".." &&
            '/' !in name && '\\' !in name && ':' !in name) { "Invalid download filename" }
        val file = File(folder, name)
        require(file.canonicalFile.parentFile == folder.canonicalFile) { "Download escapes pack folder" }
        return file
    }
}
