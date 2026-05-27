package dev.dediamondpro.resourcify.config;

import java.io.File;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class ConfiguredPlatforms {

    public static final ConfiguredPlatforms INSTANCE = new ConfiguredPlatforms();

    private ConfiguredPlatforms() {}

    public File getIconsDirectory() {
        return new File(Config.getConfigDirectory(), "icons");
    }

    public List<ConfiguredModrinthPlatform> load() {
        return Collections.emptyList();
    }

    public List<ConfiguredModrinthPlatform> enabledPlatforms() {
        return Collections.emptyList();
    }

    public String displayName(String platformId) {
        return null;
    }

    public File iconFile(String platformId) {
        return null;
    }

    public String platformId(String name) {
        String normalized = name.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "_");
        normalized = normalized.replaceAll("^_+", "")
            .replaceAll("_+$", "");
        return normalized.isEmpty() ? "configured_modrinth" : normalized;
    }

    public String normalizedApiUrl(String apiUrl) {
        String normalized = apiUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    public String browserBaseUrl(String apiUrl) {
        String normalized = normalizedApiUrl(apiUrl);
        try {
            URI uri = new URI(normalized);
            String host = uri.getHost();
            if (host == null) return normalized;
            String browserHost = host.startsWith("api.") ? host.substring(4) : host;
            String scheme = uri.getScheme() == null ? "https" : uri.getScheme();
            return scheme + "://" + browserHost;
        } catch (Throwable ignored) {
            return normalized;
        }
    }
}
