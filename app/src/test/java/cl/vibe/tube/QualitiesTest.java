package cl.vibe.tube;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/** Agrupación de calidades por pantalla y etiquetas de la franja de Calidad. */
public class QualitiesTest {

    private static Qualities.Variant v(int height, float fps, int bitrate) {
        int width = Math.round(height * 16 / 9f);
        return new Qualities.Variant(width, height, fps, bitrate, false, null);
    }

    @Test
    public void pantallasVanDeLaMayorALaMenor() {
        List<Qualities.Variant> all = new ArrayList<>(Arrays.asList(
                v(720, 30, 3_100_000), v(2160, 30, 18_400_000), v(1080, 30, 6_200_000)));
        List<Qualities.Screen> screens = Qualities.screens(all);
        assertEquals(3, screens.size());
        assertEquals(2160, screens.get(0).height);
        assertEquals(1080, screens.get(1).height);
        assertEquals(720, screens.get(2).height);
    }

    @Test
    public void laMejorVarianteDeUnaResolucionEsLaDeMayorBitrate() {
        List<Qualities.Variant> all = new ArrayList<>(Arrays.asList(
                v(1080, 30, 6_200_000), v(1080, 60, 9_000_000)));
        Qualities.Screen screen = Qualities.screens(all).get(0);
        assertEquals(9_000_000, screen.best().bitrate);
        assertEquals("1080p60", Qualities.label(screen.best()));
    }

    @Test
    public void etiquetaSinCuadrosExtraEnTreintaCuadros() {
        assertEquals("1080p", Qualities.label(v(1080, 30, 6_200_000)));
        assertEquals("2160p", Qualities.label(v(2160, 0, 18_400_000)));
        assertEquals("2160p", Qualities.height(2160));
    }

    @Test
    public void anchoProporcionalConMinimo() {
        assertEquals(200, Qualities.width(2160, 2160));
        assertEquals(100, Qualities.width(1080, 2160));
        assertEquals(Qualities.MIN_WIDTH, Qualities.width(144, 2160));
        assertEquals(Qualities.TOP_WIDTH, Qualities.width(720, 0));
    }

    @Test
    public void detalleConMegabits() {
        Qualities.Variant hd = v(1080, 30, 6_200_000);
        assertEquals("1920 × 1080 · 6.2 Mbps", Qualities.detail(new Qualities.Variant(
                1920, 1080, 30, 6_200_000, false, null)));
        assertEquals(hd.height, 1080);
    }

    @Test
    public void altoCeroNoEntraEnLasPantallas() {
        List<Qualities.Variant> all = new ArrayList<>(Arrays.asList(v(0, 0, 0), v(480, 30, 1_400_000)));
        List<Qualities.Screen> screens = Qualities.screens(all);
        assertEquals(1, screens.size());
        assertEquals(480, screens.get(0).height);
    }
}
