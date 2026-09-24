package dev.dediamondpro.resourcify.api;

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.Objects;

import net.minecraft.util.ResourceLocation;

import dev.dediamondpro.resourcify.services.DistributionPolicy;
import dev.dediamondpro.resourcify.services.IService;
import dev.dediamondpro.resourcify.services.ServiceRegistry;
import dev.dediamondpro.resourcify.services.modrinth.ModrinthApiService;
import dev.dediamondpro.resourcify.util.LocalIndex;

/** Client-only entry point for Forge addon mods. See the Platform Addons page in the project wiki. */
public final class ProviderApi {

    public static final int API_VERSION = 1;

    private ProviderApi() {}

    public static boolean isRegistrationEnabled() {
        return DistributionPolicy.allowAddonProviders();
    }

    public static RegistrationResult register(IService provider) {
        return register(provider, null);
    }

    /** Register during client init or postInit. The optional icon should be a square texture. */
    public static RegistrationResult register(IService provider, ResourceLocation icon) {
        return ServiceRegistry.INSTANCE.registerProvider(provider, icon);
    }

    public static RegistrationResult registerModrinth(String platformId, String displayName, String apiBaseUrl,
        String browserBaseUrl) {
        return registerModrinth(platformId, displayName, apiBaseUrl, browserBaseUrl, null);
    }

    /**
     * Register an addon-owned Modrinth v2-compatible platform, including search, projects, and updates.
     * Supply the complete API base (including /v2 or /api/v2) and the website base separately.
     * Registration performs no network requests. Malformed URLs or blank names throw IllegalArgumentException.
     * This programmatic entry point is available in CurseForge builds; platforms.json remains disabled there.
     */
    public static RegistrationResult registerModrinth(String platformId, String displayName, String apiBaseUrl,
        String browserBaseUrl, ResourceLocation icon) {
        if (!isRegistrationEnabled()) return RegistrationResult.DISABLED_BY_DISTRIBUTION;
        Objects.requireNonNull(displayName, "displayName");
        if (displayName.trim()
            .isEmpty()) throw new IllegalArgumentException("Display name must not be blank");
        return register(
            new ModrinthApiService(
                displayName.trim(),
                normalizeBaseUrl(apiBaseUrl),
                normalizeBaseUrl(browserBaseUrl),
                Objects.requireNonNull(platformId, "platformId")),
            icon);
    }

    private static String normalizeBaseUrl(String value) {
        String base = Objects.requireNonNull(value, "baseUrl")
            .trim();
        URI uri = URI.create(base);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
            || uri.getUserInfo() != null
            || uri.getRawQuery() != null
            || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("Expected an HTTPS base URL without credentials, query, or fragment");
        }
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base;
    }

    /** An immutable snapshot including built-in, configured, and addon providers. */
    public static List<IService> getProviders() {
        return ServiceRegistry.INSTANCE.getAllServices();
    }

    /** Returns null if the stable platform ID is not registered. */
    public static IService getProvider(String platformId) {
        return ServiceRegistry.INSTANCE.getServiceById(platformId);
    }

    /** Worker-thread lookup: may read metadata and hash a renamed file. Returns null for untracked files. */
    public static InstalledPack getInstalledPack(File file) {
        LocalIndex.Entry entry = LocalIndex.Companion.forFolder(
            file.getAbsoluteFile()
                .getParentFile())
            .lookupByFile(file);
        if (entry == null) return null;
        return new InstalledPack(
            entry.getPlatform(),
            entry.getProjectId(),
            entry.getRemoteFileId(),
            entry.getSha1(),
            entry.getUpdateTrack(),
            entry.getInstalledReleaseDate());
    }
}
