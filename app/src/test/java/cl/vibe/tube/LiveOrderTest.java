package cl.vibe.tube;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Orden de fuentes de un directo: DASH dinámico primero, HLS después, y ninguna sin fuentes. */
public class LiveOrderTest {

    @Test
    public void dashVaAntesQueHls() {
        LiveOrder order = new LiveOrder(true, true);
        assertFalse(order.isEmpty());
        assertEquals(LiveOrder.Kind.DASH, order.current());
        assertTrue(order.advance());
        assertEquals(LiveOrder.Kind.HLS, order.current());
    }

    @Test
    public void sinUnaFuenteNoHayOtraQueProbar() {
        LiveOrder order = new LiveOrder(true, true);
        order.advance();
        assertFalse(order.advance());
        assertEquals(LiveOrder.Kind.HLS, order.current());
        // Otra llamada no pasa de la última.
        assertFalse(order.advance());
        assertEquals(LiveOrder.Kind.HLS, order.current());
    }

    @Test
    public void soloDashNoProbaHls() {
        LiveOrder order = new LiveOrder(true, false);
        assertEquals(LiveOrder.Kind.DASH, order.current());
        assertFalse(order.advance());
    }

    @Test
    public void soloHlsEsLaUnicaFuente() {
        LiveOrder order = new LiveOrder(false, true);
        assertFalse(order.isEmpty());
        assertEquals(LiveOrder.Kind.HLS, order.current());
        assertFalse(order.advance());
    }

    @Test
    public void sinFuentesEstaVacio() {
        LiveOrder order = new LiveOrder(false, false);
        assertTrue(order.isEmpty());
        assertFalse(order.advance());
    }

    @Test
    public void etiquetasParaElLog() {
        assertEquals("DASH", LiveOrder.Kind.DASH.label);
        assertEquals("HLS", LiveOrder.Kind.HLS.label);
    }
}
