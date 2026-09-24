package dev.dediamondpro.resourcify.api;

import java.net.URL;
import java.util.Objects;

/** Immutable result of resolving a version for download. Browser targets never install files. */
public final class DownloadTarget {

    private final URL url;
    private final DownloadChecksum checksum;
    private final boolean browser;

    private DownloadTarget(URL url, DownloadChecksum checksum, boolean browser) {
        this.url = Objects.requireNonNull(url, "url");
        if (!("https".equalsIgnoreCase(url.getProtocol()) || (browser && "http".equalsIgnoreCase(url.getProtocol())))
            || url.getHost()
                .isEmpty()
            || url.getUserInfo() != null) {
            throw new IllegalArgumentException("Expected an HTTPS download or HTTP(S) browser URL");
        }
        this.checksum = checksum;
        this.browser = browser;
    }

    /** A null checksum means the provider does not publish a supported digest. */
    public static DownloadTarget direct(URL url, DownloadChecksum checksum) {
        return new DownloadTarget(url, checksum, false);
    }

    public static DownloadTarget browser(URL url) {
        return new DownloadTarget(url, null, true);
    }

    public URL getUrl() {
        return url;
    }

    public DownloadChecksum getChecksum() {
        return checksum;
    }

    public boolean isBrowser() {
        return browser;
    }
}
