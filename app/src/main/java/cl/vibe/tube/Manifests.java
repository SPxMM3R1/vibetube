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
     * Cuántos segmentos antes del final de la línea de tiempo queda el último disponible según el
     * reloj. Pedir el último segmento publicado da 404: Media3 pide números posteriores a los que
     * YouTube ya publicó.
     */
    static final int LIVE_EDGE_MARGIN_SEGMENTS = 3;

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
     * El núcleo deja un <SegmentTimeline> de 48 h que termina en el último segmento, sin
     * availabilityStartTime. Aquí se quita la línea de tiempo (queda el SegmentTemplate con
     * duration, startNumber, timescale, media y presentationTimeOffset) y se agrega el inicio de
     * disponibilidad: «ahora» menos (r + 1 − LIVE_EDGE_MARGIN_SEGMENTS) segmentos. Así el último
     * segmento disponible según el reloj queda tres segmentos antes del final de la línea de
     * tiempo, y Media3 no pide números que el núcleo todavía no publicó (404). Con líneas de
     * tiempo de menos de cuatro segmentos se deja al menos uno disponible. Si el MPD no tiene un
     * <S> reconocible, se devuelve sin cambios.
     *
     * @param mpd    texto del MPD del núcleo
     * @param nowMs  instante de referencia, en milisegundos desde la época UTC
     */
    static String toLive(String mpd, long nowMs) {
        Matcher first = FIRST_S.matcher(mpd);
        if (!first.find()) return mpd;
        Long d = attribute(first.group(1), "d");
        if (d == null || d <= 0) return mpd;
        long r = Math.max(0, valueOr(attribute(first.group(1), "r"), 0));

        long timescale = 1;
        Matcher template = SEGMENT_TEMPLATE.matcher(mpd);
        if (template.find()) timescale = Math.max(1, valueOr(attribute(template.group(), "timescale"), 1));

        // Segmentos ya disponibles según el reloj: (r + 1) menos el margen, al menos uno.
        long available = Math.max(1, r + 1 - LIVE_EDGE_MARGIN_SEGMENTS);
        long behindMs = available * d * 1000L / timescale;
        String start = isoUtc(nowMs - behindMs);

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
