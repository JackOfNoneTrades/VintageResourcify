package dev.dediamondpro.resourcify.api;

import dev.dediamondpro.resourcify.services.IVersion;

/** Optional stable remote file identity, independent of expiring URLs and display names. */
public interface IdentifiedVersion extends IVersion {

    String getFileId();
}
