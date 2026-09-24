package dev.dediamondpro.resourcify.api;

import java.util.concurrent.CompletableFuture;

import dev.dediamondpro.resourcify.services.IVersion;

/**
 * Optional version capability for expiring links, non-SHA-1 checksums, and browser downloads.
 * Called on a worker after distribution checks, once per download attempt. Do not touch Minecraft UI here.
 * Complete exceptionally on failure. Cancellation may discard the result without stopping provider I/O.
 */
public interface AsyncDownloadVersion extends IVersion {

    CompletableFuture<DownloadTarget> resolveDownload();
}
