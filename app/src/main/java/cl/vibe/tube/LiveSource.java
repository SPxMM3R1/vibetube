package cl.vibe.tube;

import android.net.Uri;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Fuente de datos para el manifiesto DASH de un directo. Media3 no acepta un MPD «dynamic» armado
 * con createMediaSource(manifest); pidiéndolo por URI, Media3 lo vuelve a pedir en cada refresco,
 * como en un directo normal. Cada apertura de «vibetube://mpd/…» llama de nuevo al núcleo para
 * tomar el MPD actual; el resto (segmentos) va por HTTP.
 */
@OptIn(markerClass = UnstableApi.class)
final class LiveSource implements DataSource {
    static final String SCHEME = "vibetube";

    private final DataSource http;
    private final Callable<InputStream> provider;
    /** Manifiesto abierto ahora; si es null, los pedidos van por HTTP. */
    private InputStream manifest;
    private Uri uri;

    private LiveSource(DataSource http, Callable<InputStream> provider) {
        this.http = http;
        this.provider = provider;
    }

    /** Fábrica: una fuente por pedido; el MPD se toma de `provider` en cada apertura del manifiesto. */
    static DataSource.Factory factory(DataSource.Factory http, Callable<InputStream> provider) {
        return () -> new LiveSource(http.createDataSource(), provider);
    }

    /** URI del manifiesto de un directo. */
    static Uri uri(String videoId) {
        return Uri.parse(SCHEME + "://mpd/" + videoId);
    }

    @Override
    public void addTransferListener(TransferListener listener) {
        http.addTransferListener(listener);
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        if (SCHEME.equals(dataSpec.uri.getScheme())) {
            try {
                // El MPD del núcleo se ajusta a directo (Manifests.toLive) antes de entregarlo a Media3.
                String mpd = readText(provider.call());
                manifest = new ByteArrayInputStream(
                        Manifests.toLive(mpd, System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8));
            } catch (IOException error) {
                throw error;
            } catch (Exception error) {
                throw new IOException("No se pudo pedir el manifiesto del directo", error);
            }
            uri = dataSpec.uri;
            return C.LENGTH_UNSET;
        }
        return http.open(dataSpec);
    }

    /** Lee el stream entero como texto UTF-8 y lo cierra. */
    private static String readText(InputStream in) throws IOException {
        try (InputStream stream = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = stream.read(buffer)) != -1) out.write(buffer, 0, read);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (manifest != null) return manifest.read(buffer, offset, length);
        return http.read(buffer, offset, length);
    }

    @Override
    public Uri getUri() {
        return manifest != null ? uri : http.getUri();
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return manifest != null ? Collections.<String, List<String>>emptyMap() : http.getResponseHeaders();
    }

    @Override
    public void close() throws IOException {
        if (manifest != null) {
            manifest.close();
            manifest = null;
        } else {
            http.close();
        }
    }
}
