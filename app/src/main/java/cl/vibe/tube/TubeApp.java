package cl.vibe.tube;

import android.app.Application;

import com.liskovsoft.googlecommon.common.locale.LocaleManager;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;

/** El núcleo de SmartTube guarda su estado (visitante, cuenta) en GlobalPreferences. */
public class TubeApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        GlobalPreferences.instance(this);
        Hidden.init(this);
        // Idioma y región fijos antes de cualquier pedido: el núcleo los lee de LocaleManager
        // (sin esto las filas llegan en en-US). Se fijan aquí y no según el idioma del equipo.
        LocaleManager.instance().setLanguage("es");
        LocaleManager.instance().setCountry("CL");
    }
}
