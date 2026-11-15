package cl.vibe.tube;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Manifiestos DASH: de «dynamic» a «static» (videos) y de «dynamic» del núcleo a directo (toLive). */
public class ManifestsTest {

    /** 2027-01-15T08:00:00 UTC, en milisegundos. */
    private static final long NOW = 1_800_000_000_000L;
    /** Hora del primer segmento del núcleo: 100 s antes de «ahora» (07:58:20 UTC). */
    private static final long START = NOW - 100_000L;

    /** MPD como lo arma YouTubeMPDBuilder para un directo: una representación, 5 s por segmento, r = 10. */
    private static final String LIVE_MPD = "<MPD xmlns=\"urn:mpeg:DASH:schema:MPD:2011\" profiles=\"urn:mpeg:dash:profile:isoff-on-demand:2011\""
            + " type=\"dynamic\" minimumUpdatePeriod=\"P100Y\" minBufferTime=\"PT1.500S\">"
            + "<Period start=\"PT0S\"><AdaptationSet><Representation id=\"137\" bandwidth=\"1000\">"
            + "<SegmentTemplate duration=\"5000000\" timescale=\"1000000\" media=\"https://example.com/v?sq=$Number$\""
            + " startNumber=\"100\" presentationTimeOffset=\"500000000\">"
            + "<SegmentTimeline><S t=\"500000000\" d=\"5000000\" r=\"10\"/></SegmentTimeline>"
            + "</SegmentTemplate></Representation></AdaptationSet></Period></MPD>";

    @Test
    public void dinamicoPasaAEstatico() {
        String mpd = "<MPD type=\"dynamic\" minimumUpdatePeriod=\"PT5S\"></MPD>";
        String out = Manifests.toStatic(mpd);
        assertTrue(out.contains("type=\"static\""));
        assertEquals(false, out.contains("type=\"dynamic\""));
    }

    @Test
    public void estaticoQuedaIgual() {
        String mpd = "<MPD type=\"static\"></MPD>";
        assertEquals(mpd, Manifests.toStatic(mpd));
    }

    @Test
    public void directoQuitaLaLineaDeTiempo() {
        String out = Manifests.toLive(LIVE_MPD, NOW, START);
        assertFalse(out.contains("SegmentTimeline"));
        assertFalse(out.contains("<S "));
        // El SegmentTemplate conserva lo que Media3 necesita para numerar por tiempo.
        assertTrue(out.contains("duration=\"5000000\""));
        assertTrue(out.contains("timescale=\"1000000\""));
        assertTrue(out.contains("startNumber=\"100\""));
        assertTrue(out.contains("presentationTimeOffset=\"500000000\""));
        assertTrue(out.contains("media=\"https://example.com/v?sq=$Number$\""));
    }

    @Test
    public void directoConInicioDelNucleoAnclaAlPrimerSegmentoConMargen() {
        // El primer segmento es START (07:58:20); con 3 segmentos de 5 s de margen, la disponibilidad
        // empieza 15 s después: 07:58:35.
        String out = Manifests.toLive(LIVE_MPD, NOW, START);
        assertTrue(out.contains("availabilityStartTime=\"2027-01-15T07:58:35.000Z\""));
        assertTrue(out.contains("timeShiftBufferDepth=\"PT2H\""));
        assertTrue(out.contains("suggestedPresentationDelay=\"PT20S\""));
    }

    @Test
    public void directoConInicioNoDependeDelReloj() {
        // Con el inicio del núcleo, la fecha no cambia aunque «ahora» avance: el ancla es el primer segmento.
        String out = Manifests.toLive(LIVE_MPD, NOW + 3_600_000L, START);
        assertTrue(out.contains("availabilityStartTime=\"2027-01-15T07:58:35.000Z\""));
    }

    // Los casos de respaldo (sin hora del primer segmento) son solo tolerancia: la app no pide un
    // directo sin startTimeMs > 0 (LiveOrder), así que estas pruebas cuidan que la función no se rompa.

    @Test
    public void respaldoSinInicioAgregaInicioDeDisponibilidadTresSegmentosAntesDelFinal() {
        // Sin inicio del núcleo: r = 10, 11 segmentos menos 3 de margen = 8 de 5 s = 40 s antes de «ahora».
        String out = Manifests.toLive(LIVE_MPD, NOW, 0);
        assertTrue(out.contains("availabilityStartTime=\"2027-01-15T07:59:20.000Z\""));
        assertTrue(out.contains("timeShiftBufferDepth=\"PT2H\""));
        assertTrue(out.contains("suggestedPresentationDelay=\"PT20S\""));
    }

    @Test
    public void respaldoConInicioNegativoEsIgualQueSinInicio() {
        String out = Manifests.toLive(LIVE_MPD, NOW, -1);
        assertTrue(out.contains("availabilityStartTime=\"2027-01-15T07:59:20.000Z\""));
    }

    @Test
    public void respaldoConLineaCortaDejaUnSegmentoDisponible() {
        // r = 0: 1 segmento menos el margen daría cero o menos; se deja uno (5 s antes de «ahora»).
        String out = Manifests.toLive(LIVE_MPD.replace("r=\"10\"", "r=\"0\""), NOW, 0);
        assertTrue(out.contains("availabilityStartTime=\"2027-01-15T07:59:55.000Z\""));
    }

    @Test
    public void respaldoTopeDeVentanaDe24Horas() {
        // r = 20000 daría 20001 segmentos; 24 h de 5 s son 17280. Menos 3 de margen: 17277 × 5 s
        // = 86385 s antes de «ahora», es decir, 15 s después de las 08:00 del día anterior.
        String out = Manifests.toLive(LIVE_MPD.replace("r=\"10\"", "r=\"20000\""), NOW, 0);
        assertTrue(out.contains("availabilityStartTime=\"2027-01-14T08:00:15.000Z\""));
    }

    @Test
    public void directoSigueSiendoXmlValido() throws Exception {
        String out = Manifests.toLive(LIVE_MPD, NOW, START);
        DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(out.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void directoReemplazaUnaFechaQueYaTraiga() {
        String mpd = LIVE_MPD.replace("<MPD ", "<MPD availabilityStartTime=\"2000-01-01T00:00:00.000Z\" ");
        String out = Manifests.toLive(mpd, NOW, START);
        assertEquals(1, out.split("availabilityStartTime=", -1).length - 1);
        assertTrue(out.contains("availabilityStartTime=\"2027-01-15T07:58:35.000Z\""));
    }

    @Test
    public void availabilityStartSinSegmentoEsNulo() {
        assertNull(Manifests.availabilityStart("<MPD type=\"dynamic\"><Period></Period></MPD>", NOW, START));
    }

    @Test
    public void sinSegmentoSeDevuelveIgual() {
        String mpd = "<MPD type=\"dynamic\"><Period></Period></MPD>";
        assertEquals(mpd, Manifests.toLive(mpd, NOW, START));
    }
}
