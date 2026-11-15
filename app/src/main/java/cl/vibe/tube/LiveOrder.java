package cl.vibe.tube;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Orden de las fuentes de un directo: primero DASH dinámico (con {@link LiveSource}), después HLS.
 * El HLS de este cliente se queda colgado en algunos directos, por eso va segundo (decisión del
 * revisor, 2026-11-15). Si ninguna fuente sirve, la lista queda vacía y no hay nada que probar.
 */
final class LiveOrder {
    enum Kind {
        DASH("DASH"),
        HLS("HLS");

        /** Nombre que aparece en el log (tag VibeTube). */
        final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    private final List<Kind> kinds;
    private int index;

    LiveOrder(boolean dash, boolean hls) {
        List<Kind> list = new ArrayList<>(2);
        if (dash) list.add(Kind.DASH);
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
