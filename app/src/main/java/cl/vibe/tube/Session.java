package cl.vibe.tube;

import com.liskovsoft.googlecommon.common.models.auth.AccessToken;
import com.liskovsoft.googlecommon.common.models.auth.UserCode;
import com.liskovsoft.googlecommon.common.models.auth.info.AccountInt;
import com.liskovsoft.googlecommon.service.oauth.YouTubeAccount;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.youtubeapi.auth.V2.AuthService;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;
import com.liskovsoft.youtubeapi.service.YouTubeSignInService;
import com.liskovsoft.youtubeapi.service.internal.YouTubeAccountManager;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.Observable;

/**
 * Cuenta de Google de la TV: inicio por código de dispositivo, varias cuentas guardadas y cierre
 * de sesión. El núcleo guarda las cuentas en GlobalPreferences; aquí se usan sus piezas públicas.
 *
 * Los métodos marcados «bloquea» hacen red o esperan la confirmación: llamarlos desde un hilo de fondo.
 */
final class Session {
    private Session() {}

    /** Si hay una cuenta elegida. */
    static boolean isSigned() {
        return YouTubeServiceManager.instance().getSignInService().isSigned();
    }

    /**
     * El núcleo no expone isSignedObserve() para YouTube: se envuelve isSigned() en un Observable
     * para que la pantalla de Cuenta lo consulte igual que las demás consultas.
     */
    static Observable<Boolean> isSignedObserve() {
        return Observable.fromCallable(Session::isSigned);
    }

    /** Cuentas guardadas en esta TV (copia). */
    static List<Account> accounts() {
        List<Account> stored = YouTubeSignInService.instance().getAccounts();
        return stored == null ? new ArrayList<Account>() : new ArrayList<>(stored);
    }

    /** Cuenta elegida, o null. */
    static Account selected() {
        return YouTubeSignInService.instance().getSelectedAccount();
    }

    /** Nombre y correo de una cuenta, como «Rocío · correo@ejemplo». */
    static String label(Account account) {
        String name = account.getName() == null ? "Cuenta" : account.getName();
        String email = account.getEmail();
        return email == null || email.isEmpty() ? name : name + " · " + email;
    }

    /** Pide el código de dispositivo: la dirección y el código que se muestran en la TV. Bloquea. */
    static UserCode requestCode() {
        UserCode code = AuthService.instance().getUserCode();
        if (code == null || code.getDeviceCode() == null) {
            throw new IllegalStateException("El servicio no entregó un código");
        }
        return code;
    }

    /**
     * Espera a que el usuario confirme el código en su teléfono y guarda la cuenta. Bloquea: el
     * núcleo espera hasta unos 10 minutos. Si se interrumpe, lanza InterruptedException.
     */
    static void waitForConfirmation(UserCode code) throws InterruptedException {
        AccessToken token = AuthService.instance().getAccessTokenWait(code.getDeviceCode());
        addAccount(token.getRefreshToken());
    }

    /**
     * Guarda una cuenta nueva junto a las que ya están, con la misma lógica que el núcleo
     * (persistRefreshToken, que es privado): primero una cuenta provisional con solo el token para
     * pedir la lista de cuentas; después se guardan todas y se elige la primera nueva. Bloquea.
     */
    private static void addAccount(String refreshToken) {
        YouTubeSignInService sign = YouTubeSignInService.instance();
        YouTubeAccountManager manager = YouTubeAccountManager.instance(sign);
        List<Account> stored = accounts();

        // 1) Cuenta provisional, seleccionada, para que las peticiones lleven el token.
        List<Account> provisional = new ArrayList<>();
        for (Account account : stored) {
            ((YouTubeAccount) account).setSelected(false);
            provisional.add(account);
        }
        provisional.add(YouTubeAccount.fromToken(refreshToken));
        save(provisional);
        manager.init();
        sign.invalidateCache();
        sign.checkAuth();

        // 2) Cuentas de este inicio de sesión (incluye las marcas de la cuenta de marca, si las hay).
        List<AccountInt> fetched = AuthService.instance().getAccounts();
        List<Account> result = new ArrayList<>(stored);
        YouTubeAccount chosen = null;
        if (fetched != null) {
            for (AccountInt info : fetched) {
                YouTubeAccount account = YouTubeAccount.from(info);
                account.setRefreshToken(refreshToken);
                int at = indexOf(result, account);
                if (at >= 0) {
                    account = (YouTubeAccount) result.get(at);
                    account.setRefreshToken(refreshToken);
                } else {
                    result.add(account);
                }
                if (chosen == null) chosen = account;
            }
        }
        if (chosen == null) {
            chosen = YouTubeAccount.fromToken(refreshToken);
            result.add(chosen);
        }

        // 3) Solo la elegida queda seleccionada; se guarda, se restaura y se refresca el token.
        for (Account account : result) {
            ((YouTubeAccount) account).setSelected(account == chosen);
        }
        save(result);
        manager.init();
        sign.invalidateCache();
        sign.checkAuth();
        YouTubeServiceManager.instance().invalidateCache();
    }

    /** Elige otra cuenta guardada. Bloquea (refresca el token). */
    static void switchTo(Account account) {
        YouTubeSignInService sign = YouTubeSignInService.instance();
        sign.selectAccount(account);
        sign.checkAuth();
        YouTubeServiceManager.instance().invalidateCache();
    }

    /**
     * Cierra la sesión de la cuenta elegida. Si hay otra guardada, se elige; si no, la TV queda sin
     * cuenta. Bloquea.
     */
    static void signOut(Account account) {
        YouTubeSignInService sign = YouTubeSignInService.instance();
        sign.removeAccount(account);
        List<Account> rest = accounts();
        if (!rest.isEmpty() && selected() == null) sign.selectAccount(rest.get(0));
        sign.checkAuth();
        YouTubeServiceManager.instance().invalidateCache();
    }

    /** Un ítem mínimo con el id del video: el núcleo solo lee el id para «Me gusta». */
    static MediaItem item(final String videoId) {
        return (MediaItem) Proxy.newProxyInstance(MediaItem.class.getClassLoader(),
                new Class<?>[]{MediaItem.class},
                (proxy, method, args) -> {
                    if ("getVideoId".equals(method.getName())) return videoId;
                    Class<?> type = method.getReturnType();
                    if (type == boolean.class) return false;
                    if (type == int.class) return 0;
                    if (type == long.class) return 0L;
                    return null;
                });
    }

    private static int indexOf(List<Account> accounts, Account account) {
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).equals(account)) return i;
        }
        return -1;
    }

    /** Guarda la lista de cuentas en las preferencias del núcleo (el mismo formato que usa él). */
    private static void save(List<Account> accounts) {
        GlobalPreferences.sInstance.setMediaServiceAccountData(Helpers.mergeArray(accounts.toArray()));
    }
}
