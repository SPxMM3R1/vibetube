package cl.vibe.tube;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.liskovsoft.mediaserviceinterfaces.ContentService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * Inicio, Suscripciones, En vivo, Biblioteca y Buscar con el lenguaje «Flotante minimalista» de
 * VibeM3U: el video marcado de fondo, texto que flota sobre una sola capa de degradado, foco =
 * blanco en negrita con la línea que viaja. Medidas del mockup «cliente-videos» (versión 1).
 */
public class MainActivity extends Activity implements Tiles.Actions {
    static final String TAG = "VibeTube";
    static final PathInterpolator SPRING = Ui.SPRING;
    static final int LIVE = Ui.LIVE;
    static final int NOW = Ui.NOW;

    static final int TAB_HOME = 0;
    static final int TAB_SUBS = 1;
    static final int TAB_LIVE = 2;
    static final int TAB_LIBRARY = 3;
    static final int TAB_SEARCH = 4;
    static final int TAB_ACCOUNT = 5;
    private static final String[] TAB_NAMES = {"Inicio", "Suscripciones", "En vivo", "Biblioteca", "Buscar", "Cuenta"};
    /** Inicio, Suscripciones, En vivo y Biblioteca comparten la forma: detalle arriba y filas abajo. */
    private static final int ROWS_TOP = 340;
    /**
     * Zona de filas: dos filas de 187 y 8 de separación (382) caben de sobra. Un poco más alta que
     * la pantalla para que enfocar una tarjeta levantada no desplace la columna (y se coma el Arriba).
     */
    private static final int ROWS_HEIGHT = 420;
    private static final int DESCRIPTION_DELAY_MS = 350;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, String> descriptions = new HashMap<>();
    private final TextView[] tabs = new TextView[TAB_NAMES.length];
    private final RowsView[] sections = new RowsView[4];
    private final LinearLayout[] prompts = new LinearLayout[4];
    private final TextView[] promptActions = new TextView[4];
    private final boolean[] loaded = new boolean[TAB_NAMES.length];
    private FrameLayout root;
    private FrameLayout content;
    private ImageView hero;
    private View shadeLeft;
    private View shadeBottom;
    private LinearLayout detail;
    private TextView detailEyebrow;
    private TextView detailTitle;
    private TextView detailDesc;
    private TextView detailMeta;
    private TextView offline;
    private TextView clock;
    private SearchView search;
    private Focus focus;
    /** Aviso de versión nueva: una línea en el Inicio, con OK para instalar. */
    private TextView updateBanner;
    private AppUpdater updater;
    private final AppUpdater.Listener updateListener = this::refreshUpdate;
    private Disposable metaRequest;
    private MediaItem current;
    /** Si la cuenta estaba iniciada al armar la pantalla; si cambia, Inicio se vuelve a armar. */
    private boolean signed;
    private int hiddenVersion;
    private int tab = -1;

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            Date now = new Date();
            String day = new SimpleDateFormat("EEEE d", new Locale("es", "CL")).format(now);
            String time = new SimpleDateFormat("HH:mm", Locale.US).format(now);
            clock.setText(capitalize(day) + " · " + time);
            clock.postDelayed(this, 20_000);
        }
    };

    private final Runnable descriptionLater = () -> {
        if (current != null) loadDescription(current);
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        signed = Session.isSigned();
        hiddenVersion = Hidden.version();
        updater = AppUpdater.get(this);
        root = buildRoot();
        setContentView(root);
        focus = new Focus(root);
        root.getViewTreeObserver().addOnGlobalFocusChangeListener(focus);
        clockTick.run();
        select(TAB_HOME);
        // Al abrir la app: revisa la versión nueva como máximo cada 6 horas.
        updater.checkIfDue();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updater.addListener(updateListener);
        updater.onResume();
        refreshUpdate();
        // Si se ocultó o mostró un canal en la página de Canal, las filas ya cargadas no lo saben: se rehacen.
        // Lo mismo si la cuenta cambió en la pantalla de Cuenta: Inicio vuelve a armarse, ya con sesión.
        if (Hidden.version() != hiddenVersion || Session.isSigned() != signed) {
            recreate();
            return;
        }
        checkNetwork();
    }

    @Override
    protected void onPause() {
        updater.removeListener(updateListener);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        disposables.clear();
        handler.removeCallbacksAndMessages(null);
        clock.removeCallbacks(clockTick);
        super.onDestroy();
    }

    private FrameLayout buildRoot() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        hero = new ImageView(this);
        hero.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(hero, new FrameLayout.LayoutParams(-1, -1));
        // Una sola capa de velos: desde la izquierda (detalle) y desde abajo (filas).
        shadeLeft = new View(this);
        shadeLeft.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xD9000000, 0x8C000000, 0x00000000}));
        root.addView(shadeLeft, new FrameLayout.LayoutParams(-1, -1));
        shadeBottom = new View(this);
        shadeBottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xF2000000, 0xB3000000, 0x00000000}));
        root.addView(shadeBottom, new FrameLayout.LayoutParams(-1, Ui.px(430), Gravity.BOTTOM));

        content = new FrameLayout(this);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        for (int i = 0; i < sections.length; i++) {
            sections[i] = new RowsView(this, this, disposables, ROWS_TOP, ROWS_HEIGHT);
            content.addView(sections[i], new FrameLayout.LayoutParams(-1, -1));
        }
        for (int i : new int[]{TAB_SUBS, TAB_LIBRARY}) {
            prompts[i] = signPrompt(i);
            FrameLayout.LayoutParams promptParams = new FrameLayout.LayoutParams(Ui.px(640), -2, Gravity.TOP | Gravity.START);
            promptParams.setMargins(Ui.px(64), Ui.px(136), 0, 0);
            content.addView(prompts[i], promptParams);
        }
        search = new SearchView(this, this, disposables);
        content.addView(search, new FrameLayout.LayoutParams(-1, -1));

        // Barra superior: eyebrow, pestañas y reloj, a la altura del mockup (40).
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(Ui.eyebrow(this, "VibeTube", Ui.EYEBROW));
        for (int i = 0; i < TAB_NAMES.length; i++) {
            final int which = i;
            TextView view = Ui.option(this, Focus.LINE, TAB_NAMES[i], 16);
            // «Cuenta» no es una pantalla de filas: abre la pantalla de Cuenta.
            view.setOnClickListener(v -> {
                if (which == TAB_ACCOUNT) openAccount();
                else select(which);
            });
            view.setId(View.generateViewId());
            tabs[i] = view;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
            params.setMarginStart(Ui.px(34));
            top.addView(view, params);
        }
        // «Iniciar sesión» queda debajo de la pestaña: Arriba debe llegar a ella aunque no se solapen en x.
        for (int i : new int[]{TAB_SUBS, TAB_LIBRARY}) promptActions[i].setNextFocusUpId(tabs[i].getId());
        View spacer = new View(this);
        top.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        clock = Ui.text(this, 15, Ui.MUTED, false);
        top.addView(clock);
        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP | Gravity.START);
        topParams.setMargins(Ui.px(64), Ui.px(40), Ui.px(64), 0);
        root.addView(top, topParams);

        // Detalle del video marcado: eyebrow, título, descripción y línea tenue.
        detail = new LinearLayout(this);
        detail.setOrientation(LinearLayout.VERTICAL);
        detailEyebrow = Ui.eyebrow(this, "", Ui.EYEBROW);
        detail.addView(detailEyebrow, new LinearLayout.LayoutParams(-1, -2));
        detailTitle = Ui.text(this, 40, Ui.WHITE, true);
        detailTitle.setLetterSpacing(-0.015f);
        detailTitle.setLineSpacing(0f, 0.92f);
        detailTitle.setMaxLines(2);
        detailTitle.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = Ui.px(12);
        detail.addView(detailTitle, titleParams);
        detailDesc = Ui.text(this, 16, Ui.MUTED, false);
        detailDesc.setMaxLines(2);
        detailDesc.setLineSpacing(0f, 1.25f);
        detailDesc.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(-1, -2);
        descParams.topMargin = Ui.px(10);
        detail.addView(detailDesc, descParams);
        detailMeta = Ui.text(this, 13, Ui.DIM, false);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(-2, -2);
        metaParams.topMargin = Ui.px(8);
        detail.addView(detailMeta, metaParams);
        FrameLayout.LayoutParams detailParams = new FrameLayout.LayoutParams(Ui.px(640), -2, Gravity.TOP | Gravity.START);
        detailParams.setMargins(Ui.px(64), Ui.px(136), 0, 0);
        root.addView(detail, detailParams);

        offline = Ui.text(this, 13, Ui.MUTED, false);
        offline.setText("Sin conexión a internet. Lo que ya cargó sigue aquí.");
        offline.setVisibility(View.GONE);
        FrameLayout.LayoutParams offlineParams = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        offlineParams.setMargins(Ui.px(64), Ui.px(96), 0, 0);
        root.addView(offline, offlineParams);

        // Aviso de versión nueva: una línea bajo la barra, enfocable; OK descarga e instala.
        updateBanner = Ui.option(this, Focus.LINE, "", 15);
        updateBanner.setVisibility(View.GONE);
        updateBanner.setOnClickListener(v -> updater.install());
        FrameLayout.LayoutParams bannerParams = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        bannerParams.setMargins(Ui.px(64), Ui.px(96), 0, 0);
        root.addView(updateBanner, bannerParams);
        return root;
    }

    /**
     * Muestra el aviso solo en el Inicio y solo si hay una versión nueva. Durante la descarga
     * dice el porcentaje; si la instalación falló, lo dice y ofrece reintentar.
     */
    private void refreshUpdate() {
        UpdateInfo next = updater.available();
        if (next == null || tab != TAB_HOME) {
            updateBanner.setVisibility(View.GONE);
            return;
        }
        String version = next.getVersionName();
        String message;
        if (updater.isDownloading()) {
            message = "Descargando " + version + " · " + updater.getProgress() + " %";
        } else if (updater.getInstallError() != null) {
            message = "No se pudo instalar " + version + ": " + updater.getInstallError() + " · OK para reintentar";
        } else {
            message = "Hay una versión nueva · " + version + " · OK para instalar";
        }
        // El punto cian va antes del texto, como en el aviso del mockup.
        SpannableStringBuilder line = new SpannableStringBuilder("●  " + message);
        line.setSpan(new ForegroundColorSpan(NOW), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        updateBanner.setText(line);
        updateBanner.setVisibility(View.VISIBLE);
    }

    /** Sin cuenta, Suscripciones y Biblioteca solo dicen cómo entrar y ofrecen la acción. */
    private LinearLayout signPrompt(int which) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        String message = which == TAB_SUBS
                ? "Inicia sesión para ver tus suscripciones."
                : "Inicia sesión para ver tu biblioteca.";
        TextView text = Ui.text(this, 16, Ui.MUTED, false);
        text.setText(message);
        box.addView(text);
        TextView action = Ui.option(this, Focus.LINE, "Iniciar sesión", 16);
        // Crece hacia la derecha desde su borde izquierdo: no se corta por la izquierda del aviso.
        action.setTag(R.id.anchor_left, Boolean.TRUE);
        action.setOnClickListener(v -> openAccount());
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-2, -2);
        actionParams.topMargin = Ui.px(14);
        box.addView(action, actionParams);
        promptActions[which] = action;
        box.setVisibility(View.GONE);
        return box;
    }

    /** La vista de una pestaña: sin cuenta, Suscripciones y Biblioteca muestran el aviso. */
    private View viewOf(int which) {
        if (which == TAB_SEARCH) return search;
        if (!signed && (which == TAB_SUBS || which == TAB_LIBRARY)) return prompts[which];
        return sections[which];
    }

    private void select(int which) {
        if (which == tab) {
            focusFirst(which);
            return;
        }
        tab = which;
        for (int i = 0; i < tabs.length; i++) Ui.markSelected(tabs[i], i == which);
        View target = viewOf(which);
        for (RowsView section : sections) {
            section.setVisibility(View.GONE);
            section.cancelFocus();
        }
        for (LinearLayout prompt : prompts) {
            if (prompt != null) prompt.setVisibility(View.GONE);
        }
        search.setVisibility(View.GONE);
        target.setVisibility(View.VISIBLE);

        boolean searchTab = which == TAB_SEARCH;
        boolean rowsTab = target instanceof RowsView;
        int media = searchTab ? View.INVISIBLE : View.VISIBLE;
        hero.setVisibility(media);
        shadeLeft.setVisibility(media);
        shadeBottom.setVisibility(media);
        detail.setVisibility(rowsTab ? View.VISIBLE : View.INVISIBLE);
        if (!searchTab) current = null;

        if (!loaded[which]) {
            loaded[which] = true;
            load(which);
        }
        if (rowsTab) ((RowsView) target).enter();
        refreshUpdate();
        focusFirst(which);
    }

    private void focusFirst(int which) {
        root.post(() -> {
            if (which == TAB_SEARCH) search.focusStart();
            else if (!signed && (which == TAB_SUBS || which == TAB_LIBRARY)) promptActions[which].requestFocus();
            else sections[which].focusFirst();
        });
    }

    private void load(int which) {
        ContentService service = content();
        switch (which) {
            case TAB_HOME:
                loadHome(sections[TAB_HOME], service);
                break;
            case TAB_SUBS:
                if (signed) {
                    sections[TAB_SUBS].groups(() -> service.getSubscriptionsObserve().map(Tiles::single), false);
                }
                break;
            case TAB_LIVE:
                sections[TAB_LIVE].groups(service::getLiveObserve, true);
                break;
            case TAB_LIBRARY:
                if (signed) {
                    sections[TAB_LIBRARY].row("Historial", false)
                            .load(() -> service.getHistoryObserve().map(Tiles::items));
                    sections[TAB_LIBRARY].groups(service::getPlaylistRowsObserve, false);
                }
                break;
            default:
                break;
        }
    }

    private void loadHome(RowsView home, ContentService service) {
        if (signed) {
            // Con cuenta: «Seguir viendo» sale del historial y debajo, las filas del inicio.
            home.row("Seguir viendo", false).load(() -> service.getHistoryObserve().map(Tiles::items));
            home.groups(service::getHomeObserve, false);
        } else {
            // Sin cuenta YouTube no entrega el inicio. Tendencias ya no existe (la retiró YouTube en
            // 2025): la primera fila es la primera sección con videos, y la segunda, los directos.
            loadFirstSection(home.row("", false), service);
            home.row("En vivo ahora", true).load(() -> service.getLiveObserve().map(Tiles::firstItems));
        }
    }

    /** Música, Noticias, Deportes o Videojuegos: la primera que traiga videos da la fila. */
    private void loadFirstSection(RowsView.Row row, ContentService service) {
        row.loadSection(Observable.concat(
                section("Música", service.getMusicObserve()),
                section("Noticias", service.getNewsObserve()),
                section("Deportes", service.getSportsObserve()),
                section("Videojuegos", service.getGamingObserve()))
                .filter(section -> !section.items.isEmpty())
                .take(1));
    }

    /** Una sección con su nombre; si falla, cuenta como vacía y se pasa a la siguiente. */
    private static Observable<RowsView.Section> section(String label, Observable<List<MediaGroup>> source) {
        return source
                .map(groups -> new RowsView.Section(label, Tiles.firstItems(groups)))
                .onErrorReturnItem(new RowsView.Section(label, new ArrayList<MediaItem>()));
    }

    private void openAccount() {
        startActivity(new Intent(this, AccountActivity.class));
    }

    private static ContentService content() {
        return YouTubeServiceManager.instance().getContentService();
    }

    @Override
    public void focused(MediaItem item) {
        if (item == null || tab == TAB_SEARCH) return;
        current = item;
        showDetail(item);
    }

    @Override
    public void play(MediaItem item) {
        Intent intent = new Intent(this, PlayerActivity.class)
                .putExtra(PlayerActivity.EXTRA_VIDEO_ID, item.getVideoId())
                .putExtra(PlayerActivity.EXTRA_TITLE, safe(item.getTitle()))
                .putExtra(PlayerActivity.EXTRA_AUTHOR, safe(item.getAuthor()))
                .putExtra(PlayerActivity.EXTRA_CHANNEL_ID, item.getChannelId())
                .putExtra(PlayerActivity.EXTRA_IMAGE, background(item));
        startActivity(intent);
    }

    /** Las búsquedas no siempre traen el canal: en ese caso se pide a la metadata del video. */
    @Override
    public void channel(MediaItem item) {
        if (!TextUtils.isEmpty(item.getChannelId())) {
            openChannel(item, item.getChannelId());
            return;
        }
        disposables.add(YouTubeServiceManager.instance().getMediaItemService().getMetadataObserve(item.getVideoId())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(meta -> {
                    String id = meta == null ? null : meta.getChannelId();
                    if (TextUtils.isEmpty(id)) Toast.makeText(this, "Este video no trae canal", Toast.LENGTH_SHORT).show();
                    else openChannel(item, id);
                }, error -> Toast.makeText(this, "No se pudo abrir el canal", Toast.LENGTH_SHORT).show()));
    }

    private void openChannel(MediaItem item, String channelId) {
        Intent intent = new Intent(this, ChannelActivity.class)
                .putExtra(ChannelActivity.EXTRA_CHANNEL_ID, channelId)
                .putExtra(ChannelActivity.EXTRA_NAME, safe(item.getAuthor()))
                .putExtra(ChannelActivity.EXTRA_VIDEO_ID, item.getVideoId())
                .putExtra(ChannelActivity.EXTRA_IMAGE, background(item));
        startActivity(intent);
    }

    private void showDetail(MediaItem item) {
        boolean live = item.isLive();
        String second = item.getSecondTitle() == null ? "" : item.getSecondTitle().toString();
        detailEyebrow.setText(join(" · ", live ? "● En directo" : "", safe(item.getAuthor()), second));
        detailEyebrow.setTextColor(live ? LIVE : Ui.EYEBROW);
        detailTitle.setText(safe(item.getTitle()));
        String cached = descriptions.get(item.getVideoId());
        detailDesc.setText(cached == null ? "" : cached);
        String progress = item.getPercentWatched() > 0 ? "visto hasta el " + item.getPercentWatched() + " %" : "";
        detailMeta.setText(live ? "En directo" : join(" · ", Ui.duration(item.getDurationMs()), progress));
        Images.load(hero, background(item), Ui.px(960));
        handler.removeCallbacks(descriptionLater);
        if (cached == null) handler.postDelayed(descriptionLater, DESCRIPTION_DELAY_MS);
    }

    /** La descripción viene de la metadata del video; se pide solo cuando el foco se detiene. */
    private void loadDescription(MediaItem item) {
        final String id = item.getVideoId();
        if (metaRequest != null) metaRequest.dispose();
        metaRequest = YouTubeServiceManager.instance().getMediaItemService().getMetadataObserve(id)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(meta -> {
                    MediaItemMetadata data = meta;
                    String text = data == null || data.getDescription() == null ? "" : data.getDescription().trim();
                    descriptions.put(id, text);
                    if (current != null && id.equals(current.getVideoId())) detailDesc.setText(text);
                }, error -> Log.w(TAG, "Sin descripción de " + id, error));
        disposables.add(metaRequest);
    }

    private static String background(MediaItem item) {
        return item.getBackgroundImageUrl() != null ? item.getBackgroundImageUrl() : item.getCardImageUrl();
    }

    @SuppressWarnings("deprecation")
    private void checkNetwork() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = manager == null ? null : manager.getActiveNetworkInfo();
        offline.setVisibility(info != null && info.isConnected() ? View.GONE : View.VISIBLE);
    }

    /** El dictado lo pide Buscar; el texto vuelve aquí y se entrega a la pantalla de búsqueda. */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SearchView.REQUEST_VOICE && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) search.setVoice(results.get(0));
        }
    }

    @Override
    public void onBackPressed() {
        if (tab != TAB_HOME && tab != -1) {
            select(TAB_HOME);
            return;
        }
        super.onBackPressed();
    }

    private static String capitalize(String value) {
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String join(String separator, String... parts) {
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isEmpty()) continue;
            if (out.length() > 0) out.append(separator);
            out.append(part);
        }
        return out.toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
