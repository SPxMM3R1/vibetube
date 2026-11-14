package cl.vibe.videos;

import android.app.Application;

import com.liskovsoft.sharedutils.prefs.GlobalPreferences;

/** El núcleo de SmartTube guarda su estado (visitante, cuenta) en GlobalPreferences. */
public class VideosApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        GlobalPreferences.instance(this);
    }
}
