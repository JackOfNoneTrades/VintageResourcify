package dev.dediamondpro.resourcify.api;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Objects;

/** Expected digest supplied by a provider, checked before replacing any installed file. */
public final class DownloadChecksum {

    public enum Algorithm {

        MD5("MD5", 32),
        SHA1("SHA-1", 40),
        SHA256("SHA-256", 64),
        SHA512("SHA-512", 128);

        private final String digestName;
        private final int hexLength;

        Algorithm(String digestName, int hexLength) {
            this.digestName = digestName;
            this.hexLength = hexLength;
        }
    }

    private final Algorithm algorithm;
    private final String hex;

    public DownloadChecksum(Algorithm algorithm, String hex) {
        this.algorithm = Objects.requireNonNull(algorithm, "algorithm");
        Objects.requireNonNull(hex, "hex");
        if (hex.length() != algorithm.hexLength || !hex.matches("[0-9a-fA-F]+")) {
            throw new IllegalArgumentException("Invalid " + algorithm.digestName + " checksum");
        }
        this.hex = hex.toLowerCase(Locale.ROOT);
    }

    public Algorithm getAlgorithm() {
        return algorithm;
    }

    public String getHex() {
        return hex;
    }

    public boolean matches(File file) throws IOException {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(algorithm.digestName);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        try (InputStream input = Files.newInputStream(file.toPath())) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder actual = new StringBuilder(algorithm.hexLength);
        for (byte value : digest.digest()) {
            actual.append(Character.forDigit((value >>> 4) & 15, 16));
            actual.append(Character.forDigit(value & 15, 16));
        }
        return hex.contentEquals(actual);
    }
}
