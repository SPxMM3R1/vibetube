package cl.vibe.tube;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Canales que no se muestran en esta TV («Ocultar en esta TV»): se quitan de las filas y de las
 * búsquedas. Es una lista local del equipo; no se sincroniza con la cuenta.
 */
final class Hidden {
    private static final String PREFS = "vibetube";
    private static final String KEY = "canales_ocultos";
    private static Context app;
    /** Sube cada vez que cambia la lista: Inicio se vuelve a armar al volver si cambió. */
    private static int version;

    private Hidden() {}

    static void init(Context context) {
        app = context.getApplicationContext();
    }

    static int version() {
        return version;
    }

    static boolean isHidden(String channelId) {
        return channelId != null && !channelId.isEmpty() && all().contains(channelId);
    }

    /** Alterna el canal y devuelve true si quedó oculto. */
    static boolean toggle(String channelId) {
        Set<String> channels = all();
        boolean hide = !channels.contains(channelId);
        if (hide) channels.add(channelId);
        else channels.remove(channelId);
        prefs().edit().putStringSet(KEY, channels).apply();
        version++;
        return hide;
    }

    private static Set<String> all() {
        if (app == null) return new HashSet<>();
        Set<String> saved = prefs().getStringSet(KEY, Collections.<String>emptySet());
        return saved == null ? new HashSet<String>() : new HashSet<>(saved);
    }

    private static SharedPreferences prefs() {
        return app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
