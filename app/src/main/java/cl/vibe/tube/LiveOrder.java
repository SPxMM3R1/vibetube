package cl.vibe.tube;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Orden de las fuentes de un directo, como en SmartTube (VideoLoaderController): primero el DASH
 * generado desde los formatos (con {@link LiveSource}, solo si el núcleo trae la hora del primer
 * segmento), después el DASH propio de YouTube por URL, y al final HLS. El HLS de YouTube solo
 * funciona bien con el cliente iOS (lo comenta tubehaus en su servidor), por eso va al final.
 * Si ninguna fuente sirve, la lista queda vacía y el directo no se puede abrir.
 */
final class LiveOrder {
    enum Kind {
        DASH("DASH"),
        DASH_URL("DASH de YouTube"),
        HLS("HLS");

        /** Nombre que aparece en el log (tag VibeTube). */
        final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    private final List<Kind> kinds;
    private int index;

    /**
     * @param dashFormats hay formatos DASH con los que armar el manifiesto generado
     * @param startTimeMs hora UNIX del primer segmento del núcleo; el DASH generado solo se usa si es mayor que 0
     * @param dashUrl     el núcleo trae la URL del DASH de YouTube
     * @param hls         el núcleo trae la URL del HLS
     */
    LiveOrder(boolean dashFormats, long startTimeMs, boolean dashUrl, boolean hls) {
        List<Kind> list = new ArrayList<>(3);
        if (dashFormats && startTimeMs > 0) list.add(Kind.DASH);
        if (dashUrl) list.add(Kind.DASH_URL);
        if (hls) list.add(Kind.HLS);
        kinds = Collections.unmodifiableList(list);
    }

    /** true si el directo no trae ninguna fuente que el cliente sepa abrir. */
    boolean isEmpty() {
        return kinds.isEmpty();
    }

    /** Fuente en uso. Solo se llama si la lista no está vacía. */
    Kind current() {
        return kinds.get(index);
    }

    /** Pasa a la fuente siguiente; devuelve false si ya no hay otra (y no mueve nada). */
    boolean advance() {
        if (index + 1 >= kinds.size()) return false;
        index++;
        return true;
    }
}
