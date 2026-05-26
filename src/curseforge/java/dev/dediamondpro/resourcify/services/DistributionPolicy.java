package dev.dediamondpro.resourcify.services;

import java.util.Locale;

public final class DistributionPolicy {

    public static final String CURSEFORGE_LIMITED_MESSAGE = "This CurseForge build only supports downloads from Modrinth and CurseForge.";

    private DistributionPolicy() {}

    public static boolean allowConfiguredPlatforms() {
        return false;
    }

    public static boolean canDownloadFrom(String platformId) {
        String normalized = normalizePlatformId(platformId);
        return "modrinth".equals(normalized) || "curseforge".equals(normalized);
    }

    public static String downloadBlockedMessage(String platformId) {
        return "Downloads from " + displayPlatform(platformId) + " unavailable in this build";
    }

    private static String normalizePlatformId(String platformId) {
        if (platformId == null) return "";
        return platformId.trim()
            .toLowerCase(Locale.ROOT);
    }

    private static String displayPlatform(String platformId) {
        if (platformId == null || platformId.trim()
            .isEmpty()) {
            return "this platform";
        }
        String normalized = platformId.trim()
            .replace('_', ' ')
            .replace('-', ' ')
            .toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(normalized.length());
        boolean capitalizeNext = true;
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (Character.isWhitespace(c)) {
                builder.append(c);
                capitalizeNext = true;
            } else if (capitalizeNext) {
                builder.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }
}
