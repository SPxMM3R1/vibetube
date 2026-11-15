package cl.vibe.tube;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Calidades de video para la franja de Calidad. Se agrupan por resolución (una pantalla por alto)
 * y, dentro de cada una, las variantes (1080p60 y 1080p, o VP9 y AV1) van por bitrate, de mayor a
 * menor. Es lógica pura, sin Android, para probarla en la JVM.
 */
final class Qualities {
    /** Ancho de la pantalla más grande, en unidades del mockup (16:9). */
    static final int TOP_WIDTH = 200;
    /** Ancho mínimo de una pantalla chica, para que quepa su etiqueta. */
    static final int MIN_WIDTH = 56;

    private Qualities() {}

    /** Una variante de video: resolución, cuadros por segundo, bitrate y la pista a la que apunta. */
    static final class Variant {
        final int width;
        final int height;
        final float fps;
        final int bitrate;
        final boolean hdr;
        /** Referencia opaca a la pista del reproductor. */
        final Object ref;

        Variant(int width, int height, float fps, int bitrate, boolean hdr, Object ref) {
            this.width = width;
            this.height = height;
            this.fps = fps;
            this.bitrate = bitrate;
            this.hdr = hdr;
            this.ref = ref;
        }
    }

    /** Una pantalla: todas las variantes de un mismo alto, la de mayor bitrate primero. */
    static final class Screen {
        final int height;
        final List<Variant> variants;

        Screen(int height, List<Variant> variants) {
            this.height = height;
            this.variants = variants;
        }

        /** La variante con que se rotula la pantalla: la de mayor bitrate. */
        Variant best() {
            return variants.get(0);
        }
    }

    /** Agrupa por alto, de la mayor a la menor resolución; dentro de cada alto, por bitrate. */
    static List<Screen> screens(List<Variant> all) {
        Map<Integer, List<Variant>> byHeight = new TreeMap<>(Collections.<Integer>reverseOrder());
        for (Variant v : all) {
            if (v.height <= 0) continue;
            List<Variant> list = byHeight.get(v.height);
            if (list == null) {
                list = new ArrayList<>();
                byHeight.put(v.height, list);
            }
            list.add(v);
        }
        List<Screen> out = new ArrayList<>();
        for (Map.Entry<Integer, List<Variant>> entry : byHeight.entrySet()) {
            List<Variant> list = entry.getValue();
            Collections.sort(list, (a, b) -> Integer.compare(b.bitrate, a.bitrate));
            out.add(new Screen(entry.getKey(), list));
        }
        return out;
    }

    /** Etiqueta de una variante: «1080p», o «1080p60» si pasa de 48 cuadros por segundo. */
    static String label(Variant v) {
        return v.fps >= 48 ? v.height + "p" + Math.round(v.fps) : v.height + "p";
    }

    /** Etiqueta de un alto solo: «2160p». */
    static String height(int height) {
        return height + "p";
    }

    /** Detalle de una variante: «1920 × 1080 · 6.2 Mbps», con «· HDR» si lo es. */
    static String detail(Variant v) {
        String text = String.format(Locale.US, "%d × %d · %.1f Mbps", v.width, v.height, v.bitrate / 1_000_000f);
        return v.hdr ? text + " · HDR" : text;
    }

    /**
     * Si la etiqueta de la pantalla i cabe, sin pisar a la de la pantalla inmediatamente mayor: su
     * esquina superior derecha tiene que quedar al menos labelHeight + gap por debajo y a la
     * izquierda de la de la pantalla anterior, en vertical y en horizontal. Las pantallas van de
     * mayor a menor, en px; la primera siempre cabe.
     */
    static boolean labelFits(int[] widths, int[] heights, int i, int labelHeight, int gap) {
        if (i <= 0) return true;
        int need = labelHeight + gap;
        return widths[i - 1] - widths[i] >= need && heights[i - 1] - heights[i] >= need;
    }

    /** Ancho de la pantalla de una resolución: proporcional a su alto frente a la mayor, mínimo 56. */
    static int width(int height, int maxHeight) {
        if (maxHeight <= 0) return TOP_WIDTH;
        return Math.max(MIN_WIDTH, Math.round(TOP_WIDTH * height / (float) maxHeight));
    }
}
