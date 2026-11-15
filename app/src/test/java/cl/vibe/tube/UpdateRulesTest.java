package cl.vibe.tube;

import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Comparación de versiones, nombre del APK, dirección de descarga y horario de revisión. */
public class UpdateRulesTest {

    private static final long HOUR = 60L * 60L * 1000L;

    @Test
    public void versionNuevaSoloSiEsMayor() {
        assertTrue(UpdateRules.isNewerVersion("0.4.2", "0.4.1"));
        assertFalse(UpdateRules.isNewerVersion("0.4.1", "0.4.1"));
        assertFalse(UpdateRules.isNewerVersion("0.4.0", "0.4.1"));
    }

    @Test
    public void lasPartesSeComparanComoNumeros() {
        assertTrue(UpdateRules.isNewerVersion("0.10.0", "0.9.9"));
        assertTrue(UpdateRules.isNewerVersion("1.0", "0.9.9"));
        assertTrue(UpdateRules.isNewerVersion("0.4.1.1", "0.4.1"));
        // Lo que va después de un guion no cambia la versión estable.
        assertFalse(UpdateRules.isNewerVersion("0.4.1-rc1", "0.4.1"));
    }

    @Test
    public void laVEnLaEtiquetaSeIgnora() {
        assertEquals("0.4.1", UpdateRules.normalizeVersion("v0.4.1"));
        assertEquals("0.4.1", UpdateRules.normalizeVersion("  V0.4.1 "));
        assertEquals("", UpdateRules.normalizeVersion(null));
        assertTrue(UpdateRules.isNewerVersion(UpdateRules.normalizeVersion("v0.5.0"), "0.4.9"));
    }

    @Test
    public void elAPKSeReconocePorPrefijoYExtension() {
        assertTrue(UpdateRules.isApkAssetName("VibeTube-v0.4.1.apk"));
        assertTrue(UpdateRules.isApkAssetName("VibeTube-v0.5.0.apk"));
        assertFalse(UpdateRules.isApkAssetName("VibeTube-v0.4.1.apk.sha256"));
        assertFalse(UpdateRules.isApkAssetName("VibeDLNA-v1.0.apk"));
        assertFalse(UpdateRules.isApkAssetName("vibetube-v0.4.1.apk"));
        assertFalse(UpdateRules.isApkAssetName("VibeTube-.apk"));
        assertFalse(UpdateRules.isApkAssetName(null));
    }

    @Test
    public void soloSeAceptanDescargasDelReleaseEnHttps() {
        String base = "https://github.com/SPxMM3R1/vibetube/releases/download/v0.4.1/";
        assertTrue(UpdateRules.isReleaseDownload(URI.create(base + "VibeTube-v0.4.1.apk")));
        assertFalse(UpdateRules.isReleaseDownload(URI.create(base.replace("https", "http") + "VibeTube-v0.4.1.apk")));
        assertFalse(UpdateRules.isReleaseDownload(URI.create("https://evil.example/SPxMM3R1/vibetube/releases/download/v0.4.1/VibeTube-v0.4.1.apk")));
        assertFalse(UpdateRules.isReleaseDownload(URI.create("https://github.com/otro/repo/releases/download/v0.4.1/VibeTube-v0.4.1.apk")));
        assertFalse(UpdateRules.isReleaseDownload(URI.create(base + "VibeTube-v0.4.1.zip")));
        assertFalse(UpdateRules.isReleaseDownload(null));
    }

    @Test
    public void laRevisionVenceCadaSeisHoras() {
        long last = 1_000_000_000_000L;
        assertTrue("nunca revisada", UpdateRules.isCheckDue(last, 0L));
        assertFalse("recién revisada", UpdateRules.isCheckDue(last + HOUR, last));
        assertFalse("a 5 h 59 min", UpdateRules.isCheckDue(last + 6 * HOUR - 60_000L, last));
        assertTrue("a las 6 h exactas", UpdateRules.isCheckDue(last + 6 * HOUR, last));
        assertTrue("el reloj retrocedió", UpdateRules.isCheckDue(last - HOUR, last));
    }
}
