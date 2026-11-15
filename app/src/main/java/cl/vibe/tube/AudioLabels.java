package cl.vibe.tube;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Rótulos de las pistas de audio de la franja de Calidad: idioma, códec y bitrate, a partir de lo
 * que trae el formato de Media3. Es lógica pura, sin Android, para probarla en la JVM.
 */
final class AudioLabels {
    private AudioLabels() {}

    /** Nombre legible del códec («AAC», «Opus», «Dolby Digital», «Dolby Digital+»); null si no se reconoce. */
    static String codec(String codecs, String mime) {
        String c = codecs == null ? "" : codecs.toLowerCase(Locale.ROOT);
        if (c.startsWith("mp4a")) return "AAC";
        if (c.startsWith("opus")) return "Opus";
        if (c.startsWith("ec-3") || c.startsWith("ec3")) return "Dolby Digital+";
        if (c.startsWith("ac-3") || c.startsWith("ac3")) return "Dolby Digital";
        if (c.startsWith("flac")) return "FLAC";
        if (c.startsWith("vorbis")) return "Vorbis";
        String m = mime == null ? "" : mime.toLowerCase(Locale.ROOT);
        if (m.equals("audio/mp4a-latm")) return "AAC";
        if (m.equals("audio/opus")) return "Opus";
        if (m.equals("audio/eac3")) return "Dolby Digital+";
        if (m.equals("audio/ac3")) return "Dolby Digital";
        if (m.equals("audio/flac")) return "FLAC";
        if (m.equals("audio/vorbis")) return "Vorbis";
        return null;
    }

    /** Nombre del idioma en español, con mayúscula inicial («Español»); null si no hay idioma o no se reconoce. */
    static String language(String language) {
        if (language == null || language.isEmpty() || "und".equals(language)) return null;
        String name = new Locale(language).getDisplayLanguage(new Locale("es", "CL"));
        if (name.isEmpty() || name.equals(language)) return null;
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /**
     * Rótulo de una pista: «Original · Español · AAC · 128 kbps». Lo que falte se omite; si no
     * queda nada, «Pista». Con doblaje automático se agrega «(doblaje automático)».
     *
     * @param bitrate bits por segundo; 0 o menos si no se conoce
     */
    static String label(String language, String codecs, String mime, int bitrate, boolean original, boolean dubbed) {
        List<String> parts = new ArrayList<>();
        if (original) parts.add("Original");
        String lang = language(language);
        if (lang != null) parts.add(lang);
        String name = codec(codecs, mime);
        if (name != null) parts.add(name);
        if (bitrate > 0) parts.add(Math.round(bitrate / 1000f) + " kbps");
        if (parts.isEmpty()) parts.add("Pista");
        StringBuilder text = new StringBuilder();
        for (String part : parts) {
            if (text.length() > 0) text.append(" · ");
            text.append(part);
        }
        return dubbed ? text + " (doblaje automático)" : text.toString();
    }

    /** Hace únicos los rótulos repetidos: la segunda aparición lleva «· 2», la tercera «· 3», etc. */
    static List<String> unique(List<String> labels) {
        Map<String, Integer> seen = new HashMap<>();
        List<String> out = new ArrayList<>();
        for (String label : labels) {
            int count = seen.containsKey(label) ? seen.get(label) + 1 : 1;
            seen.put(label, count);
            out.add(count == 1 ? label : label + " · " + count);
        }
        return out;
    }
}
