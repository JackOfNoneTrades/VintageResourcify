package dev.dediamondpro.resourcify.api;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.cert.Certificate;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import dev.dediamondpro.resourcify.services.DistributionPolicy;
import dev.dediamondpro.resourcify.services.IProject;
import dev.dediamondpro.resourcify.services.IService;
import dev.dediamondpro.resourcify.services.IVersion;
import dev.dediamondpro.resourcify.services.ProjectType;
import dev.dediamondpro.resourcify.services.ServiceRegistry;

public class ModrinthProviderTest {

    private static final String API = "https://127.0.0.2/api/v2";
    private static final String SITE = "https://127.0.0.3";
    private static final String VERSION = "{\"name\":\"Example 2\",\"project_id\":\"p\",\"version_number\":\"2\","
        + "\"version_type\":\"release\",\"date_published\":\"2026-09-24T00:00:00Z\","
        + "\"game_versions\":[\"1.7.10\"],\"loaders\":[\"minecraft\"],\"files\":[{"
        + "\"url\":\"https://127.0.0.3/pack.zip\",\"filename\":\"pack.zip\",\"primary\":true,"
        + "\"hashes\":{\"sha1\":\"0000000000000000000000000000000000000000\"}}],"
        + "\"dependencies\":[{\"project_id\":\"dep\",\"dependency_type\":\"required\"}]}";

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @BeforeClass
    public static void useOfflineResponses() {
        // This test JVM never needs real HTTPS. Fail on any request to the wrong platform.
        // DownloadManagerTest supplies its own URL handler and is independent of this factory.
        URL.setURLStreamHandlerFactory(protocol -> "https".equals(protocol) ? new URLStreamHandler() {

            @Override
            protected URLConnection openConnection(URL url) {
                assertEquals("127.0.0.2", url.getHost());
                return new HttpsURLConnection(url) {

                    @Override
                    public InputStream getInputStream() {
                        return new ByteArrayInputStream(response(url).getBytes(StandardCharsets.UTF_8));
                    }

                    @Override
                    public OutputStream getOutputStream() {
                        return new ByteArrayOutputStream();
                    }

                    @Override
                    public String getCipherSuite() {
                        return "test";
                    }

                    @Override
                    public Certificate[] getLocalCertificates() {
                        return new Certificate[0];
                    }

                    @Override
                    public Certificate[] getServerCertificates() {
                        return new Certificate[0];
                    }

                    @Override
                    public void connect() {}

                    @Override
                    public void disconnect() {}

                    @Override
                    public boolean usingProxy() {
                        return false;
                    }
                };
            }
        } : null);
    }

    private static String response(URL url) {
        switch (url.getPath()) {
            case "/api/v2/search":
                return "{\"total_hits\":1,\"hits\":[{\"project_id\":\"p\",\"slug\":\"pack\","
                    + "\"title\":\"Pack\",\"project_type\":\"resourcepack\"}]}";
            case "/api/v2/project/pack/members":
                return "[{\"user\":{\"username\":\"author\"},\"role\":\"Owner\"}]";
            case "/api/v2/project/pack/version":
                return "[" + VERSION + "]";
            case "/api/v2/projects":
                if (url.getQuery()
                    .contains("dep")) {
                    return "[{\"id\":\"dep\",\"slug\":\"dependency\",\"project_type\":\"resourcepack\"}]";
                }
                return "[{\"id\":\"p\",\"slug\":\"pack\",\"project_type\":\"resourcepack\"}]";
            case "/api/v2/version_files/update":
                return "{\"a9993e364706816aba3e25717850c26c9cd0d89d\":" + VERSION + "}";
            default:
                throw new AssertionError("Unexpected endpoint: " + url);
        }
    }

    @Test
    public void registeredAdapterKeepsRequestsAndLinksOnItsPlatform() throws Exception {
        assertEquals(
            RegistrationResult.REGISTERED,
            ProviderApi.registerModrinth("compatible_test", "Compatible", " " + API + "/// ", SITE + "/"));
        IService service = ProviderApi.getProvider("compatible_test");
        assertEquals("Compatible", service.getName());
        assertTrue(DistributionPolicy.canDownloadFrom("compatible_test"));
        assertTrue(ServiceRegistry.INSTANCE.isAddonProvider("compatible_test"));
        assertTrue(service.canFetchProjectUrl(URI.create(SITE + "/resourcepack/pack")));
        assertFalse(service.canFetchProjectUrl(URI.create("https://modrinth.com/resourcepack/pack")));
        IProject project = service
            .search(
                "pack",
                "relevance",
                Collections.singletonList("1.7.10"),
                Collections.emptyList(),
                0,
                ProjectType.RESOURCE_PACK)
            .getProjects()
            .get(0);
        assertEquals(SITE + "/resourcepack/pack", project.getBrowserUrl());
        assertEquals(
            SITE + "/user/author",
            project.getMembers()
                .get(5, TimeUnit.SECONDS)
                .get(0)
                .getUrl());
        IVersion version = project.getVersions()
            .get(5, TimeUnit.SECONDS)
            .get(0);
        assertEquals(
            SITE + "/resourcepack/dependency",
            version.getDependencies()
                .get(5, TimeUnit.SECONDS)
                .get(0)
                .getProject()
                .getBrowserUrl());

        IProject full = service.getProjectsFromIds(Collections.singletonList("p"))
            .get("p");
        assertEquals(
            SITE + "/user/author",
            full.getMembers()
                .get(5, TimeUnit.SECONDS)
                .get(0)
                .getUrl());
        assertEquals(
            SITE + "/resourcepack/dependency",
            full.getVersions()
                .get(5, TimeUnit.SECONDS)
                .get(0)
                .getDependencies()
                .get(5, TimeUnit.SECONDS)
                .get(0)
                .getProject()
                .getBrowserUrl());

        File installed = temporary.newFile("pack.zip");
        Files.write(installed.toPath(), "abc".getBytes(StandardCharsets.UTF_8));
        IVersion update = service.getUpdates(Collections.singletonList(installed), ProjectType.RESOURCE_PACK, "1.7.10")
            .get(5, TimeUnit.SECONDS)
            .get(installed);
        assertEquals(
            SITE + "/resourcepack/dependency",
            update.getDependencies()
                .get(5, TimeUnit.SECONDS)
                .get(0)
                .getProject()
                .getBrowserUrl());
        ServiceRegistry.INSTANCE.loadConfiguredServices();
        assertSame(service, ProviderApi.getProvider("compatible_test"));
        assertEquals(
            RegistrationResult.DUPLICATE_ID,
            ProviderApi.registerModrinth("compatible_test", "Duplicate", API, SITE));
    }

    @Test
    public void rejectsMalformedEndpointsBeforeRegistering() {
        for (String invalid : new String[] { "http://example.org/api/v2", "https://user:pass@example.org/api/v2",
            "https://example.org/api/v2?key=secret", "https://example.org/#fragment", "not a URL" }) {
            assertThrows(
                IllegalArgumentException.class,
                () -> ProviderApi.registerModrinth("bad_endpoint", "Example", invalid, SITE));
            assertThrows(
                IllegalArgumentException.class,
                () -> ProviderApi.registerModrinth("bad_endpoint", "Example", API, invalid));
        }
        assertNull(ProviderApi.getProvider("bad_endpoint"));
        assertThrows(
            IllegalArgumentException.class,
            () -> ProviderApi.registerModrinth("bad_endpoint", " ", API, SITE));
        assertEquals(RegistrationResult.INVALID_ID, ProviderApi.registerModrinth("Bad ID", "Example", API, SITE));
        assertEquals(RegistrationResult.RESERVED_ID, ProviderApi.registerModrinth("modrinth", "Example", API, SITE));
    }
}
