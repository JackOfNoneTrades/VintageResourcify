package dev.dediamondpro.resourcify.api;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.cert.Certificate;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.HttpsURLConnection;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import dev.dediamondpro.resourcify.services.IVersion;
import dev.dediamondpro.resourcify.util.DownloadManager;
import dev.dediamondpro.resourcify.util.DownloadResolver;
import dev.dediamondpro.resourcify.util.DownloadResult;

public class DownloadManagerTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    private URL memoryUrl(AtomicInteger requests) throws Exception {
        return new URL(null, "https://127.0.0.1/pack.zip", new URLStreamHandler() {

            @Override
            protected URLConnection openConnection(URL url) {
                return new HttpsURLConnection(url) {

                    @Override
                    public InputStream getInputStream() {
                        requests.incrementAndGet();
                        return new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8));
                    }

                    @Override
                    public int getContentLength() {
                        return 3;
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
        });
    }

    @Test
    public void verifiesBeforeReplacingInstalledFile() throws Exception {
        assertEquals(RegistrationResult.REGISTERED, ProviderApi.register(ProviderApiTest.provider("manager_example")));
        File file = temporary.newFile("pack.zip");
        Files.write(file.toPath(), "old pack".getBytes(StandardCharsets.UTF_8));
        AtomicInteger requests = new AtomicInteger();
        URL url = memoryUrl(requests);
        DownloadChecksum wrong = new DownloadChecksum(
            DownloadChecksum.Algorithm.MD5,
            "00000000000000000000000000000000");
        assertEquals(
            DownloadResult.FAILED,
            DownloadManager.INSTANCE.downloadResolved("manager_example", file, DownloadTarget.direct(url, wrong))
                .get(5, TimeUnit.SECONDS));
        assertEquals("old pack", new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));

        DownloadChecksum correct = new DownloadChecksum(
            DownloadChecksum.Algorithm.MD5,
            "900150983cd24fb0d6963f7d28e17f72");
        DownloadResult result = DownloadManager.INSTANCE
            .downloadResolved("manager_example", file, DownloadTarget.direct(url, correct))
            .get(5, TimeUnit.SECONDS);
        assertEquals(DownloadResult.SUCCESS, result);
        assertEquals("abc", new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        assertEquals(2, requests.get());
    }

    @Test
    public void browserTargetNeverWritesAFile() throws Exception {
        File target = new File(temporary.getRoot(), "pack.zip");
        AtomicInteger requests = new AtomicInteger();
        DownloadTarget browser = DownloadTarget.browser(memoryUrl(requests));
        assertEquals(
            DownloadResult.FAILED,
            DownloadManager.INSTANCE.downloadResolved("modrinth", target, browser)
                .get(5, TimeUnit.SECONDS));
        assertEquals(0, requests.get());
        assertFalse(target.exists());
    }

    @Test
    public void rejectsEscapingProviderFilename() throws Exception {
        for (String name : new String[] { "../pack.zip", "..\\pack.zip", "/pack.zip", "C:pack.zip", ".." }) {
            IVersion version = (IVersion) Proxy.newProxyInstance(
                IVersion.class.getClassLoader(),
                new Class<?>[] { IVersion.class },
                (proxy, method, args) -> name);
            assertThrows(
                IllegalArgumentException.class,
                () -> DownloadResolver.INSTANCE.targetFile(temporary.getRoot(), version));
        }
    }
}
