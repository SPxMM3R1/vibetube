package cl.vibe.tube;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.dash.manifest.DashManifest;
import androidx.media3.exoplayer.dash.manifest.DashManifestParser;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.data.ChapterItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.mediaserviceinterfaces.data.PlaylistInfo;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * Reproductor con el OSD de la v1 del mockup: eyebrow, título, barra cian con marcas de capítulo,
 * «Después» y las acciones en una línea de 760 dp; a la derecha, reloj, fecha y la línea técnica
 * a la altura de la barra. «Calidad» abre la franja con pantallas anidadas. Sin cuenta, «Me gusta»
 * y «Guardar» llevan a Cuenta; con cuenta guardan y la posición se lleva al historial.
 */
@OptIn(markerClass = UnstableApi.class)
public class PlayerActivity extends Activity {
    static final String EXTRA_VIDEO_ID = "video_id";
    static final String EXTRA_TITLE = "title";
    static final String EXTRA_AUTHOR = "author";
    static final String EXTRA_CHANNEL_ID = "channel_id";
    static final String EXTRA_IMAGE = "image";
    private static final String TAG = MainActivity.TAG;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
    /** Cada cuánto se guarda la posición en el historial (con cuenta). */
    private static final long SAVE_EVERY_MS = 30_000;
    /** Cuánto queda el OSD sin tocar el control. */
    private static final long HIDE_AFTER_MS = 4_000;
    /** Un directo que no abre vuelve atrás después de mostrar el mensaje. */
    private static final long LIVE_FAIL_BACK_MS = 3_000;
    private static final String LIVE_FAIL = "Este directo no se pudo abrir";
    /** Largo máximo de la URI de un segmento que falla, en el log. */
    private static final int LOG_URI_MAX = 200;
    /** Velocidades que ciclan con OK. */
    private static final float[] SPEEDS = {1f, 1.25f, 1.5f, 2f, 0.75f, 0.5f};
    /** Ancho de la barra de avance, en unidades del mockup. */
    private static final int BAR_WIDTH = 420;
    /** Ancho de la fila de acciones, en unidades del mockup. */
    private static final int ACTIONS_WIDTH = 760;

    /** Pista del reproductor: grupo y posición, para aplicar una selección. */
    static final class TrackRef {
        final TrackGroup group;
        final int index;
        final String label;
        final boolean selected;

        TrackRef(TrackGroup group, int index, String label, boolean selected) {
            this.group = group;
            this.index = index;
            this.label = label;
            this.selected = selected;
        }
    }

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String videoId;
    private String channelId;
    private String image;
    private String author = "";
    private String videoTitle = "";
    private String viewCount = "";
    private String uploadDate = "";
    private String likeCount;
    private String nextTitle = "";
    private String nextDuration = "";
    private String wlId;
    private Disposable loading;
    private volatile boolean live;
    /** Un directo que falla se vuelve a abrir una sola vez; se rearma cuando empieza a sonar. */
    private boolean liveRetried;
    /** Con el OSD en acciones (su foco), ◀ ▶ mueven el foco y no adelantan el video. */
    private boolean actionsArmed;
    /** Descarta el ACTION_UP de OK que abrió esta pantalla (clic fantasma). */
    private final ClickGuard clicks = new ClickGuard();
    private boolean signed;
    private boolean liked;
    private boolean saved;
    private boolean panelOpen;
    private boolean marksPlaced;
    private long lastSave;
    private long[] chapterStarts = new long[0];
    private int speedIndex;
    /** 0 = subtítulos desactivados; n = la pista n-1 de «texts». */
    private int subtitleIndex;
    private ExoPlayer player;
    private final List<Qualities.Variant> videos = new ArrayList<>();
    private final List<TrackRef> audios = new ArrayList<>();
    private final List<TrackRef> texts = new ArrayList<>();
    private final List<View> marks = new ArrayList<>();
    private final List<TextView> audioViews = new ArrayList<>();
    private List<Qualities.Screen> screens = new ArrayList<>();
    /** Variante elegida dentro de cada pantalla (por defecto, la de mayor bitrate). */
    private int[] pick = new int[0];
    private FrameLayout[] screenBoxes = new FrameLayout[0];
    private TextView[] screenLabels = new TextView[0];
    /** Variante elegida a mano; null = Auto. */
    private Qualities.Variant chosen;
    private String videoSignature = "";
    private String audioSignature = "";

    private FrameLayout root;
    private PlayerView playerView;
    private TextView statusView;
    private FrameLayout osd;
    private LinearLayout left;
    private LinearLayout right;
    private TextView eyebrowView;
    private TextView titleView;
    private LinearLayout barRow;
    private FrameLayout barBox;
    private View barFill;
    private TextView timeView;
    private TextView nextLabelView;
    private TextView nextTextView;
    private LinearLayout actions;
    private TextView likeView;
    private TextView saveView;
    private TextView subsView;
    private TextView qualityView;
    private TextView speedView;
    private TextView channelView;
    private TextView clockView;
    private TextView dateView;
    private TextView techView;
    private FrameLayout panel;
    private FrameLayout screensBox;
    private LinearLayout audioBox;
    private TextView panelEyebrow;
    private TextView panelBig;
    private TextView panelBigMeta;
    private TextView panelMode;
    private TextView autoRow;
    private TextView detailName;
    private TextView detailMeta;
    private TextView detailMode;
    private Focus focus;

    /** Reloj del OSD: cada 20 s, con la fecha en español de Chile. */
    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            Date now = new Date();
            clockView.setText(new SimpleDateFormat("HH:mm", Locale.US).format(now));
            dateView.setText(capitalize(new SimpleDateFormat("EEEE d 'de' MMMM", new Locale("es", "CL")).format(now)));
            handler.postDelayed(this, 20_000);
        }
    };

    /** Avance de la barra y línea técnica, cada medio segundo. */
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            handler.postDelayed(this, 500);
        }
    };

    private final Runnable hideOsd = () -> {
        if (!inActions() && !panelOpen) {
            osd.animate().alpha(0f).translationY(Ui.px(8)).setDuration(220).start();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        videoId = getIntent().getStringExtra(EXTRA_VIDEO_ID);
        channelId = getIntent().getStringExtra(EXTRA_CHANNEL_ID);
        image = getIntent().getStringExtra(EXTRA_IMAGE);
        author = safe(getIntent().getStringExtra(EXTRA_AUTHOR));
        videoTitle = safe(getIntent().getStringExtra(EXTRA_TITLE));
        signed = Session.isSigned();
        lastSave = System.currentTimeMillis();

        setContentView(buildLayout());
        titleView.setText(videoTitle);
        updateEyebrow();
        statusView.setText("Conectando");
        clockTick.run();
        showOsd();
        refreshAll();
        loadFormats();
        loadMetadata();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Si la cuenta cambió en la pantalla de Cuenta, «Ver más tarde» y los me gusta se piden de nuevo.
        boolean now = Session.isSigned();
        if (now != signed) {
            signed = now;
            if (now) loadWatchLater();
            refreshActions();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) {
            saveHistory();
            player.pause();
        }
    }

    @Override
    protected void onDestroy() {
        if (loading != null) loading.dispose();
        disposables.clear();
        handler.removeCallbacksAndMessages(null);
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }

    // ---------- Construcción ----------

    private View buildLayout() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        // Las acciones crecen al tener el foco (1,07×): los contenedores no las recortan.
        root.setClipChildren(false);
        player = new ExoPlayer.Builder(this).build();
        playerView = new PlayerView(this);
        playerView.setUseController(false);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerView.setPlayer(player);
        root.addView(playerView, new FrameLayout.LayoutParams(-1, -1));

        statusView = Ui.eyebrow(this, "", Ui.MUTED);
        root.addView(statusView, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        // OSD: un solo degradado desde abajo; el texto flota encima.
        osd = new FrameLayout(this);
        osd.setClipChildren(false);
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xE6000000, 0x99000000, 0x00000000}));
        osd.addView(shade, new FrameLayout.LayoutParams(-1, Ui.px(430), Gravity.BOTTOM));

        // Izquierda (760): eyebrow, título, barra con «Después» y acciones.
        left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setClipChildren(false);
        left.setClipToPadding(false);
        eyebrowView = Ui.eyebrow(this, "", Ui.EYEBROW);
        left.addView(eyebrowView, new LinearLayout.LayoutParams(-1, -2));

        titleView = Ui.text(this, 40, Ui.WHITE, true);
        titleView.setLetterSpacing(-0.015f);
        titleView.setLineSpacing(0f, 1.1f);
        titleView.setMaxLines(2);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = Ui.px(10);
        left.addView(titleView, titleParams);

        barRow = new LinearLayout(this);
        barRow.setGravity(Gravity.CENTER_VERTICAL);
        barBox = new FrameLayout(this);
        GradientDrawable track = new GradientDrawable();
        track.setColor(0x38FFFFFF);
        track.setCornerRadius(Ui.px(2));
        barBox.setBackground(track);
        barFill = new View(this);
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(Ui.NOW);
        fill.setCornerRadius(Ui.px(2));
        barFill.setBackground(fill);
        barFill.setPivotX(0f);
        barFill.setScaleX(0f);
        barBox.addView(barFill, new FrameLayout.LayoutParams(Ui.px(BAR_WIDTH), Ui.px(4),
                Gravity.START | Gravity.CENTER_VERTICAL));
        barRow.addView(barBox, new LinearLayout.LayoutParams(Ui.px(BAR_WIDTH), Ui.px(4)));
        timeView = Ui.text(this, 15, Ui.MUTED, false);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(-2, -2);
        timeParams.setMarginStart(Ui.px(18));
        barRow.addView(timeView, timeParams);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(-2, -2);
        barParams.topMargin = Ui.px(18);
        left.addView(barRow, barParams);

        LinearLayout nextRow = new LinearLayout(this);
        nextRow.setGravity(Gravity.CENTER_VERTICAL);
        nextLabelView = Ui.label(this, "");
        nextRow.addView(nextLabelView, new LinearLayout.LayoutParams(-2, -2));
        nextTextView = Ui.text(this, 15, Ui.MUTED, false);
        nextTextView.setSingleLine(true);
        nextTextView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nextTextParams = new LinearLayout.LayoutParams(-1, -2);
        nextTextParams.setMarginStart(Ui.px(14));
        nextRow.addView(nextTextView, nextTextParams);
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(-1, -2);
        nextParams.topMargin = Ui.px(14);
        left.addView(nextRow, nextParams);

        actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setClipChildren(false);
        actions.setClipToPadding(false);
        likeView = actionView();
        saveView = actionView();
        subsView = actionView();
        qualityView = actionView();
        speedView = actionView();
        channelView = actionView();
        likeView.setOnClickListener(v -> toggleLike());
        saveView.setOnClickListener(v -> toggleSave());
        subsView.setOnClickListener(v -> cycleSubtitles());
        qualityView.setOnClickListener(v -> showPanel());
        speedView.setOnClickListener(v -> cycleSpeed());
        channelView.setOnClickListener(v -> openChannel());
        // Sin el OSD en acciones, ninguna recibe foco: si no, el foco inicial cae en «Me gusta».
        armActions(false);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(Ui.px(ACTIONS_WIDTH), -2);
        actionsParams.topMargin = Ui.px(26);
        left.addView(actions, actionsParams);

        FrameLayout.LayoutParams leftParams = new FrameLayout.LayoutParams(Ui.px(ACTIONS_WIDTH), -2,
                Gravity.BOTTOM | Gravity.START);
        leftParams.setMargins(Ui.px(64), 0, 0, Ui.px(52));
        osd.addView(left, leftParams);

        // Derecha: reloj, fecha y la línea técnica (se alinea con la barra en alignTech).
        right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        clockView = Ui.text(this, 64, Ui.WHITE, false);
        clockView.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        right.addView(clockView, new LinearLayout.LayoutParams(-2, -2));
        dateView = Ui.eyebrow(this, "", Ui.EYEBROW);
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(-2, -2);
        dateParams.topMargin = Ui.px(12);
        right.addView(dateView, dateParams);
        techView = Ui.text(this, 13, Ui.DIM, false);
        LinearLayout.LayoutParams techParams = new LinearLayout.LayoutParams(-2, -2);
        techParams.topMargin = Ui.px(22);
        right.addView(techView, techParams);
        FrameLayout.LayoutParams rightParams = new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.BOTTOM);
        rightParams.setMargins(0, 0, Ui.px(64), Ui.px(52));
        osd.addView(right, rightParams);
        root.addView(osd, new FrameLayout.LayoutParams(-1, -1));

        panel = buildPanel();
        panel.setVisibility(View.GONE);
        root.addView(panel, new FrameLayout.LayoutParams(-1, -1));

        osd.getViewTreeObserver().addOnGlobalLayoutListener(this::alignTech);
        // El foco se dibuja encima de todo: se crea al final.
        focus = new Focus(root);
        root.getViewTreeObserver().addOnGlobalFocusChangeListener(focus);
        root.getViewTreeObserver().addOnGlobalFocusChangeListener(this::onFocusMoved);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    statusView.setText("");
                    liveRetried = false;
                }
                if (state == Player.STATE_BUFFERING && player.getCurrentPosition() == 0) statusView.setText("Cargando");
                if (state == Player.STATE_ENDED) finish();
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                Log.e(TAG, "Error del reproductor", error);
                logHttpFailure(error);
                if (live && !liveRetried) {
                    // Un solo reintento: prepare() vuelve a pedir el manifiesto del directo.
                    liveRetried = true;
                    statusView.setText("Cargando");
                    player.prepare();
                    return;
                }
                fail(live ? LIVE_FAIL : "No se pudo reproducir: " + error.getErrorCodeName());
            }

            @Override
            public void onRenderedFirstFrame() {
                statusView.setText("");
                hideSoon();
            }

            @Override
            public void onTracksChanged(Tracks tracks) {
                collectTracks();
                refreshAll();
            }
        });
        return root;
    }

    /** Franja de Calidad y audio: abajo, con pantallas anidadas, audio y el detalle de lo marcado. */
    private FrameLayout buildPanel() {
        FrameLayout box = new FrameLayout(this);
        box.setClipChildren(false);
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xE6000000, 0xA8000000, 0x00000000}));
        box.addView(shade, new FrameLayout.LayoutParams(-1, Ui.px(400), Gravity.BOTTOM));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        contentParams.setMargins(Ui.px(64), 0, Ui.px(64), Ui.px(48));
        box.addView(content, contentParams);

        panelEyebrow = Ui.eyebrow(this, "", Ui.EYEBROW);
        content.addView(panelEyebrow, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout big = new LinearLayout(this);
        big.setGravity(Gravity.BOTTOM);
        panelBig = Ui.text(this, 40, Ui.WHITE, true);
        big.addView(panelBig, new LinearLayout.LayoutParams(-2, -2));
        panelBigMeta = Ui.text(this, 18, Ui.MUTED, false);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(-2, -2);
        metaParams.setMarginStart(Ui.px(16));
        big.addView(panelBigMeta, metaParams);
        LinearLayout.LayoutParams bigParams = new LinearLayout.LayoutParams(-1, -2);
        bigParams.topMargin = Ui.px(8);
        content.addView(big, bigParams);

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.HORIZONTAL);
        main.setClipChildren(false);

        // Columna de pantallas: «En uso», las pantallas anidadas y «Auto».
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        panelMode = Ui.label(this, "");
        panelMode.setTextColor(Ui.LIVE);
        column.addView(panelMode, new LinearLayout.LayoutParams(-2, -2));
        screensBox = new FrameLayout(this);
        LinearLayout.LayoutParams screensParams = new LinearLayout.LayoutParams(
                Ui.px(Qualities.TOP_WIDTH), Ui.px(113));
        screensParams.topMargin = Ui.px(12);
        column.addView(screensBox, screensParams);
        autoRow = Ui.option(this, Focus.LINE, "", 15);
        autoRow.setOnClickListener(v -> chooseAuto());
        LinearLayout.LayoutParams autoParams = new LinearLayout.LayoutParams(-2, -2);
        autoParams.topMargin = Ui.px(12);
        column.addView(autoRow, autoParams);
        main.addView(column, new LinearLayout.LayoutParams(-2, -2));

        // Columna de audio: las pistas, con punto verde en la que suena.
        LinearLayout audioColumn = new LinearLayout(this);
        audioColumn.setOrientation(LinearLayout.VERTICAL);
        audioColumn.addView(Ui.label(this, "Audio"), new LinearLayout.LayoutParams(-2, -2));
        audioBox = new LinearLayout(this);
        audioBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams audioParams = new LinearLayout.LayoutParams(-2, -2);
        audioParams.topMargin = Ui.px(12);
        audioColumn.addView(audioBox, audioParams);
        LinearLayout.LayoutParams audioColumnParams = new LinearLayout.LayoutParams(-2, -2);
        audioColumnParams.setMarginStart(Ui.px(56));
        main.addView(audioColumn, audioColumnParams);

        // Detalle de lo marcado, a la derecha.
        LinearLayout detailColumn = new LinearLayout(this);
        detailColumn.setOrientation(LinearLayout.VERTICAL);
        detailColumn.setGravity(Gravity.END);
        detailColumn.addView(Ui.label(this, "Calidad"), new LinearLayout.LayoutParams(-2, -2));
        detailName = Ui.text(this, 22, Ui.WHITE, true);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(-2, -2);
        nameParams.topMargin = Ui.px(8);
        detailColumn.addView(detailName, nameParams);
        detailMeta = Ui.text(this, 15, Ui.MUTED, false);
        LinearLayout.LayoutParams detailMetaParams = new LinearLayout.LayoutParams(-2, -2);
        detailMetaParams.topMargin = Ui.px(8);
        detailColumn.addView(detailMeta, detailMetaParams);
        detailMode = Ui.text(this, 13, Ui.DIM, false);
        LinearLayout.LayoutParams detailModeParams = new LinearLayout.LayoutParams(-2, -2);
        detailModeParams.topMargin = Ui.px(12);
        detailColumn.addView(detailMode, detailModeParams);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(0, -2, 1f);
        detailParams.setMarginStart(Ui.px(56));
        main.addView(detailColumn, detailParams);

        LinearLayout.LayoutParams mainParams = new LinearLayout.LayoutParams(-1, -2);
        mainParams.topMargin = Ui.px(22);
        content.addView(main, mainParams);
        return box;
    }

    /** Una acción del OSD: texto suelto con la línea de foco. */
    private TextView actionView() {
        TextView view = Ui.option(this, Focus.LINE, "", 16);
        // Crece hacia la derecha desde su borde izquierdo: al tomar el foco no se corre a la izquierda.
        view.setTag(R.id.anchor_left, Boolean.TRUE);
        view.setPivotX(0f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.setMarginEnd(Ui.px(22));
        actions.addView(view, params);
        return view;
    }

    /**
     * Alinea la línea técnica con la barra de avance, no con las acciones (corrección de la v1): el
     * borde inferior del bloque derecho queda a la altura del centro de la barra más media línea.
     */
    private void alignTech() {
        if (osd == null || left == null || barRow == null || techView == null || right == null) return;
        int height = osd.getHeight();
        if (height == 0) return;
        int barMiddle = left.getTop() + barRow.getTop() + barRow.getHeight() / 2;
        int bottom = barMiddle + techView.getHeight() / 2;
        int margin = Math.max(0, height - bottom);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) right.getLayoutParams();
        if (params.bottomMargin != margin) {
            params.bottomMargin = margin;
            right.setLayoutParams(params);
        }
    }

    // ---------- Carga del video ----------

    private static MediaItemService service() {
        return YouTubeServiceManager.instance().getMediaItemService();
    }

    private void loadFormats() {
        loading = service().getFormatInfoObserve(videoId)
                .subscribeOn(Schedulers.io())
                .flatMap(info -> {
                    Log.i(TAG, "Formatos de " + videoId + ": dash=" + info.containsDashFormats()
                            + " hls=" + info.containsHlsUrl() + " url=" + info.containsUrlFormats()
                            + " sabr=" + info.containsSabrFormats() + " vivo=" + info.isLive()
                            + " reproducible=" + !info.isUnplayable());
                    live = info.isLive();
                    return Observable.just(source(info));
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::start, error -> {
                    Log.e(TAG, "Sin formatos para " + videoId, error);
                    String message = error.getMessage() != null ? error.getMessage() : "No se pudo obtener el video";
                    fail(live ? LIVE_FAIL : message);
                });
    }

    /**
     * Elige la fuente: HLS en un directo; DASH dinámico por URI en un directo sin HLS; DASH
     * estático en un video normal; HLS o un archivo progresivo como último recurso.
     */
    private MediaSource source(MediaItemFormatInfo info) throws Exception {
        if (info.isUnplayable()) throw new IllegalStateException("No disponible: " + safe(info.getPlayabilityReason()));
        runOnUiThread(() -> {
            if (!TextUtils.isEmpty(info.getAuthor())) author = info.getAuthor();
            if (!TextUtils.isEmpty(info.getTitle())) {
                videoTitle = info.getTitle();
                titleView.setText(videoTitle);
            }
            // Vistas y fecha vienen de la metadata (onMetadata); el formato solo rellena lo que falte.
            if (TextUtils.isEmpty(viewCount)) viewCount = pluralViews(info.getViewCount());
            if (TextUtils.isEmpty(uploadDate)) uploadDate = safe(info.getUploadDate());
            updateEyebrow();
        });
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(USER_AGENT)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(20_000);
        if (info.isLive() && info.containsHlsUrl()) {
            return hls(http, info.getHlsManifestUrl());
        }
        if (info.containsDashFormats()) {
            if (info.isLive()) {
                // Directo: el MPD «dynamic» se pide por URI; Media3 lo vuelve a pedir en cada refresco.
                DataSource.Factory factory = LiveSource.factory(http,
                        () -> info.createMpdStreamObservable().blockingFirst(),
                        info.getStartTimeMs(), info.getStartSegmentNum(), info.getSegmentDurationUs());
                return new DashMediaSource.Factory(factory)
                        .createMediaSource(androidx.media3.common.MediaItem.fromUri(LiveSource.uri(videoId)));
            }
            // Video normal: el manifiesto estático se arma aquí (el núcleo lo entrega como «dynamic»).
            String mpd = Manifests.toStatic(readText(info.createMpdStream()));
            Log.i(TAG, "MPD de " + info.getTitle() + ": static");
            DashManifest manifest = new DashManifestParser().parse(Uri.parse("https://www.youtube.com/"),
                    new ByteArrayInputStream(mpd.getBytes(StandardCharsets.UTF_8)));
            return new DashMediaSource.Factory(http).createMediaSource(manifest,
                    new androidx.media3.common.MediaItem.Builder().setUri(Uri.parse("https://www.youtube.com/"))
                            .setMimeType("application/dash+xml").build());
        }
        if (info.containsHlsUrl()) {
            return hls(http, info.getHlsManifestUrl());
        }
        java.util.List<String> urls = info.createUrlList();
        if (urls != null && !urls.isEmpty()) {
            return new ProgressiveMediaSource.Factory(http).createMediaSource(
                    androidx.media3.common.MediaItem.fromUri(urls.get(0)));
        }
        throw new IllegalStateException(info.containsSabrFormats()
                ? "Este video solo llega por SABR: falta el lector de SmartTube para ese formato"
                : "El núcleo no devolvió formatos reproducibles");
    }

    private static MediaSource hls(DefaultHttpDataSource.Factory http, String url) {
        return new HlsMediaSource.Factory(http).createMediaSource(androidx.media3.common.MediaItem.fromUri(url));
    }

    private void start(MediaSource source) {
        player.setMediaSource(source);
        player.prepare();
        player.play();
        handler.post(tick);
    }

    /** Un fallo muestra el mensaje en el OSD; un directo que no abre vuelve atrás. */
    private void fail(String message) {
        statusView.setText(message);
        statusView.bringToFront();
        showOsd();
        if (live) handler.postDelayed(this::finish, LIVE_FAIL_BACK_MS);
    }

    /**
     * Si la causa del error es una respuesta HTTP que no es 2xx, deja en el log (tag VibeTube) el
     * código, la URI del pedido recortada y el número de segmento (parámetro sq), para saber qué
     * segmento falló. El sq se toma de la URI completa: la recorte no lo borra.
     */
    private static void logHttpFailure(PlaybackException error) {
        Throwable cause = error;
        for (int depth = 0; cause != null && depth < 10; depth++) {
            if (cause instanceof HttpDataSource.InvalidResponseCodeException) {
                HttpDataSource.InvalidResponseCodeException http = (HttpDataSource.InvalidResponseCodeException) cause;
                Uri full = http.dataSpec == null ? null : http.dataSpec.uri;
                String uri = full == null ? "" : String.valueOf(full);
                String sq = full == null ? null : full.getQueryParameter("sq");
                if (uri.length() > LOG_URI_MAX) uri = uri.substring(0, LOG_URI_MAX);
                Log.e(TAG, "HTTP " + http.responseCode + " al pedir " + uri + " sq=" + sq);
                return;
            }
            cause = cause.getCause();
        }
    }

    // ---------- Metadata y acciones con cuenta ----------

    /** Me gusta, «Ver más tarde», capítulos y «Después». Sin cuenta, lo que traiga el núcleo. */
    private void loadMetadata() {
        disposables.add(service().getMetadataObserve(videoId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onMetadata, error -> Log.w(TAG, "Sin metadata de " + videoId, error)));
        if (signed) loadWatchLater();
    }

    /** «Ver más tarde» es la primera lista que devuelve el núcleo; su estado dice si el video está guardado. */
    private void loadWatchLater() {
        disposables.add(service().getPlaylistsInfoObserve(videoId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(list -> {
                    if (list != null && !list.isEmpty()) {
                        PlaylistInfo watchLater = list.get(0);
                        wlId = watchLater.getPlaylistId();
                        saved = watchLater.isSelected();
                        refreshActions();
                    }
                }, error -> Log.w(TAG, "Sin lista «Ver más tarde»", error)));
    }

    private void onMetadata(MediaItemMetadata meta) {
        if (meta == null) return;
        if (TextUtils.isEmpty(channelId)) channelId = meta.getChannelId();
        if (TextUtils.isEmpty(author)) {
            author = safe(meta.getAuthor());
            updateEyebrow();
        }
        likeCount = meta.getLikeCount();
        liked = meta.getLikeStatus() == MediaItemMetadata.LIKE_STATUS_LIKE;
        setChapters(meta.getChapters());
        // Eyebrow: canal · vistas · fecha. La metadata es la fuente; el formato solo rellena.
        if (!TextUtils.isEmpty(meta.getViewCount())) viewCount = meta.getViewCount();
        if (!TextUtils.isEmpty(meta.getPublishedDate())) uploadDate = meta.getPublishedDate();
        Log.i(TAG, "Metadata de " + videoId + ": vistas=" + viewCount + " fecha=" + uploadDate);
        updateEyebrow();
        MediaItem next = meta.getNextVideo();
        if (next != null) {
            nextTitle = safe(next.getTitle());
            loadNextDuration(next.getVideoId());
        }
        refreshNext();
        refreshActions();
    }

    /**
     * Duración del siguiente video para «Después · 18:42». El autoplay del núcleo no la trae; el
     * formato del video siguiente sí (lengthSeconds). Si falla, «Después» queda sin duración.
     */
    private void loadNextDuration(String nextId) {
        if (TextUtils.isEmpty(nextId)) return;
        disposables.add(service().getFormatInfoObserve(nextId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(info -> {
                    nextDuration = Ui.duration(seconds(info.getLengthSeconds()) * 1000L);
                    refreshNext();
                }, error -> Log.w(TAG, "Sin duración del siguiente video " + nextId, error)));
    }

    private void toggleLike() {
        if (!signed) {
            openAccount();
            return;
        }
        MediaItem item = Session.item(videoId);
        Observable<Void> call = liked ? service().removeLikeObserve(item) : service().setLikeObserve(item);
        disposables.add(call.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(ignored -> {
                }, error -> {
                    Log.w(TAG, "No se cambió el me gusta", error);
                    toast("No se pudo cambiar el me gusta");
                }, () -> {
                    liked = !liked;
                    refreshActions();
                }));
    }

    private void toggleSave() {
        if (!signed) {
            openAccount();
            return;
        }
        if (wlId == null) {
            toast("No se pudo leer «Ver más tarde»");
            return;
        }
        Observable<Void> call = saved
                ? service().removeFromPlaylistObserve(wlId, videoId)
                : service().addToPlaylistObserve(wlId, videoId);
        disposables.add(call.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(ignored -> {
                }, error -> {
                    Log.w(TAG, "No se guardó el video", error);
                    toast("No se pudo guardar el video");
                }, () -> {
                    saved = !saved;
                    refreshActions();
                }));
    }

    private void openChannel() {
        if (TextUtils.isEmpty(channelId)) {
            toast("Este video no trae canal");
            return;
        }
        startActivity(new Intent(this, ChannelActivity.class)
                .putExtra(ChannelActivity.EXTRA_CHANNEL_ID, channelId)
                .putExtra(ChannelActivity.EXTRA_NAME, author)
                .putExtra(ChannelActivity.EXTRA_VIDEO_ID, videoId)
                .putExtra(ChannelActivity.EXTRA_IMAGE, image));
    }

    private void openAccount() {
        startActivity(new Intent(this, AccountActivity.class));
    }

    /** Guarda la posición en el historial (con cuenta). Corre sola: no se cancela al salir. */
    private void saveHistory() {
        if (!signed || live || player == null) return;
        lastSave = System.currentTimeMillis();
        float seconds = player.getCurrentPosition() / 1000f;
        service().updateHistoryPositionObserve(videoId, seconds)
                .subscribeOn(Schedulers.io())
                .subscribe(ignored -> {
                }, error -> Log.w(TAG, "No se guardó la posición", error));
    }

    // ---------- Pistas y calidades ----------

    /** Lee las pistas que el reproductor conoce y, si cambiaron las calidades, rehace las pantallas. */
    private void collectTracks() {
        if (player == null) return;
        List<Qualities.Variant> newVideos = new ArrayList<>();
        List<TrackRef> newAudios = new ArrayList<>();
        List<TrackRef> newTexts = new ArrayList<>();
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            for (int i = 0; i < group.length; i++) {
                if (!group.isTrackSupported(i)) continue;
                Format format = group.getTrackFormat(i);
                boolean selected = group.isTrackSelected(i);
                TrackGroup mediaGroup = group.getMediaTrackGroup();
                if (group.getType() == C.TRACK_TYPE_VIDEO) {
                    if (format.height > 0) {
                        newVideos.add(new Qualities.Variant(format.width, format.height, format.frameRate,
                                format.bitrate, isHdr(format), new TrackRef(mediaGroup, i, "", selected)));
                    }
                } else if (group.getType() == C.TRACK_TYPE_AUDIO) {
                    newAudios.add(new TrackRef(mediaGroup, i, audioName(format), selected));
                } else if (group.getType() == C.TRACK_TYPE_TEXT) {
                    newTexts.add(new TrackRef(mediaGroup, i, textName(format), selected));
                }
            }
        }

        // Pistas con el mismo rótulo: la segunda lleva «· 2», etc.
        List<String> audioNames = new ArrayList<>();
        for (TrackRef track : newAudios) audioNames.add(track.label);
        List<String> unique = AudioLabels.unique(audioNames);
        for (int i = 0; i < newAudios.size(); i++) {
            TrackRef track = newAudios.get(i);
            newAudios.set(i, new TrackRef(track.group, track.index, unique.get(i), track.selected));
        }

        String signature = videoSignature(newVideos);
        if (!signature.equals(videoSignature)) {
            boolean hadChoice = chosen != null;
            videoSignature = signature;
            videos.clear();
            videos.addAll(newVideos);
            chosen = null;
            rebuildScreens();
            if (hadChoice) applyVideo();
        }
        texts.clear();
        texts.addAll(newTexts);
        if (subtitleIndex > texts.size()) subtitleIndex = 0;
        audios.clear();
        audios.addAll(newAudios);
        refreshAudio();
    }

    private void rebuildScreens() {
        screens = Qualities.screens(videos);
        pick = new int[screens.size()];
        screensBox.removeAllViews();
        screenBoxes = new FrameLayout[screens.size()];
        screenLabels = new TextView[screens.size()];
        int max = screens.isEmpty() ? 0 : screens.get(0).height;
        // La mayor primero: queda atrás, abajo a la izquierda, y las chicas delante.
        for (int i = 0; i < screens.size(); i++) {
            final int index = i;
            int width = Ui.px(Qualities.width(screens.get(i).height, max));
            int height = Math.round(width * 9 / 16f);
            FrameLayout box = new FrameLayout(this);
            box.setFocusable(true);
            TextView label = Ui.text(this, 11, Ui.MUTED, false);
            label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
            labelParams.setMargins(0, Ui.px(4), Ui.px(6), 0);
            box.addView(label, labelParams);
            box.setOnClickListener(v -> chooseVariant(index));
            screensBox.addView(box, new FrameLayout.LayoutParams(width, height, Gravity.BOTTOM | Gravity.START));
            screenBoxes[i] = box;
            screenLabels[i] = label;
        }
        refreshScreenLabels();
    }

    private void refreshScreenLabels() {
        for (int i = 0; i < screenLabels.length; i++) {
            screenLabels[i].setText(Qualities.label(screens.get(i).variants.get(pick[i])));
        }
        restyleScreens();
    }

    /**
     * Verde con relleno la pantalla que suena; blanca la que tiene el foco. La etiqueta se muestra
     * si cabe junto a la de la pantalla mayor (Qualities.labelFits); la que tiene el foco siempre
     * la muestra. Una etiqueta que no cabe queda oculta: la resolución sale en el detalle.
     */
    private void restyleScreens() {
        int playing = playingHeight();
        int n = screenBoxes.length;
        int[] widths = new int[n];
        int[] heights = new int[n];
        for (int i = 0; i < n; i++) {
            widths[i] = screenBoxes[i].getLayoutParams().width;
            heights[i] = screenBoxes[i].getLayoutParams().height;
        }
        int labelHeight = n == 0 ? 0 : Math.round(screenLabels[0].getPaint().getFontSpacing());
        int gap = Ui.px(4);
        for (int i = 0; i < n; i++) {
            boolean focused = screenBoxes[i].hasFocus();
            boolean playingHere = screens.get(i).height == playing;
            screenBoxes[i].setBackground(screenFrame(focused, playingHere));
            screenLabels[i].setTextColor(focused ? Color.WHITE : playingHere ? Ui.LIVE : Ui.MUTED);
            boolean shown = focused || Qualities.labelFits(widths, heights, i, labelHeight, gap);
            screenLabels[i].setVisibility(shown ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private static GradientDrawable screenFrame(boolean focused, boolean playing) {
        GradientDrawable frame = new GradientDrawable();
        frame.setCornerRadius(Ui.px(3));
        frame.setColor(playing ? 0x1A5BE9A0 : 0x00000000);
        int stroke = focused ? Color.WHITE : playing ? Ui.LIVE : 0x57FFFFFF;
        frame.setStroke(Math.max(1, Ui.px(focused ? 2 : 1)), stroke);
        return frame;
    }

    private void chooseVariant(int i) {
        if (i < 0 || i >= screens.size()) return;
        chosen = screens.get(i).variants.get(pick[i]);
        applyVideo();
        refreshAll();
    }

    private void chooseAuto() {
        chosen = null;
        applyVideo();
        refreshAll();
    }

    /** Aplica la calidad elegida (o Auto) con los parámetros de selección del reproductor. */
    private void applyVideo() {
        if (player == null) return;
        TrackSelectionParameters.Builder builder = player.getTrackSelectionParameters().buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO);
        if (chosen != null) {
            TrackRef ref = (TrackRef) chosen.ref;
            builder.setOverrideForType(new TrackSelectionOverride(ref.group, ref.index));
        }
        player.setTrackSelectionParameters(builder.build());
    }

    private void applyAudio(int i) {
        if (player == null || i < 0 || i >= audios.size()) return;
        TrackRef ref = audios.get(i);
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .setOverrideForType(new TrackSelectionOverride(ref.group, ref.index))
                .build());
    }

    /** Subtítulos cicla entre desactivados y las pistas que traiga el video. */
    private void cycleSubtitles() {
        if (texts.isEmpty()) {
            toast("Este video no trae subtítulos");
            return;
        }
        subtitleIndex = (subtitleIndex + 1) % (texts.size() + 1);
        if (player != null) {
            TrackSelectionParameters.Builder builder = player.getTrackSelectionParameters().buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT);
            if (subtitleIndex == 0) {
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true);
            } else {
                TrackRef ref = texts.get(subtitleIndex - 1);
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(new TrackSelectionOverride(ref.group, ref.index));
            }
            player.setTrackSelectionParameters(builder.build());
        }
        refreshActions();
    }

    private void cycleSpeed() {
        speedIndex = (speedIndex + 1) % SPEEDS.length;
        if (player != null) player.setPlaybackSpeed(SPEEDS[speedIndex]);
        refreshActions();
    }

    // ---------- Franja de Calidad ----------

    private void showPanel() {
        panelOpen = true;
        handler.removeCallbacks(hideOsd);
        osd.animate().alpha(0f).setDuration(200).start();
        panel.setVisibility(View.VISIBLE);
        panel.setAlpha(0f);
        panel.animate().alpha(1f).setInterpolator(Ui.SPRING).setDuration(420).start();
        refreshPanel();
        root.post(this::focusPanelDefault);
    }

    private void hidePanel() {
        panelOpen = false;
        panel.animate().alpha(0f).setDuration(200).withEndAction(() -> panel.setVisibility(View.GONE)).start();
        showOsd();
        armActions(true);
        qualityView.requestFocus();
    }

    /** Al abrir, el foco va a la pantalla que se elige (o a la que suena), o a Auto si no hay pantallas. */
    private void focusPanelDefault() {
        int target = chosen != null ? indexOfHeight(chosen.height) : indexOfHeight(playingHeight());
        if (target >= 0 && target < screenBoxes.length) screenBoxes[target].requestFocus();
        else autoRow.requestFocus();
    }

    private int indexOfHeight(int height) {
        for (int i = 0; i < screens.size(); i++) {
            if (screens.get(i).height == height) return i;
        }
        return -1;
    }

    private int screenIndexOf(View view) {
        for (int i = 0; i < screenBoxes.length; i++) {
            if (screenBoxes[i] == view) return i;
        }
        return -1;
    }

    /** Con el foco en la franja: redibuja las pantallas y muestra el detalle de lo marcado. */
    private void onFocusMoved(View oldFocus, View newFocus) {
        if (!panelOpen) return;
        restyleScreens();
        if (newFocus == autoRow) {
            showDetailAuto();
            return;
        }
        int i = screenIndexOf(newFocus);
        if (i >= 0) showDetail(screens.get(i).variants.get(pick[i]));
    }

    private void showDetail(Qualities.Variant v) {
        detailName.setText(Qualities.label(v));
        detailMeta.setText(Qualities.detail(v));
        detailMode.setText(dotPrefix("en uso: " + modeText()));
    }

    private void showDetailAuto() {
        detailName.setText("Auto");
        detailMeta.setText("elige según tu conexión");
        detailMode.setText(dotPrefix("en uso: " + modeText()));
    }

    /** Lo que suena hoy: «Auto (2160p)» o la resolución elegida a mano. */
    private String modeText() {
        if (chosen != null) return Qualities.label(chosen);
        return playingHeight() > 0 ? "Auto (" + heightLabel() + ")" : "Auto";
    }

    private void refreshAll() {
        refreshActions();
        refreshPanel();
    }

    private void refreshActions() {
        likeView.setText(withDot("Me gusta" + (TextUtils.isEmpty(likeCount) ? "" : " · " + likeCount), liked));
        saveView.setText(withDot("Guardar", saved));
        subsView.setText("Subtítulos · " + subtitleLabel());
        // Sin pistas, «no hay» no recibe foco: el control salta de Me gusta/Guardar a Calidad.
        boolean hasSubtitles = !texts.isEmpty();
        subsView.setFocusable(actionsArmed && hasSubtitles);
        subsView.setAlpha(hasSubtitles ? 1f : 0.25f);
        qualityView.setText("Calidad · " + modeText());
        speedView.setText("Velocidad · " + speedText());
        channelView.setText("Ir al canal");
    }

    private void refreshPanel() {
        Format video = player == null ? null : player.getVideoFormat();
        panelEyebrow.setText(dotPrefix("Calidad y audio · " + author));
        panelBig.setText(playingHeight() > 0 ? heightLabel() : "—");
        panelBigMeta.setText(video == null ? "" : bitrateLine(video));
        panelMode.setText("En uso · " + (chosen == null ? "Auto" : Qualities.label(chosen)));
        autoRow.setText(chosen == null ? withDot("Auto · suena " + heightLabel(), true) : "Auto");
        refreshScreenLabels();
        refreshAudio();
    }

    private void refreshAudio() {
        String signature = audioSignature(audios);
        if (!signature.equals(audioSignature)) {
            audioSignature = signature;
            audioBox.removeAllViews();
            audioViews.clear();
            if (audios.isEmpty()) {
                TextView none = Ui.text(this, 15, Ui.DIM, false);
                none.setText("Sin pistas de audio aparte");
                audioBox.addView(none);
            }
            for (int i = 0; i < audios.size(); i++) {
                final int index = i;
                TextView row = Ui.option(this, Focus.LINE, "", 15);
                row.setOnClickListener(v -> applyAudio(index));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
                params.topMargin = Ui.px(i == 0 ? 0 : 10);
                audioBox.addView(row, params);
                audioViews.add(row);
            }
        }
        for (int i = 0; i < audioViews.size(); i++) {
            TrackRef track = audios.get(i);
            audioViews.get(i).setText(withDot(track.label, track.selected));
        }
    }

    // ---------- OSD y control ----------

    private void showOsd() {
        if (panelOpen) return;
        handler.removeCallbacks(hideOsd);
        osd.animate().alpha(1f).translationY(0).setInterpolator(Ui.SPRING).setDuration(420).start();
    }

    private void hideSoon() {
        handler.removeCallbacks(hideOsd);
        if (!inActions() && !panelOpen) handler.postDelayed(hideOsd, HIDE_AFTER_MS);
    }

    private void focusActions() {
        armActions(true);
        showOsd();
        handler.removeCallbacks(hideOsd);
        likeView.requestFocus();
    }

    /**
     * Las acciones solo reciben foco cuando el OSD las usa (Abajo). Fuera de eso, Android daría el
     * foco inicial a la primera que sea enfocable, y el OK que abrió el video la activaría.
     */
    private void armActions(boolean on) {
        actionsArmed = on;
        for (int i = 0; i < actions.getChildCount(); i++) {
            View child = actions.getChildAt(i);
            // «Subtítulos» solo es enfocable si el video trae pistas.
            child.setFocusable(on && (child != subsView || !texts.isEmpty()));
        }
    }

    /** ◀ ▶ con el foco en las acciones: pasa a la acción vecina enfocable; no se detiene en los bordes. */
    private void moveAction(int step) {
        // El foco puede estar en un descendiente de la acción: se sube hasta su hijo directo.
        int at = actions.indexOfChild(directChildOf(actions, getCurrentFocus()));
        if (at < 0) {
            Log.d(TAG, "moveAction sin acción enfocada");
            return;
        }
        for (int i = at + step; i >= 0 && i < actions.getChildCount(); i += step) {
            View target = firstFocusable(actions.getChildAt(i));
            if (target != null && target.requestFocus()) {
                Log.d(TAG, "moveAction de " + at + " a " + i);
                return;
            }
        }
    }

    /**
     * ◀ ▶ con el foco en la fila de acciones (cambia de acción) o en una pantalla de Calidad con
     * varias variantes (cambia cuál es; el detalle muestra cuál). Se atiende aquí, antes que las
     * vistas: en el emulador el foco no se movía cuando la tecla llegaba al onKeyDown del Activity.
     * Devuelve true si la tecla quedó atendida (en DOWN se actúa; en UP solo se consume).
     */
    private boolean handleArrowKey(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
        int step = keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1;
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        if (panelOpen) {
            int index = screenIndexOf(getCurrentFocus());
            if (index < 0 || screens.get(index).variants.size() < 2) return false;
            if (down) cycleVariant(index, step);
            return true;
        }
        if (!inActions()) return false;
        if (down) moveAction(step);
        return true;
    }

    /** Cambia la variante marcada de una pantalla de Calidad y muestra su detalle. */
    private void cycleVariant(int index, int step) {
        int count = screens.get(index).variants.size();
        pick[index] = ((pick[index] + step) % count + count) % count;
        refreshScreenLabels();
        showDetail(screens.get(index).variants.get(pick[index]));
    }

    /** El hijo directo de `group` que contiene a `view`, o null si `view` no está dentro de `group`. */
    private static View directChildOf(ViewGroup group, View view) {
        View cursor = view;
        while (cursor != null && cursor.getParent() != group) {
            cursor = cursor.getParent() instanceof View ? (View) cursor.getParent() : null;
        }
        return cursor;
    }

    /** La propia vista si es enfocable; si no, su primer descendiente enfocable (o null). */
    private static View firstFocusable(View view) {
        if (view.isFocusable()) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = firstFocusable(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private boolean inActions() {
        return isInside(actions, getCurrentFocus());
    }

    /** Descarta el ACTION_UP de OK que llega sin su DOWN (la tecla que abrió el video). */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (clicks.discard(event)) return true;
        if (handleArrowKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    /** true si `view` es `group` o está dentro de él. Un View no es ViewParent: se sube desde su padre. */
    private static boolean isInside(View group, View view) {
        if (group == null || view == null) return false;
        if (view == group) return true;
        ViewParent parent = view.getParent();
        while (parent != null) {
            if (parent == group) return true;
            parent = parent.getParent();
        }
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Con el foco en las acciones o en la franja, las flechas las mueve el sistema.
        if (panelOpen) {
            if (getCurrentFocus() == null && keyCode != KeyEvent.KEYCODE_BACK) {
                focusPanelDefault();
                return true;
            }
            return super.onKeyDown(keyCode, event);
        }
        if (inActions()) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                getCurrentFocus().clearFocus();
                // Al soltar el foco, la línea se va también aquí: no depende del aviso de foco del sistema.
                focus.hideAll();
                armActions(false);
                hideSoon();
                return true;
            }
            // ◀ ▶ con el foco en la fila lo atiende dispatchKeyEvent (handleArrowKey); no llegan aquí.
            return super.onKeyDown(keyCode, event);
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                player.setPlayWhenReady(!player.getPlayWhenReady());
                showOsd();
                hideSoon();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                player.seekTo(player.getCurrentPosition() + 10_000);
                showOsd();
                hideSoon();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                player.seekTo(Math.max(0, player.getCurrentPosition() - 10_000));
                showOsd();
                hideSoon();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                focusActions();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_INFO:
                showOsd();
                hideSoon();
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public void onBackPressed() {
        if (panelOpen) hidePanel();
        else super.onBackPressed();
    }

    // ---------- Progreso y textos ----------

    private void updateProgress() {
        if (player == null) return;
        long duration = player.getDuration();
        long position = player.getCurrentPosition();
        if (!live && duration > 0) {
            barFill.setScaleX(Math.min(1f, position / (float) duration));
            if (!marksPlaced) placeMarks(duration);
            timeView.setText(clock(position) + " / " + clock(duration) + " · quedan "
                    + minutesLeft(Math.max(0, duration - position)));
        } else {
            barFill.setScaleX(live ? 1f : 0f);
            timeView.setText(live ? "En directo" : "");
        }
        updateTech();
        if (signed && !live && System.currentTimeMillis() - lastSave >= SAVE_EVERY_MS) saveHistory();
    }

    private void updateTech() {
        Format video = player.getVideoFormat();
        Format audio = player.getAudioFormat();
        if (video == null) return;
        StringBuilder line = new StringBuilder();
        line.append(video.width).append(" × ").append(video.height);
        if (video.frameRate > 0) line.append(" · ").append(Math.round(video.frameRate)).append(" fps");
        if (video.codecs != null) line.append(" · ").append(codecName(video.codecs));
        if (audio != null && audio.codecs != null) line.append(" · ").append(codecName(audio.codecs));
        if (video.bitrate > 0) line.append(String.format(Locale.US, " · %.1f Mbps", video.bitrate / 1_000_000f));
        techView.setText(line);
    }

    /** Marcas de capítulo sobre la barra, a partir del inicio de cada uno. */
    private void setChapters(List<ChapterItem> chapters) {
        for (View mark : marks) barBox.removeView(mark);
        marks.clear();
        marksPlaced = false;
        if (chapters == null || chapters.isEmpty()) {
            chapterStarts = new long[0];
            return;
        }
        chapterStarts = new long[chapters.size()];
        for (int i = 0; i < chapters.size(); i++) chapterStarts[i] = chapters.get(i).getStartTimeMs();
    }

    private void placeMarks(long duration) {
        marksPlaced = true;
        int width = Ui.px(BAR_WIDTH);
        for (long start : chapterStarts) {
            if (start <= 0) continue;
            View mark = new View(this);
            mark.setBackgroundColor(0xA6000000);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(Math.max(1, Ui.px(2)), Ui.px(10),
                    Gravity.START | Gravity.CENTER_VERTICAL);
            params.leftMargin = Math.round(width * Math.min(1f, start / (float) duration));
            barBox.addView(mark, params);
            marks.add(mark);
        }
    }

    private void refreshNext() {
        nextLabelView.setText("Después" + (TextUtils.isEmpty(nextDuration) ? "" : " · " + nextDuration));
        nextTextView.setText(nextTitle);
    }

    private void updateEyebrow() {
        if (live) {
            eyebrowView.setText(dotPrefix(join(" · ", "En vivo", author, viewCount)));
        } else {
            eyebrowView.setText(join(" · ", author, viewCount, dateText(uploadDate)));
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int playingHeight() {
        Format video = player == null ? null : player.getVideoFormat();
        return video == null ? 0 : video.height;
    }

    private String heightLabel() {
        int height = playingHeight();
        return height > 0 ? Qualities.height(height) : "—";
    }

    private String subtitleLabel() {
        if (texts.isEmpty()) return "no hay";
        return subtitleIndex == 0 ? "Desactivados" : texts.get(subtitleIndex - 1).label;
    }

    private String speedText() {
        float speed = SPEEDS[speedIndex];
        return (speed == Math.round(speed) ? String.valueOf(Math.round(speed)) : String.valueOf(speed)) + "×";
    }

    // ---------- Utilidades ----------

    private static String videoSignature(List<Qualities.Variant> list) {
        StringBuilder out = new StringBuilder();
        for (Qualities.Variant v : list) {
            out.append(v.height).append(':').append(v.fps).append(':').append(v.bitrate).append(';');
        }
        return out.toString();
    }

    private static String audioSignature(List<TrackRef> list) {
        StringBuilder out = new StringBuilder();
        for (TrackRef ref : list) out.append(ref.label).append(';');
        return out.toString();
    }

    private static boolean isHdr(Format format) {
        return format.colorInfo != null
                && (format.colorInfo.colorTransfer == C.COLOR_TRANSFER_HLG
                || format.colorInfo.colorTransfer == C.COLOR_TRANSFER_ST2084);
    }

    private static String bitrateLine(Format video) {
        StringBuilder line = new StringBuilder();
        if (video.bitrate > 0) line.append(String.format(Locale.US, "%.1f Mbps", video.bitrate / 1_000_000f));
        if (video.frameRate > 0) {
            if (line.length() > 0) line.append(" · ");
            line.append(Math.round(video.frameRate)).append(" fps");
        }
        return line.toString();
    }

    /**
     * Rótulo de una pista de audio: idioma, códec y bitrate (AudioLabels), con «Original» si es la
     * pista principal y «(doblaje automático)» si el formato lo dice.
     */
    private static String audioName(Format format) {
        String label = format.label == null ? "" : format.label.toLowerCase(Locale.ROOT);
        boolean original = (format.roleFlags & C.ROLE_FLAG_MAIN) != 0 || label.contains("original");
        boolean dubbed = !original && (label.contains("auto") || label.contains("dub"));
        return AudioLabels.label(format.language, format.codecs, format.sampleMimeType, format.bitrate,
                original, dubbed);
    }

    private static String textName(Format format) {
        return capitalize(languageName(format.language, "Subtítulos"));
    }

    private static String languageName(String language, String fallback) {
        if (language == null || language.isEmpty() || "und".equals(language)) return fallback;
        return new Locale(language).getDisplayLanguage(new Locale("es", "CL"));
    }

    /**
     * Fecha del eyebrow. Si viene en ISO («2026-11-03») se pasa a «3 nov 2026»; si el núcleo ya la
     * trae escrita («3 nov 2026», «hace 2 días»), se muestra tal cual.
     */
    private static String dateText(String value) {
        if (value == null || value.isEmpty()) return "";
        if (!value.matches("\\d{4}-\\d{2}-\\d{2}.*")) return value;
        try {
            Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value.substring(0, 10));
            return new SimpleDateFormat("d MMM yyyy", new Locale("es", "CL")).format(date);
        } catch (Exception error) {
            return value;
        }
    }

    /** Vistas en español: «24.135 vistas» si el núcleo trae solo el número; el texto si ya trae palabras. */
    private static String pluralViews(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        if (!raw.matches("\\d{1,18}")) return raw;
        return java.text.NumberFormat.getIntegerInstance(new Locale("es", "CL")).format(Long.parseLong(raw))
                + " vistas";
    }

    /** Segundos de un texto numérico («1122»); 0 si no hay o no es un número. */
    private static long seconds(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception error) {
            return 0;
        }
    }

    /** Minutos que quedan: «40 min», o «1 h 2 min» pasada la hora. */
    private static String minutesLeft(long millis) {
        long minutes = millis / 60_000;
        return minutes >= 60 ? (minutes / 60) + " h " + (minutes % 60) + " min" : minutes + " min";
    }

    /** Texto con un punto verde al final, cuando «on». */
    private static CharSequence withDot(String text, boolean on) {
        if (!on) return text;
        SpannableStringBuilder out = new SpannableStringBuilder(text + " ●");
        out.setSpan(new ForegroundColorSpan(Ui.LIVE), text.length() + 1, out.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return out;
    }

    /** Texto con un punto verde al comienzo (eyebrow de «en vivo» y de la franja). */
    private static CharSequence dotPrefix(String text) {
        SpannableStringBuilder out = new SpannableStringBuilder("● " + text);
        out.setSpan(new ForegroundColorSpan(Ui.LIVE), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return out;
    }

    private static String codecName(String codecs) {
        String c = codecs.toLowerCase(Locale.US);
        if (c.startsWith("avc")) return "H.264";
        if (c.startsWith("vp09") || c.startsWith("vp9")) return "VP9";
        if (c.startsWith("av01")) return "AV1";
        if (c.startsWith("mp4a")) return "AAC";
        if (c.startsWith("opus")) return "Opus";
        return codecs;
    }

    private static String clock(long ms) {
        long s = ms / 1000, h = s / 3600, m = s % 3600 / 60, sec = s % 60;
        return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, sec) : String.format(Locale.US, "%d:%02d", m, sec);
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

    private static String capitalize(String value) {
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String readText(InputStream in) throws IOException {
        try (InputStream stream = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = stream.read(buffer)) != -1) out.write(buffer, 0, read);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
