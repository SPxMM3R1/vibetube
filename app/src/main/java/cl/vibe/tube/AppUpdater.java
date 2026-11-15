package cl.vibe.tube;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import java.io.File;
import java.net.URI;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Actualizador de VibeTube. Al abrir la app revisa GitHub como máximo cada 6 horas y guarda el
 * resultado: el Inicio muestra el aviso («Hay una versión nueva… OK para instalar») y la pestaña
 * Cuenta la fila «Actualizaciones» con «Buscar ahora». Instalar descarga el APK del release
 * Latest, comprueba el paquete, la versión y la firma, y abre el instalador de Android.
 *
 * Una sola instancia por proceso. Todo el estado cambia en el hilo principal; los hilos de fondo
 * solo calculan y publican el resultado.
 */
final class AppUpdater {
    /** Lo llama el actualizador cuando cambia algo que se muestra (revisión, descarga, error). */
    interface Listener {
        void onUpdateChanged();
    }

    private static final String PREFS = "vibetube_update";
    private static final String KEY_ATTEMPT_AT = "attempt_at";
    private static final String KEY_CHECKED_AT = "checked_at";
    private static final String KEY_FAILED = "failed";
    private static final String KEY_TAG = "tag";
    private static final String KEY_VERSION = "version";
    private static final String KEY_URL = "url";
    private static final String KEY_SIZE = "size";

    private static AppUpdater instance;

    private final Context app;
    private final SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final UpdateRepository repository = new UpdateRepository();
    private final List<Listener> listeners = new ArrayList<>();

    /** Versión más nueva publicada según la última revisión; null si está al día. */
    private UpdateInfo update;
    /** Último intento de revisión (aunque fallara); manda sobre las 6 horas. */
    private long attemptAt;
    /** Última revisión que respondió. 0 si nunca. */
    private long checkedAt;
    private boolean checkFailed;
    private boolean checking;
    private boolean downloading;
    private int progress;
    /** APK descargado y verificado, listo para el instalador, y su versión. */
    private File pendingApk;
    private String pendingVersion;
    /** Falta permiso para instalar desde VibeTube: se espera a que el usuario vuelva de los ajustes. */
    private boolean awaitingPermission;
    /** Por qué no se pudo instalar la última vez (texto para el aviso); null si no hubo error. */
    private String installError;

    static synchronized AppUpdater get(Context context) {
        if (instance == null) instance = new AppUpdater(context.getApplicationContext());
        return instance;
    }

    private AppUpdater(Context app) {
        this.app = app;
        this.prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        attemptAt = prefs.getLong(KEY_ATTEMPT_AT, 0L);
        checkedAt = prefs.getLong(KEY_CHECKED_AT, 0L);
        checkFailed = prefs.getBoolean(KEY_FAILED, false);
        String tag = prefs.getString(KEY_TAG, null);
        String version = prefs.getString(KEY_VERSION, null);
        String url = prefs.getString(KEY_URL, null);
        if (tag != null && version != null && url != null) {
            try {
                update = new UpdateInfo(tag, version, URI.create(url), prefs.getLong(KEY_SIZE, 0L));
            } catch (IllegalArgumentException ignored) {
                // Un dato guardado que ya no se lee: se vuelve a revisar al abrir.
                update = null;
            }
        }
    }

    /** Versión instalada, p. ej. «0.4.1». */
    String currentVersion() {
        try {
            String name = app.getPackageManager().getPackageInfo(app.getPackageName(), 0).versionName;
            return name == null ? "0.0.0" : name;
        } catch (PackageManager.NameNotFoundException ignored) {
            return "0.0.0";
        }
    }

    /** La versión nueva a ofrecer, o null si está al día (o ya quedó instalada). */
    UpdateInfo available() {
        if (update == null || !UpdateRules.isNewerVersion(update.getVersionName(), currentVersion())) {
            return null;
        }
        return update;
    }

    boolean isChecking() {
        return checking;
    }

    boolean isDownloading() {
        return downloading;
    }

    /** Porcentaje de la descarga en curso (0 a 100). */
    int getProgress() {
        return progress;
    }

    /** Hora de la última revisión que respondió; 0 si nunca. */
    long getCheckedAt() {
        return checkedAt;
    }

    boolean isCheckFailed() {
        return checkFailed;
    }

    /** Motivo del último error de instalación, o null. */
    String getInstallError() {
        return installError;
    }

    void addListener(Listener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    /** Al abrir la app: revisa si pasaron 6 horas desde el último intento. */
    void checkIfDue() {
        if (UpdateRules.isCheckDue(System.currentTimeMillis(), attemptAt)) checkNow();
    }

    /** Revisa ahora (el botón «Buscar ahora»). No hace nada si ya se está revisando o descargando. */
    void checkNow() {
        if (checking || downloading) return;
        checking = true;
        notifyListeners();
        executor.submit(() -> {
            long now = System.currentTimeMillis();
            try {
                UpdateInfo found = repository.findAvailableUpdate(currentVersion());
                main.post(() -> finishCheck(found, now, false));
            } catch (Exception error) {
                main.post(() -> finishCheck(null, now, true));
            }
        });
    }

    /**
     * Instalar (OK en el aviso): descarga el APK si hace falta, lo verifica y abre el instalador.
     * Si ya estaba descargado, solo abre el instalador.
     */
    void install() {
        UpdateInfo target = available();
        if (target == null || downloading || checking) return;
        installError = null;
        // Un APK descargado sirve solo si es de la misma versión que la que se ofrece ahora.
        if (pendingApk != null && pendingApk.isFile() && target.getVersionName().equals(pendingVersion)) {
            launchInstaller();
            return;
        }
        pendingApk = null;
        pendingVersion = null;
        downloading = true;
        progress = 0;
        notifyListeners();
        executor.submit(() -> {
            try {
                File apk = repository.download(target, app.getCacheDir(), percent ->
                        main.post(() -> {
                            progress = percent;
                            notifyListeners();
                        }));
                verify(apk);
                main.post(() -> {
                    downloading = false;
                    pendingApk = apk;
                    pendingVersion = target.getVersionName();
                    launchInstaller();
                });
            } catch (Exception error) {
                String message = shortMessage(error);
                main.post(() -> {
                    downloading = false;
                    installError = message;
                    notifyListeners();
                });
            }
        });
    }

    /**
     * Al volver a una pantalla de la app: si el usuario acaba de permitir instalar desde VibeTube,
     * sigue con la instalación; si no lo permitió, lo dice en el aviso.
     */
    void onResume() {
        if (!awaitingPermission || pendingApk == null) return;
        if (canInstallPackages()) {
            launchInstaller();
        } else {
            installError = "Falta permitir instalar apps desde VibeTube.";
            notifyListeners();
        }
    }

    private void finishCheck(UpdateInfo found, long now, boolean failed) {
        checking = false;
        attemptAt = now;
        checkFailed = failed;
        SharedPreferences.Editor editor = prefs.edit()
                .putLong(KEY_ATTEMPT_AT, now)
                .putBoolean(KEY_FAILED, failed);
        if (!failed) {
            update = found;
            checkedAt = now;
            editor.putLong(KEY_CHECKED_AT, now);
            if (found != null) {
                editor.putString(KEY_TAG, found.getTagName())
                        .putString(KEY_VERSION, found.getVersionName())
                        .putString(KEY_URL, found.getDownloadUri().toString())
                        .putLong(KEY_SIZE, found.getSizeBytes());
            } else {
                editor.remove(KEY_TAG).remove(KEY_VERSION).remove(KEY_URL).remove(KEY_SIZE);
            }
        }
        editor.apply();
        notifyListeners();
    }

    /** Abre el instalador con el APK verificado; sin permiso, lleva a los ajustes y espera. */
    private void launchInstaller() {
        if (pendingApk == null || !pendingApk.isFile()) {
            pendingApk = null;
            notifyListeners();
            return;
        }
        if (!canInstallPackages()) {
            awaitingPermission = true;
            Intent settings = new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + app.getPackageName()));
            settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                app.startActivity(settings);
                installError = null;
            } catch (ActivityNotFoundException error) {
                awaitingPermission = false;
                installError = "Este equipo no permite instalar apps desde aquí.";
            }
            notifyListeners();
            return;
        }

        awaitingPermission = false;
        try {
            Uri uri = FileProvider.getUriForFile(app, app.getPackageName() + ".fileprovider", pendingApk);
            Intent install = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            install.setData(uri);
            install.setClipData(ClipData.newRawUri("VibeTube", uri));
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            if (install.resolveActivity(app.getPackageManager()) == null) {
                throw new IllegalStateException("No hay un instalador de APK disponible.");
            }
            app.startActivity(install);
            installError = null;
        } catch (Exception error) {
            installError = shortMessage(error);
        }
        notifyListeners();
    }

    private boolean canInstallPackages() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || app.getPackageManager().canRequestPackageInstalls();
    }

    /**
     * Comprueba que el APK sea de VibeTube, más nuevo que el instalado y firmado con la misma
     * llave. Si algo no coincide, lanza error y el APK no se instala.
     */
    private void verify(File apk) throws Exception {
        PackageManager packageManager = app.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? PackageManager.GET_SIGNING_CERTIFICATES
                : PackageManager.GET_SIGNATURES;

        PackageInfo candidate = packageManager.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        PackageInfo installed = packageManager.getPackageInfo(app.getPackageName(), flags);
        if (candidate == null || !app.getPackageName().equals(candidate.packageName)) {
            throw new SecurityException("El APK no pertenece a VibeTube.");
        }

        long candidateVersion = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? candidate.getLongVersionCode()
                : candidate.versionCode;
        long installedVersion = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? installed.getLongVersionCode()
                : installed.versionCode;
        if (candidateVersion <= installedVersion) {
            throw new SecurityException("El APK no es una versión más reciente.");
        }

        Set<String> installedSigners = signerDigests(installed);
        Set<String> candidateSigners = signerDigests(candidate);
        if (installedSigners.isEmpty() || !installedSigners.equals(candidateSigners)) {
            throw new SecurityException("La firma del APK no coincide con la instalada.");
        }
    }

    private static Set<String> signerDigests(PackageInfo packageInfo) throws Exception {
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (packageInfo.signingInfo == null) return new HashSet<>();
            signatures = packageInfo.signingInfo.getApkContentsSigners();
        } else {
            signatures = packageInfo.signatures;
        }

        Set<String> result = new HashSet<>();
        if (signatures == null) return result;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (Signature signature : signatures) {
            result.add(toHex(digest.digest(signature.toByteArray())));
        }
        return result;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format("%02x", value & 0xff));
        return result.toString();
    }

    private void notifyListeners() {
        for (Listener listener : new ArrayList<>(listeners)) listener.onUpdateChanged();
    }

    private static String shortMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.trim().isEmpty() ? "error desconocido" : message;
    }
}
