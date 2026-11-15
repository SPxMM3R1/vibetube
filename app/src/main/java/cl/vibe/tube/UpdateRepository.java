package cl.vibe.tube;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Habla con GitHub: pide la última versión publicada (releases/latest) y descarga su APK. Las
 * reglas de versión y de nombre están en UpdateRules.
 */
final class UpdateRepository {
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final int MAX_JSON_BYTES = 1_000_000;
    private static final long MAX_APK_BYTES = 250L * 1024L * 1024L;
    private static final String PARTIAL_NAME = "VibeTube-update.apk.part";
    private static final String COMPLETED_NAME = "VibeTube-update.apk";

    interface ProgressListener {
        void onProgress(int percent);
    }

    /**
     * La versión publicada si es más nueva que la instalada; null si está al día. Lanza error si
     * GitHub no responde, si la versión no trae el APK o si la dirección no es la esperada.
     */
    UpdateInfo findAvailableUpdate(String currentVersionName) throws Exception {
        HttpURLConnection connection = openConnection(URI.create(UpdateRules.LATEST_RELEASE_URL));
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");

        int status = connection.getResponseCode();
        if (status != HttpURLConnection.HTTP_OK) {
            connection.disconnect();
            throw new IOException("GitHub respondió " + status + ".");
        }

        String response;
        try (InputStream input = connection.getInputStream()) {
            response = readLimitedText(input, MAX_JSON_BYTES);
        } finally {
            connection.disconnect();
        }

        JSONObject release = new JSONObject(response);
        if (release.optBoolean("draft") || release.optBoolean("prerelease")) return null;

        String tagName = release.optString("tag_name", "").trim();
        String versionName = UpdateRules.normalizeVersion(tagName);
        if (versionName.isEmpty() || !UpdateRules.isNewerVersion(versionName, currentVersionName)) {
            return null;
        }

        JSONArray assets = release.optJSONArray("assets");
        if (assets == null) throw new IOException("La versión no trae archivos.");

        for (int index = 0; index < assets.length(); index++) {
            JSONObject asset = assets.optJSONObject(index);
            if (asset == null || !UpdateRules.isApkAssetName(asset.optString("name"))) continue;

            URI downloadUri = URI.create(asset.getString("browser_download_url"));
            if (!UpdateRules.isReleaseDownload(downloadUri)) {
                throw new IOException("GitHub entregó una dirección de descarga no válida.");
            }
            return new UpdateInfo(
                    tagName,
                    versionName,
                    downloadUri,
                    Math.max(0, asset.optLong("size", 0))
            );
        }
        throw new IOException("La versión " + tagName + " no trae el APK de VibeTube.");
    }

    /**
     * Descarga el APK a cache/updates y devuelve el archivo listo. Informa el porcentaje; si el
     * tamaño no se conoce, el porcentaje no avanza hasta el final.
     */
    File download(UpdateInfo update, File cacheDirectory, ProgressListener listener) throws Exception {
        if (!UpdateRules.isReleaseDownload(update.getDownloadUri())) {
            throw new IOException("La dirección de descarga no es válida.");
        }
        File updateDirectory = new File(cacheDirectory, "updates");
        if (!updateDirectory.exists() && !updateDirectory.mkdirs()) {
            throw new IOException("No se pudo crear la carpeta de la actualización.");
        }

        File partial = new File(updateDirectory, PARTIAL_NAME);
        File completed = new File(updateDirectory, COMPLETED_NAME);
        if (partial.exists() && !partial.delete()) {
            throw new IOException("No se pudo limpiar la descarga anterior.");
        }

        HttpURLConnection connection = openFollowingRedirects(update.getDownloadUri());
        int status = connection.getResponseCode();
        if (status != HttpURLConnection.HTTP_OK) {
            connection.disconnect();
            throw new IOException("La descarga respondió " + status + ".");
        }

        long contentLength = connection.getContentLength();
        if (contentLength <= 0) contentLength = update.getSizeBytes();
        if (contentLength > MAX_APK_BYTES) {
            connection.disconnect();
            throw new IOException("El APK supera el tamaño máximo permitido.");
        }
        long total = 0;
        int lastPercent = -1;

        try (InputStream input = new BufferedInputStream(connection.getInputStream());
             FileOutputStream output = new FileOutputStream(partial)) {
            byte[] buffer = new byte[32 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                total += read;
                if (total > MAX_APK_BYTES) {
                    throw new IOException("El APK supera el tamaño máximo permitido.");
                }
                if (contentLength > 0) {
                    int percent = (int) Math.min(100, (total * 100L) / contentLength);
                    if (percent != lastPercent) {
                        lastPercent = percent;
                        listener.onProgress(percent);
                    }
                }
            }
            output.getFD().sync();
        } finally {
            connection.disconnect();
        }

        if (total <= 0) {
            partial.delete();
            throw new IOException("GitHub entregó un archivo vacío.");
        }
        if (completed.exists() && !completed.delete()) {
            partial.delete();
            throw new IOException("No se pudo reemplazar la actualización anterior.");
        }
        if (!partial.renameTo(completed)) {
            partial.delete();
            throw new IOException("No se pudo terminar la descarga.");
        }
        listener.onProgress(100);
        return completed;
    }

    /** Sigue las redirecciones de GitHub (hacia su CDN), solo si siguen siendo HTTPS. */
    private static HttpURLConnection openFollowingRedirects(URI initialUri) throws Exception {
        URI current = initialUri;
        for (int redirect = 0; redirect < 6; redirect++) {
            HttpURLConnection connection = openConnection(current);
            connection.setInstanceFollowRedirects(false);
            int status = connection.getResponseCode();
            if (status < 300 || status >= 400) return connection;

            String location = connection.getHeaderField("Location");
            connection.disconnect();
            if (location == null || location.trim().isEmpty()) {
                throw new IOException("GitHub redirigió la descarga sin destino.");
            }
            current = current.resolve(location.trim());
            if (!"https".equalsIgnoreCase(current.getScheme())) {
                throw new IOException("GitHub intentó usar una descarga insegura.");
            }
        }
        throw new IOException("Demasiadas redirecciones al descargar.");
    }

    private static HttpURLConnection openConnection(URI uri) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", "VibeTube-Android-TV");
        return connection;
    }

    private static String readLimitedText(InputStream input, int limit) throws IOException {
        StringBuilder result = new StringBuilder();
        int total = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                total += read;
                if (total > limit) throw new IOException("Respuesta de GitHub demasiado grande.");
                result.append(buffer, 0, read);
            }
        }
        return result.toString();
    }
}
