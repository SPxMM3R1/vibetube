package cl.vibe.tube;

import android.net.Uri;
import android.util.Log;

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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fuente de datos para el manifiesto DASH de un directo. Media3 no acepta un MPD «dynamic» armado
 * con createMediaSource(manifest); pidiéndolo por URI, Media3 lo vuelve a pedir en cada refresco,
 * como en un directo normal. Cada apertura de «vibetube://mpd/…» llama de nuevo al núcleo para
 * tomar el MPD actual; el resto (segmentos) va por HTTP.
 */
@OptIn(markerClass = UnstableApi.class)
final class LiveSource implements DataSource {
    static final String SCHEME = "vibetube";
    private static final String TAG = MainActivity.TAG;

    private final DataSource http;
    private final Callable<InputStream> provider;
    /** Hora UNIX del primer segmento del núcleo (startNumber); la ancla del directo. */
    private final long startTimeMs;
    private final int startSegmentNum;
    private final int segmentDurationUs;
    /** Compartido por todas las fuentes de una misma reproducción: la línea de diagnóstico sale una vez. */
    private final AtomicBoolean logged;
    /** Manifiesto abierto ahora; si es null, los pedidos van por HTTP. */
    private InputStream manifest;
    private Uri uri;

    private LiveSource(DataSource http, Callable<InputStream> provider, long startTimeMs, int startSegmentNum,
                       int segmentDurationUs, AtomicBoolean logged) {
        this.http = http;
        this.provider = provider;
        this.startTimeMs = startTimeMs;
        this.startSegmentNum = startSegmentNum;
        this.segmentDurationUs = segmentDurationUs;
        this.logged = logged;
    }

    /**
     * Fábrica: una fuente por pedido; el MPD se toma de `provider` en cada apertura del manifiesto.
     *
     * @param startTimeMs      hora UNIX del primer segmento del núcleo (MediaItemFormatInfo.getStartTimeMs)
     * @param startSegmentNum  número del primer segmento (getStartSegmentNum), solo para el diagnóstico
     * @param segmentDurationUs duración de cada segmento (getSegmentDurationUs), solo para el diagnóstico
     */
    static DataSource.Factory factory(DataSource.Factory http, Callable<InputStream> provider,
                                      long startTimeMs, int startSegmentNum, int segmentDurationUs) {
        AtomicBoolean logged = new AtomicBoolean();
        return () -> new LiveSource(http.createDataSource(), provider, startTimeMs, startSegmentNum,
                segmentDurationUs, logged);
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
                long now = System.currentTimeMillis();
                String mpd = readText(provider.call());
                manifest = new ByteArrayInputStream(
                        Manifests.toLive(mpd, now, startTimeMs).getBytes(StandardCharsets.UTF_8));
                if (logged.compareAndSet(false, true)) {
                    Log.i(TAG, "Directo: start=" + startSegmentNum + " startTimeMs=" + startTimeMs
                            + " segUs=" + segmentDurationUs + " ast=" + Manifests.availabilityStart(mpd, now, startTimeMs));
                }
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
