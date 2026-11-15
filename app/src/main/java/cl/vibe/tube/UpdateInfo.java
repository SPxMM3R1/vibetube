package cl.vibe.tube;

import java.net.URI;

/** Una versión nueva publicada: la etiqueta, la versión sin «v», la dirección del APK y su tamaño. */
final class UpdateInfo {
    private final String tagName;
    private final String versionName;
    private final URI downloadUri;
    private final long sizeBytes;

    UpdateInfo(String tagName, String versionName, URI downloadUri, long sizeBytes) {
        this.tagName = tagName;
        this.versionName = versionName;
        this.downloadUri = downloadUri;
        this.sizeBytes = sizeBytes;
    }

    String getTagName() {
        return tagName;
    }

    String getVersionName() {
        return versionName;
    }

    URI getDownloadUri() {
        return downloadUri;
    }

    long getSizeBytes() {
        return sizeBytes;
    }
}
