package dev.dediamondpro.resourcify.api;

import static org.junit.Assert.*;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DownloadChecksumTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void knownDigestsMatchAndChangedContentFails() throws Exception {
        String[] digests = { "900150983cd24fb0d6963f7d28e17f72", "a9993e364706816aba3e25717850c26c9cd0d89d",
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            "ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f" };
        File file = temporary.newFile();
        for (DownloadChecksum.Algorithm algorithm : DownloadChecksum.Algorithm.values()) {
            DownloadChecksum checksum = new DownloadChecksum(algorithm, digests[algorithm.ordinal()].toUpperCase());
            Files.write(file.toPath(), "abc".getBytes(StandardCharsets.UTF_8));
            assertTrue(checksum.matches(file));
            Files.write(file.toPath(), "abcd".getBytes(StandardCharsets.UTF_8));
            assertFalse(checksum.matches(file));
        }
    }

    @Test
    public void rejectsWrongDigestAlgorithmAndUnsafeUrls() throws Exception {
        assertThrows(
            IllegalArgumentException.class,
            () -> new DownloadChecksum(DownloadChecksum.Algorithm.SHA1, "900150983cd24fb0d6963f7d28e17f72"));
        assertThrows(
            IllegalArgumentException.class,
            () -> DownloadTarget.direct(new URL("file:///tmp/pack.zip"), null));
        assertThrows(
            IllegalArgumentException.class,
            () -> DownloadTarget.direct(new URL("http://example.org/pack.zip"), null));
        assertThrows(
            IllegalArgumentException.class,
            () -> DownloadTarget.browser(new URL("https://user:secret@example.org/")));
        assertTrue(
            DownloadTarget.browser(new URL("https://example.org/download"))
                .isBrowser());
    }
}
