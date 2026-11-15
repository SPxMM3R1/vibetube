package cl.vibe.tube;

import java.net.URI;
import java.util.Locale;

/**
 * Reglas del actualizador que no dependen de Android ni de la red: qué versión es más nueva, qué
 * archivo del release es el APK de VibeTube, qué dirección de descarga se acepta y cuándo toca
 * volver a revisar. Son clases puras para probarlas en la JVM (`UpdateRulesTest`).
 */
final class UpdateRules {
    /** Repositorio de las versiones publicadas. */
    static final String RELEASE_PATH_PREFIX = "/SPxMM3R1/vibetube/releases/download/";
    static final String LATEST_RELEASE_URL = "https://api.github.com/repos/SPxMM3R1/vibetube/releases/latest";
    /** El APK del release se llama VibeTube-vX.Y.Z.apk. */
    static final String APK_PREFIX = "VibeTube-";
    static final String APK_SUFFIX = ".apk";
    /** Como máximo una revisión automática cada 6 horas (al abrir la app). */
    static final long CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L;

    private UpdateRules() {}

    /**
     * true si la versión candidata es mayor que la instalada. Compara parte por parte como números
     * («0.10.0» es mayor que «0.9.9»); lo que va después de un guion no cuenta.
     */
    static boolean isNewerVersion(String candidate, String current) {
        int[] candidateParts = numericParts(candidate);
        int[] currentParts = numericParts(current);
        int length = Math.max(candidateParts.length, currentParts.length);
        for (int index = 0; index < length; index++) {
            int candidatePart = index < candidateParts.length ? candidateParts[index] : 0;
            int currentPart = index < currentParts.length ? currentParts[index] : 0;
            if (candidatePart != currentPart) return candidatePart > currentPart;
        }
        return false;
    }

    /** Quita la «v» inicial y los espacios: «v0.4.1» queda «0.4.1». */
    static String normalizeVersion(String version) {
        if (version == null) return "";
        String normalized = version.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("v") ? normalized.substring(1) : normalized;
    }

    /** true si el nombre del archivo del release es el APK de VibeTube (VibeTube-…apk). */
    static boolean isApkAssetName(String name) {
        return name != null
                && name.startsWith(APK_PREFIX)
                && name.endsWith(APK_SUFFIX)
                && name.length() > APK_PREFIX.length() + APK_SUFFIX.length();
    }

    /**
     * true si la dirección es un APK del release de este repositorio en github.com por HTTPS.
     * Solo se aceptan esas descargas; GitHub puede redirigirlas después, y eso se sigue aparte.
     */
    static boolean isReleaseDownload(URI uri) {
        return uri != null
                && "https".equalsIgnoreCase(uri.getScheme())
                && "github.com".equalsIgnoreCase(uri.getHost())
                && uri.getPath() != null
                && uri.getPath().startsWith(RELEASE_PATH_PREFIX)
                && uri.getPath().endsWith(APK_SUFFIX);
    }

    /**
     * true si toca revisar: nunca se revisó, pasaron 6 horas desde el último intento o el reloj
     * retrocedió (una marca en el futuro no debe dejar la app sin revisar).
     */
    static boolean isCheckDue(long now, long lastAttemptAt) {
        if (lastAttemptAt <= 0) return true;
        long elapsed = now - lastAttemptAt;
        return elapsed < 0 || elapsed >= CHECK_INTERVAL_MS;
    }

    /** Partes numéricas de la versión estable: «0.4.1-rc1» queda [0, 4, 1]. */
    private static int[] numericParts(String version) {
        String stable = normalizeVersion(version).split("-", 2)[0];
        String[] pieces = stable.split("\\.");
        int[] result = new int[pieces.length];
        for (int index = 0; index < pieces.length; index++) {
            String digits = pieces[index].replaceAll("[^0-9].*$", "");
            if (digits.isEmpty()) return new int[]{0};
            try {
                result[index] = Integer.parseInt(digits);
            } catch (NumberFormatException ignored) {
                return new int[]{0};
            }
        }
        return result;
    }
}
