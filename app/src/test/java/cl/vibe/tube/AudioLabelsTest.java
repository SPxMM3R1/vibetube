package cl.vibe.tube;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Rótulos de las pistas de audio: códec, idioma, bitrate y repetidos. */
public class AudioLabelsTest {

    @Test
    public void codecsConocidosTienenNombreLegible() {
        assertEquals("AAC", AudioLabels.codec("mp4a.40.2", null));
        assertEquals("Opus", AudioLabels.codec("opus", null));
        assertEquals("Dolby Digital", AudioLabels.codec("ac-3", null));
        assertEquals("Dolby Digital+", AudioLabels.codec("ec-3", null));
        assertEquals("AAC", AudioLabels.codec(null, "audio/mp4a-latm"));
        assertNull(AudioLabels.codec("xyz", "audio/desconocido"));
        assertNull(AudioLabels.codec(null, null));
    }

    @Test
    public void idiomaEnEspanolConMayuscula() {
        assertEquals("Español", AudioLabels.language("es"));
        assertEquals("Inglés", AudioLabels.language("en"));
        assertNull(AudioLabels.language("und"));
        assertNull(AudioLabels.language(null));
        assertNull(AudioLabels.language(""));
    }

    @Test
    public void rotuloConIdiomaCodecYBitrate() {
        assertEquals("Español · AAC · 128 kbps",
                AudioLabels.label("es", "mp4a.40.2", null, 128_000, false, false));
    }

    @Test
    public void sinIdiomaQuedaCodecYBitrate() {
        assertEquals("AAC · 128 kbps", AudioLabels.label(null, "mp4a.40.2", null, 128_000, false, false));
        assertEquals("Opus · 160 kbps", AudioLabels.label("und", "opus", null, 160_000, false, false));
    }

    @Test
    public void sinNadaQuedaPista() {
        assertEquals("Pista", AudioLabels.label(null, null, null, 0, false, false));
    }

    @Test
    public void originalYDoblajeAutomatico() {
        assertEquals("Original · Inglés · AAC · 128 kbps",
                AudioLabels.label("en", "mp4a.40.2", null, 128_000, true, false));
        assertEquals("Español · AAC · 128 kbps (doblaje automático)",
                AudioLabels.label("es", "mp4a.40.2", null, 128_000, false, true));
    }

    @Test
    public void repetidosLlevanDos() {
        assertEquals(Arrays.asList("AAC · 128 kbps", "AAC · 128 kbps · 2", "AAC · 128 kbps · 3", "Opus"),
                AudioLabels.unique(Arrays.asList("AAC · 128 kbps", "AAC · 128 kbps", "AAC · 128 kbps", "Opus")));
    }
}
