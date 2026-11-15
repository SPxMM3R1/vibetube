package cl.vibe.tube;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Ajustes de los manifiestos DASH que entrega el núcleo. Sin Android, para probarlo en la JVM. */
final class Manifests {
    /** Un <SegmentTimeline> completo, vacío o autocerrado. */
    private static final Pattern TIMELINE =
            Pattern.compile("<SegmentTimeline\\b[^>]*?(?:/>|>.*?</SegmentTimeline\\s*>)", Pattern.DOTALL);
    /** El primer <S> de la línea de tiempo: sus atributos van en el grupo 1. */
    private static final Pattern FIRST_S = Pattern.compile("<S\\s([^>]*?)/?>");
    private static final Pattern SEGMENT_TEMPLATE = Pattern.compile("<SegmentTemplate\\b[^>]*>");
    private static final Pattern MPD_TAG = Pattern.compile("<MPD\\b[^>]*>");
    /** Atributos que toLive agrega; si ya están, se reemplazan. */
    private static final Pattern LIVE_ATTRS =
            Pattern.compile("\\s(?:availabilityStartTime|timeShiftBufferDepth|suggestedPresentationDelay)=\"[^\"]*\"");
    /** Cuánto se puede retroceder en un directo (la ventana de tiempo desplazable). */
    static final String TIME_SHIFT = "PT2H";
    /** Retraso sugerido respecto del borde en vivo. */
    static final String PRESENTATION_DELAY = "PT20S";
    /**
     * Cuántos segmentos se retrasa el último disponible respecto del inicio del primer segmento.
     * Pedir el último segmento publicado da 404: Media3 pide números posteriores a los que YouTube
     * ya publicó.
     */
    static final int LIVE_EDGE_MARGIN_SEGMENTS = 3;
    /** Tope de la ventana de un directo en el núcleo: 24 h. */
    private static final long DAY_SECONDS = 24 * 60 * 60;

    private Manifests() {}

    /**
     * Un video que no es en vivo es un manifiesto estático. El núcleo lo entrega como «dynamic» y
     * Media3 lo rechaza al armar la fuente con createMediaSource(manifest, ...).
     */
    static String toStatic(String mpd) {
        return mpd.replace("type=\"dynamic\"", "type=\"static\"");
    }

    /**
     * Convierte el MPD «dynamic» de un directo en uno que Media3 acepta y que avanza con el reloj.
     *
     * El núcleo deja un <SegmentTimeline> que no marca el borde en vivo, así que se quita (queda
     * el SegmentTemplate con duration, startNumber, timescale, media y presentationTimeOffset) y
     * se fija availabilityStartTime según availabilityStart. Se quitan también los atributos de
     * ventana y retraso del núcleo, y se agregan timeShiftBufferDepth y suggestedPresentationDelay.
     * Si el MPD no tiene un <S> reconocible, se devuelve sin cambios.
     *
     * @param mpd         texto del MPD del núcleo
     * @param nowMs       instante de referencia, en milisegundos desde la época UTC
     * @param startTimeMs hora UNIX del primer segmento (startNumber) que entrega el núcleo, o 0 o
     *                    menos si no la hay
     */
    static String toLive(String mpd, long nowMs, long startTimeMs) {
        String start = availabilityStart(mpd, nowMs, startTimeMs);
        if (start == null) return mpd;

        String out = TIMELINE.matcher(mpd).replaceAll("");
        Matcher tag = MPD_TAG.matcher(out);
        if (!tag.find()) return mpd;
        String open = LIVE_ATTRS.matcher(tag.group()).replaceAll("");
        open = open.substring(0, open.length() - 1)
                + " availabilityStartTime=\"" + start + "\""
                + " timeShiftBufferDepth=\"" + TIME_SHIFT + "\""
                + " suggestedPresentationDelay=\"" + PRESENTATION_DELAY + "\">";
        return out.substring(0, tag.start()) + open + out.substring(tag.end());
    }

    /**
     * Inicio de disponibilidad (availabilityStartTime) del directo, en UTC con el formato de DASH,
     * o null si el MPD no tiene un <S> reconocible.
     *
     * Con startTimeMs > 0 se ancla a la hora real del primer segmento y se retrasa
     * LIVE_EDGE_MARGIN_SEGMENTS segmentos: el último disponible queda ese margen antes del borde.
     * Sin startTimeMs (respaldo) se calcula desde «ahora»: min(r + 1, segmentos de 24 h) menos el
     * margen, con mínimo un segmento.
     */
    static String availabilityStart(String mpd, long nowMs, long startTimeMs) {
        Matcher first = FIRST_S.matcher(mpd);
        if (!first.find()) return null;
        Long d = attribute(first.group(1), "d");
        if (d == null || d <= 0) return null;
        long r = Math.max(0, valueOr(attribute(first.group(1), "r"), 0));

        long timescale = 1;
        Matcher template = SEGMENT_TEMPLATE.matcher(mpd);
        if (template.find()) timescale = Math.max(1, valueOr(attribute(template.group(), "timescale"), 1));

        if (startTimeMs > 0) {
            return isoUtc(startTimeMs + LIVE_EDGE_MARGIN_SEGMENTS * d * 1000L / timescale);
        }
        // Respaldo: segmentos disponibles según el reloj, sin pasar de 24 h ni de la línea de tiempo.
        long maxSegments = DAY_SECONDS * timescale / d;
        long available = Math.max(1, Math.min(r + 1, maxSegments) - LIVE_EDGE_MARGIN_SEGMENTS);
        return isoUtc(nowMs - available * d * 1000L / timescale);
    }

    /** Valor numérico de un atributo XML («name="123"»), o null si no está o no es número. */
    private static Long attribute(String attributes, String name) {
        Matcher m = Pattern.compile("(?:^|\\s)" + name + "\\s*=\\s*\"([^\"]*)\"").matcher(attributes);
        if (!m.find()) return null;
        try {
            return Long.parseLong(m.group(1).trim());
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private static long valueOr(Long value, long fallback) {
        return value == null ? fallback : value;
    }

    /** «2027-01-15T08:00:00.000Z»: el formato de fecha de DASH, en UTC y con milisegundos. */
    private static String isoUtc(long ms) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(ms));
    }
}
