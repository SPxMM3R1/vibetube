package cl.vibe.tube;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Orden de fuentes de un directo, como en SmartTube: DASH generado (solo con hora del primer
 * segmento), DASH de YouTube por URL, HLS al final; y ninguna sin fuentes.
 */
public class LiveOrderTest {
    /** Hora del primer segmento del núcleo (cualquier valor mayor que 0). */
    private static final long START = 1_800_000_000_000L;

    @Test
    public void ordenCompletoEsDashGeneradoDashUrlYHls() {
        LiveOrder order = new LiveOrder(true, START, true, true);
        assertFalse(order.isEmpty());
        assertEquals(LiveOrder.Kind.DASH, order.current());
        assertTrue(order.advance());
        assertEquals(LiveOrder.Kind.DASH_URL, order.current());
        assertTrue(order.advance());
        assertEquals(LiveOrder.Kind.HLS, order.current());
    }

    @Test
    public void sinUnaFuenteNoHayOtraQueProbar() {
        LiveOrder order = new LiveOrder(true, START, true, true);
        order.advance();
        order.advance();
        assertFalse(order.advance());
        assertEquals(LiveOrder.Kind.HLS, order.current());
        // Otra llamada no pasa de la última.
        assertFalse(order.advance());
        assertEquals(LiveOrder.Kind.HLS, order.current());
    }

    @Test
    public void startTimeCeroSaltaElDashGeneradoYVaAUrlDeDash() {
        // Sin hora del primer segmento no hay DASH generado: lo primero es la URL de DASH de YouTube.
        LiveOrder order = new LiveOrder(true, 0, true, true);
        assertEquals(LiveOrder.Kind.DASH_URL, order.current());
        assertTrue(order.advance());
        assertEquals(LiveOrder.Kind.HLS, order.current());
        assertFalse(order.advance());
    }

    @Test
    public void startTimeCeroSinUrlDeDashVaAHls() {
        LiveOrder order = new LiveOrder(true, 0, false, true);
        assertFalse(order.isEmpty());
        assertEquals(LiveOrder.Kind.HLS, order.current());
        assertFalse(order.advance());
    }

    @Test
    public void startTimeCeroSinUrlDeDashNiHlsEstaVacio() {
        // Solo formatos DASH sin ancla ni URL: no hay nada que probar; el directo no se puede abrir.
        LiveOrder order = new LiveOrder(true, 0, false, false);
        assertTrue(order.isEmpty());
        assertFalse(order.advance());
    }

    @Test
    public void startTimeNegativoCuentaComoCero() {
        LiveOrder order = new LiveOrder(true, -1, true, false);
        assertEquals(LiveOrder.Kind.DASH_URL, order.current());
        assertFalse(order.advance());
    }

    @Test
    public void sinUrlDeDashProbaDashGeneradoYHls() {
        LiveOrder order = new LiveOrder(true, START, false, true);
        assertEquals(LiveOrder.Kind.DASH, order.current());
        assertTrue(order.advance());
        assertEquals(LiveOrder.Kind.HLS, order.current());
        assertFalse(order.advance());
    }

    @Test
    public void sinFormatosDashNoHayDashGenerado() {
        LiveOrder order = new LiveOrder(false, START, true, true);
        assertEquals(LiveOrder.Kind.DASH_URL, order.current());
        assertTrue(order.advance());
        assertEquals(LiveOrder.Kind.HLS, order.current());
    }

    @Test
    public void soloDashUrlEsLaUnicaFuente() {
        LiveOrder order = new LiveOrder(false, 0, true, false);
        assertFalse(order.isEmpty());
        assertEquals(LiveOrder.Kind.DASH_URL, order.current());
        assertFalse(order.advance());
    }

    @Test
    public void sinFuentesEstaVacio() {
        LiveOrder order = new LiveOrder(false, 0, false, false);
        assertTrue(order.isEmpty());
        assertFalse(order.advance());
    }

    @Test
    public void etiquetasParaElLog() {
        assertEquals("DASH", LiveOrder.Kind.DASH.label);
        assertEquals("DASH de YouTube", LiveOrder.Kind.DASH_URL.label);
        assertEquals("HLS", LiveOrder.Kind.HLS.label);
    }
}
