package cl.vibe.tube;

import android.view.KeyEvent;

/**
 * Evita el clic fantasma al abrir una pantalla con OK. Si una tarjeta abre el reproductor con el
 * DOWN de OK, el ACTION_UP de esa misma tecla llega a la pantalla nueva, que nunca recibió el DOWN;
 * sin este filtro, el control que tiene el foco (por ejemplo «Me gusta») se activaría solo.
 *
 * Cada actividad guarda una instancia y la consulta al comienzo de dispatchKeyEvent: si discard()
 * devuelve true, el evento se descarta. La bandera se pone en el DOWN de la tecla de selección
 * (DPAD_CENTER, ENTER o BUTTON_A) que llega a esta pantalla.
 */
final class ClickGuard {
    /** Tecla de selección cuyo DOWN recibió esta pantalla; -1 si ninguna. */
    private int pressed = -1;

    /** true si la tecla es de selección (OK, Enter o el botón A del control). */
    static boolean isSelect(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_BUTTON_A;
    }

    /**
     * Decide si el evento se descarta. Un UP de selección sin su DOWN en esta pantalla se descarta;
     * el resto pasa sin cambios.
     */
    boolean discard(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (!isSelect(keyCode)) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            pressed = keyCode;
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_UP) {
            boolean ours = pressed == keyCode;
            if (ours) pressed = -1;
            return !ours;
        }
        return false;
    }
}
