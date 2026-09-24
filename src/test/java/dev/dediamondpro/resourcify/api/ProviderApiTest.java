package dev.dediamondpro.resourcify.api;

import static org.junit.Assert.*;

import java.io.File;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import dev.dediamondpro.resourcify.config.Config;
import dev.dediamondpro.resourcify.services.DistributionPolicy;
import dev.dediamondpro.resourcify.services.IService;
import dev.dediamondpro.resourcify.services.IVersion;
import dev.dediamondpro.resourcify.services.ServiceRegistry;
import dev.dediamondpro.resourcify.util.DownloadResolver;
import dev.dediamondpro.resourcify.util.LocalIndex;

public class ProviderApiTest {

    private static final boolean RESTRICTED = Boolean.getBoolean("resourcify.test.curseforge");

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    static IService provider(String id) {
        return (IService) Proxy.newProxyInstance(
            IService.class.getClassLoader(),
            new Class<?>[] { IService.class },
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getPlatformId":
                        return id;
                    case "getName":
                        return "Example " + id;
                    case "equals":
                        return proxy == args[0];
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    default:
                        throw new AssertionError("Unexpected provider call: " + method.getName());
                }
            });
    }

    @Test
    public void distributionAndRegistration() {
        assertTrue(ProviderApi.isRegistrationEnabled());
        assertNotNull(ProviderApi.getProvider("modrinth"));
        assertNotNull(ProviderApi.getProvider("curseforge"));
        IService provider = provider("example_registration");
        assertEquals(RegistrationResult.REGISTERED, ProviderApi.register(provider));
        assertSame(provider, ProviderApi.getProvider("example_registration"));
        assertTrue(DistributionPolicy.canDownloadFrom("example_registration"));
        assertTrue(ServiceRegistry.INSTANCE.isAddonProvider("example_registration"));
        assertEquals(RegistrationResult.DUPLICATE_ID, ProviderApi.register(provider("example_registration")));
        assertEquals(RegistrationResult.RESERVED_ID, ProviderApi.register(provider("modrinth")));
        assertEquals(RegistrationResult.RESERVED_ID, ProviderApi.register(provider("curseforge")));
        assertEquals(RegistrationResult.INVALID_ID, ProviderApi.register(provider("Bad ID")));
        ServiceRegistry.INSTANCE.loadConfiguredServices();
        assertSame(provider, ProviderApi.getProvider("example_registration"));
    }

    @Test
    public void snapshotsCannotModifyRegistry() {
        List<IService> snapshot = ProviderApi.getProviders();
        int size = snapshot.size();
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        ProviderApi.register(provider("snapshot_example"));
        assertEquals(size, snapshot.size());
        assertEquals(
            size + 1,
            ProviderApi.getProviders()
                .size());
    }

    @Test
    public void legacyRegistrationObeysPolicy() {
        ServiceRegistry.INSTANCE.registerService(provider("legacy_example"));
        assertNotNull(ProviderApi.getProvider("legacy_example"));
        assertTrue(DistributionPolicy.canDownloadFrom("legacy_example"));
    }

    @Test
    public void configuredPlatformsRemainDisabledInCurseForge() throws Exception {
        assertEquals(!RESTRICTED, DistributionPolicy.allowConfiguredPlatforms());
        assertEquals(!RESTRICTED, DistributionPolicy.canDownloadFrom("unregistered_platform"));
        File config = new File(Config.getConfigDirectory(), "platforms.json");
        byte[] previous = config.exists() ? Files.readAllBytes(config.toPath()) : null;
        try {
            Files.write(
                config.toPath(),
                ("[{\"name\":\"configured_test\"," + "\"api_url\":\"https://example.org/api/v2\",\"enabled\":true}]")
                    .getBytes(StandardCharsets.UTF_8));
            ServiceRegistry.INSTANCE.loadConfiguredServices();
            assertEquals(!RESTRICTED, ProviderApi.getProvider("configured_test") != null);
            assertFalse(ServiceRegistry.INSTANCE.isAddonProvider("configured_test"));
        } finally {
            if (previous == null) Files.deleteIfExists(config.toPath());
            else Files.write(config.toPath(), previous);
            ServiceRegistry.INSTANCE.loadConfiguredServices();
        }
    }

    @Test
    public void unregisteredPlatformDoesNotResolveInCurseForge() throws Exception {
        if (!RESTRICTED) return;
        AtomicInteger calls = new AtomicInteger();
        IVersion version = (IVersion) Proxy.newProxyInstance(
            IVersion.class.getClassLoader(),
            new Class<?>[] { AsyncDownloadVersion.class },
            (proxy, method, args) -> {
                calls.incrementAndGet();
                throw new AssertionError("Unregistered platform resolved a download");
            });
        assertThrows(
            Exception.class,
            () -> DownloadResolver.INSTANCE.resolve("unregistered_platform", version)
                .get(5, TimeUnit.SECONDS));
        assertEquals(0, calls.get());
    }

    @Test
    public void resolutionRunsOnWorkerAndHonorsPolicy() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Thread caller = Thread.currentThread();
        DownloadTarget target = DownloadTarget.browser(new URL("https://example.org/download"));
        IVersion version = (IVersion) Proxy.newProxyInstance(
            IVersion.class.getClassLoader(),
            new Class<?>[] { AsyncDownloadVersion.class },
            (proxy, method, args) -> {
                assertEquals("resolveDownload", method.getName());
                assertNotSame(caller, Thread.currentThread());
                calls.incrementAndGet();
                return CompletableFuture.completedFuture(target);
            });
        assertEquals(RegistrationResult.REGISTERED, ProviderApi.register(provider("resolver_example")));
        CompletableFuture<DownloadTarget> future = DownloadResolver.INSTANCE.resolve("resolver_example", version);
        assertSame(target, future.get(5, TimeUnit.SECONDS));
        assertEquals(1, calls.get());
    }

    @Test
    public void legacyVersionsStillResolveSha1() throws Exception {
        IVersion version = (IVersion) Proxy.newProxyInstance(
            IVersion.class.getClassLoader(),
            new Class<?>[] { IVersion.class },
            (proxy, method, args) -> {
                if (method.getName()
                    .equals("getDownloadUrl")) return new URL("https://example.org/pack.zip");
                if (method.getName()
                    .equals("getSha1")) return "a9993e364706816aba3e25717850c26c9cd0d89d";
                throw new AssertionError(method.getName());
            });
        DownloadTarget target = DownloadResolver.INSTANCE.resolve("modrinth", version)
            .get(5, TimeUnit.SECONDS);
        assertFalse(target.isBrowser());
        assertEquals(
            DownloadChecksum.Algorithm.SHA1,
            target.getChecksum()
                .getAlgorithm());
    }

    @Test
    public void cancelledResolutionDiscardsLateTarget() throws Exception {
        assertEquals(RegistrationResult.REGISTERED, ProviderApi.register(provider("cancel_example")));
        CompletableFuture<DownloadTarget> remote = new CompletableFuture<>();
        CompletableFuture<Void> started = new CompletableFuture<>();
        IVersion version = (IVersion) Proxy.newProxyInstance(
            IVersion.class.getClassLoader(),
            new Class<?>[] { AsyncDownloadVersion.class },
            (proxy, method, args) -> {
                started.complete(null);
                return remote;
            });
        CompletableFuture<DownloadTarget> resolution = DownloadResolver.INSTANCE.resolve("cancel_example", version);
        started.get(5, TimeUnit.SECONDS);
        assertTrue(resolution.cancel(false));
        remote.complete(DownloadTarget.direct(new URL("https://example.org/late.zip"), null));
        assertTrue(resolution.isCancelled());
    }

    @Test
    public void provenanceSurvivesRenameAndMissingAddon() throws Exception {
        File folder = temporary.newFolder();
        File file = new File(folder, "pack.zip");
        Files.write(file.toPath(), "abc".getBytes(StandardCharsets.UTF_8));
        IVersion version = (IVersion) Proxy.newProxyInstance(
            IVersion.class.getClassLoader(),
            new Class<?>[] { IdentifiedVersion.class },
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getFileId":
                        return "file-42";
                    case "getProjectId":
                        return "project-12";
                    case "getMinecraftVersions":
                        return Collections.singletonList("1.7.10");
                    case "getReleaseDate":
                        return "2026-09-24T00:00:00Z";
                    default:
                        throw new AssertionError(method.getName());
                }
            });
        LocalIndex.Companion.forFolder(folder)
            .record(file, "missing_addon", "project-12", version, "1.7.10", null);
        File renamed = new File(folder, "renamed.zip");
        Files.move(file.toPath(), renamed.toPath());
        InstalledPack pack = ProviderApi.getInstalledPack(renamed);
        assertEquals("missing_addon", pack.getPlatformId());
        assertEquals("project-12", pack.getProjectId());
        assertEquals("file-42", pack.getFileId());
        assertEquals("1.7.10", pack.getUpdateTrack());
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", pack.getSha1());
    }
}
