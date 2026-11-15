package cl.vibe.tube;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.OptIn;
import androidx.media3.common.Format;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.dash.manifest.DashManifest;
import androidx.media3.exoplayer.dash.manifest.DashManifestParser;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * Reproductor con el OSD de VibeM3U: eyebrow con el canal, título, barra cian, línea técnica.
 * El núcleo de SmartTube entrega el manifiesto (DASH armado por él o HLS en vivo); lo reproduce Media3.
 */
@OptIn(markerClass = UnstableApi.class)
public class PlayerActivity extends Activity {
    static final String EXTRA_VIDEO_ID = "video_id";
    static final String EXTRA_TITLE = "title";
    static final String EXTRA_AUTHOR = "author";
    private static final String TAG = MainActivity.TAG;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private ExoPlayer player;
    private Disposable loading;
    private View osd;
    private TextView eyebrow, title, time, tech, status;
    private View barFill;
    private LinearLayout bar;
    private final Runnable hideOsd = () -> osd.animate().alpha(0f).translationY(dp(8)).setDuration(220).start();
    private final Runnable tick = new Runnable() {
        @Override public void run() { updateProgress(); time.postDelayed(this, 500); }
    };

    int dp(float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        String videoId = getIntent().getStringExtra(EXTRA_VIDEO_ID);
        setContentView(buildLayout());
        eyebrow.setText(safe(getIntent().getStringExtra(EXTRA_AUTHOR)));
        title.setText(safe(getIntent().getStringExtra(EXTRA_TITLE)));
        status.setText("Conectando");
        showOsd();
        loadFormats(videoId);
    }

    private View buildLayout() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        player = new ExoPlayer.Builder(this).build();
        PlayerView view = new PlayerView(this);
        view.setUseController(false);
        view.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        view.setPlayer(player);
        root.addView(view, new FrameLayout.LayoutParams(-1, -1));

        status = label(12, 0x9EFFFFFF);
        status.setLetterSpacing(0.2f);
        status.setAllCaps(true);
        root.addView(status, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        FrameLayout overlay = new FrameLayout(this);
        osd = overlay;
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xE6000000, 0x99000000, 0x00000000}));
        overlay.addView(shade, new FrameLayout.LayoutParams(-1, dp(300), Gravity.BOTTOM));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(48), 0, dp(48), dp(36));
        eyebrow = label(10.5f, 0x99FFFFFF);
        eyebrow.setLetterSpacing(0.2f);
        eyebrow.setAllCaps(true);
        info.addView(eyebrow);
        title = label(28, Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        title.setLetterSpacing(-0.015f);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(8);
        info.addView(title, titleParams);

        LinearLayout progressRow = new LinearLayout(this);
        progressRow.setGravity(Gravity.CENTER_VERTICAL);
        bar = new LinearLayout(this);
        GradientDrawable track = new GradientDrawable();
        track.setColor(0x38FFFFFF);
        track.setCornerRadius(dp(2));
        bar.setBackground(track);
        barFill = new View(this);
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(MainActivity.NOW);
        fill.setCornerRadius(dp(2));
        barFill.setBackground(fill);
        bar.addView(barFill, new LinearLayout.LayoutParams(0, -1));
        progressRow.addView(bar, new LinearLayout.LayoutParams(dp(320), dp(3)));
        time = label(12, 0x9EFFFFFF);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(-2, -2);
        timeParams.setMarginStart(dp(14));
        progressRow.addView(time, timeParams);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.topMargin = dp(14);
        info.addView(progressRow, rowParams);

        tech = label(10.5f, 0x66FFFFFF);
        LinearLayout.LayoutParams techParams = new LinearLayout.LayoutParams(-2, -2);
        techParams.topMargin = dp(12);
        info.addView(tech, techParams);
        overlay.addView(info, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) status.setText("");
                if (state == Player.STATE_BUFFERING && player.getCurrentPosition() == 0) status.setText("Cargando");
                if (state == Player.STATE_ENDED) finish();
            }
            @Override public void onPlayerError(PlaybackException error) {
                Log.e(TAG, "Error del reproductor", error);
                status.setText("No se pudo reproducir: " + error.getErrorCodeName());
                showOsd();
            }
            @Override public void onRenderedFirstFrame() {
                status.setText("");
                hideSoon();
            }
        });
        return root;
    }

    private TextView label(float sp, int color) {
        TextView view = new TextView(this);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        view.setShadowLayer(dp(5), 0, dp(1), 0x73000000);
        view.setIncludeFontPadding(false);
        return view;
    }

    private void loadFormats(String videoId) {
        loading = YouTubeServiceManager.instance().getMediaItemService().getFormatInfoObserve(videoId)
                .subscribeOn(Schedulers.io())
                .flatMap(info -> {
                    Log.i(TAG, "Formatos de " + videoId + ": dash=" + info.containsDashFormats()
                            + " hls=" + info.containsHlsUrl() + " url=" + info.containsUrlFormats()
                            + " sabr=" + info.containsSabrFormats() + " vivo=" + info.isLive()
                            + " reproducible=" + !info.isUnplayable());
                    return Observable.just(source(info));
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::start, error -> {
                    Log.e(TAG, "Sin formatos para " + videoId, error);
                    status.setText(error.getMessage() != null ? error.getMessage() : "No se pudo obtener el video");
                });
    }

    /** Elige la fuente: HLS en vivo, DASH armado por el núcleo, o un archivo progresivo. */
    private MediaSource source(MediaItemFormatInfo info) throws Exception {
        if (info.isUnplayable()) throw new IllegalStateException("No disponible: " + safe(info.getPlayabilityReason()));
        runOnUiThread(() -> {
            if (!TextUtils.isEmpty(info.getAuthor())) eyebrow.setText(info.getAuthor()
                    + (info.isLive() ? " · en vivo" : TextUtils.isEmpty(info.getViewCount()) ? "" : " · " + info.getViewCount()));
            if (!TextUtils.isEmpty(info.getTitle())) title.setText(info.getTitle());
            if (info.isLive()) eyebrow.setTextColor(MainActivity.LIVE);
        });
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(USER_AGENT)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(20_000);
        if (info.isLive() && info.containsHlsUrl()) {
            return new HlsMediaSource.Factory(http).createMediaSource(
                    androidx.media3.common.MediaItem.fromUri(info.getHlsManifestUrl()));
        }
        if (info.containsDashFormats()) {
            try (InputStream mpd = info.createMpdStream()) {
                DashManifest manifest = new DashManifestParser().parse(Uri.parse("https://www.youtube.com/"), mpd);
                return new DashMediaSource.Factory(http).createMediaSource(manifest,
                        new androidx.media3.common.MediaItem.Builder().setUri(Uri.parse("https://www.youtube.com/"))
                                .setMimeType("application/dash+xml").build());
            }
        }
        if (info.containsHlsUrl()) {
            return new HlsMediaSource.Factory(http).createMediaSource(
                    androidx.media3.common.MediaItem.fromUri(info.getHlsManifestUrl()));
        }
        List<String> urls = info.createUrlList();
        if (urls != null && !urls.isEmpty()) {
            return new ProgressiveMediaSource.Factory(http).createMediaSource(
                    androidx.media3.common.MediaItem.fromUri(urls.get(0)));
        }
        throw new IllegalStateException(info.containsSabrFormats()
                ? "Este video solo llega por SABR: falta el lector de SmartTube para ese formato"
                : "El núcleo no devolvió formatos reproducibles");
    }

    private void start(MediaSource source) {
        player.setMediaSource(source);
        player.prepare();
        player.play();
        time.post(tick);
    }

    private void updateProgress() {
        if (player == null) return;
        long duration = player.getDuration(), position = player.getCurrentPosition();
        if (duration > 0) {
            int width = Math.round(dp(320) * Math.min(1f, position / (float) duration));
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) barFill.getLayoutParams();
            if (params.width != width) { params.width = width; barFill.setLayoutParams(params); }
            time.setText(clock(position) + " / " + clock(duration) + " · quedan " + Math.max(0, (duration - position) / 60_000) + " min");
        } else {
            time.setText(player.isCurrentMediaItemLive() ? "En directo" : "");
        }
        Format video = player.getVideoFormat();
        Format audio = player.getAudioFormat();
        if (video != null) {
            StringBuilder line = new StringBuilder();
            line.append(video.width).append(" × ").append(video.height);
            if (video.frameRate > 0) line.append(" · ").append(Math.round(video.frameRate)).append(" fps");
            if (video.codecs != null) line.append(" · ").append(codecName(video.codecs));
            if (audio != null && audio.codecs != null) line.append(" · ").append(codecName(audio.codecs));
            if (video.bitrate > 0) line.append(String.format(Locale.US, " · %.1f Mbps", video.bitrate / 1_000_000f));
            tech.setText(line);
        }
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

    private void showOsd() {
        osd.removeCallbacks(hideOsd);
        osd.animate().alpha(1f).translationY(0).setInterpolator(MainActivity.SPRING).setDuration(420).start();
    }

    private void hideSoon() {
        osd.removeCallbacks(hideOsd);
        osd.postDelayed(hideOsd, 4_000);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                player.setPlayWhenReady(!player.getPlayWhenReady());
                showOsd();
                if (player.getPlayWhenReady()) hideSoon();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                player.seekTo(player.getCurrentPosition() + 10_000);
                showOsd(); hideSoon();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                player.seekTo(Math.max(0, player.getCurrentPosition() - 10_000));
                showOsd(); hideSoon();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_INFO:
                showOsd(); hideSoon();
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) player.pause();
    }

    @Override
    protected void onDestroy() {
        if (loading != null) loading.dispose();
        time.removeCallbacks(tick);
        if (player != null) player.release();
        player = null;
        super.onDestroy();
    }
}
