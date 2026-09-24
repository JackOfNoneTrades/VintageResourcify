package dev.dediamondpro.resourcify.api;

/** Read-only installation provenance. Nullable fields were not recorded for that installation. */
public final class InstalledPack {

    private final String platformId;
    private final String projectId;
    private final String fileId;
    private final String sha1;
    private final String updateTrack;
    private final String releaseDate;

    public InstalledPack(String platformId, String projectId, String fileId, String sha1, String updateTrack,
        String releaseDate) {
        this.platformId = platformId;
        this.projectId = projectId;
        this.fileId = fileId;
        this.sha1 = sha1;
        this.updateTrack = updateTrack;
        this.releaseDate = releaseDate;
    }

    public String getPlatformId() {
        return platformId;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getFileId() {
        return fileId;
    }

    public String getSha1() {
        return sha1;
    }

    public String getUpdateTrack() {
        return updateTrack;
    }

    public String getReleaseDate() {
        return releaseDate;
    }
}
