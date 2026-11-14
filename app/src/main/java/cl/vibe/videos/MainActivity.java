package cl.vibe.videos;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.liskovsoft.mediaserviceinterfaces.ContentService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

/**
 * Inicio y búsqueda con el lenguaje «Flotante minimalista» de VibeM3U: el video marcado de fondo,
 * texto que flota sobre una sola capa de degradado, foco = blanco en negrita y bloque levantado.
 */
public class MainActivity extends Activity {
    static final String TAG = "VibeVideos";
    static final PathInterpolator SPRING = new PathInterpolator(0.34f, 1.42f, 0.64f, 1f);
    static final int LIVE = 0xFF5BE9A0;
    static final int NOW = 0xFF00B8D9;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private ImageView hero;
    private TextView heroEyebrow, heroTitle, heroMeta, status, clock;
    private LinearLayout rows;
    private ScrollView rowsScroll;
    private EditText searchBox;
    private TextView tabHome, tabSearch;
    private boolean searchMode;
    private final Runnable clockTick = new Runnable() {
        @Override public void run() {
            clock.setText(new SimpleDateFormat("EEEE d · HH:mm", new Locale("es")).format(new Date()));
            clock.postDelayed(this, 20_000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildLayout());
        clockTick.run();
        loadHome();
    }

    @Override
    protected void onDestroy() {
        disposables.clear();
        super.onDestroy();
    }

    int dp(float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }

    TextView text(float sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create(bold ? "sans-serif" : "sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        view.setShadowLayer(dp(5), 0, dp(1), 0x73000000);
        view.setIncludeFontPadding(false);
        return view;
    }

    TextView eyebrow(String value, int alphaColor) {
        TextView view = text(10.5f, alphaColor, false);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setLetterSpacing(0.2f);
        view.setAllCaps(true);
        view.setText(value);
        view.setSingleLine(true);
        return view;
    }

    private View buildLayout() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        hero = new ImageView(this);
        hero.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(hero, new FrameLayout.LayoutParams(-1, -1));
        // Una sola capa: desde la izquierda (detalle) y desde abajo (filas).
        View left = new View(this);
        left.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xD9000000, 0x8C000000, 0x00000000}));
        root.addView(left, new FrameLayout.LayoutParams(-1, -1));
        View bottom = new View(this);
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xF2000000, 0xB3000000, 0x00000000}));
        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(-1, dp(420), Gravity.BOTTOM);
        root.addView(bottom, bottomParams);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(48), dp(28), 0, 0);
        root.addView(column, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(0, 0, dp(48), 0);
        top.addView(eyebrow("Videos", 0x99FFFFFF));
        tabHome = tab("Inicio", () -> { if (searchMode) { searchMode = false; refreshTabs(); loadHome(); } });
        tabSearch = tab("Buscar", () -> { searchMode = true; refreshTabs(); searchBox.requestFocus(); });
        top.addView(tabHome);
        top.addView(tabSearch);
        View spacer = new View(this);
        top.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        clock = text(13, 0x9EFFFFFF, false);
        top.addView(clock);
        column.addView(top);

        heroEyebrow = eyebrow("", 0x99FFFFFF);
        LinearLayout.LayoutParams eyebrowParams = new LinearLayout.LayoutParams(-2, -2);
        eyebrowParams.topMargin = dp(48);
        column.addView(heroEyebrow, eyebrowParams);
        heroTitle = text(30, Color.WHITE, true);
        heroTitle.setLetterSpacing(-0.015f);
        heroTitle.setMaxLines(2);
        heroTitle.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(dp(560), -2);
        titleParams.topMargin = dp(10);
        column.addView(heroTitle, titleParams);
        heroMeta = text(12, 0x9EFFFFFF, false);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(-2, -2);
        metaParams.topMargin = dp(10);
        column.addView(heroMeta, metaParams);

        searchBox = new EditText(this);
        searchBox.setSingleLine(true);
        searchBox.setHint("Escribe y presiona Enter");
        searchBox.setHintTextColor(0x66FFFFFF);
        searchBox.setTextColor(Color.WHITE);
        searchBox.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        searchBox.setBackground(null);
        searchBox.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchBox.setOnEditorActionListener((view, action, event) -> {
            String query = searchBox.getText().toString().trim();
            if (!query.isEmpty()) search(query);
            return true;
        });
        searchBox.setVisibility(View.GONE);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(dp(560), -2);
        searchParams.topMargin = dp(8);
        column.addView(searchBox, searchParams);

        status = text(13, 0x9EFFFFFF, false);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-2, -2);
        statusParams.topMargin = dp(16);
        column.addView(status, statusParams);

        rowsScroll = new ScrollView(this);
        rowsScroll.setClipChildren(false);
        rowsScroll.setClipToPadding(false);
        rowsScroll.setVerticalScrollBarEnabled(false);
        rowsScroll.setPadding(0, dp(12), 0, dp(40));
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setClipChildren(false);
        rowsScroll.addView(rows, new ScrollView.LayoutParams(-1, -2));
        column.addView(rowsScroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        refreshTabs();
        return root;
    }

    private TextView tab(String label, Runnable action) {
        TextView view = text(14, 0x9EFFFFFF, false);
        view.setText(label);
        view.setFocusable(true);
        view.setPadding(dp(14), dp(6), dp(14), dp(6));
        view.setOnClickListener(v -> action.run());
        view.setOnFocusChangeListener((v, focused) -> styleTab((TextView) v, focused));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.setMarginStart(dp(14));
        view.setLayoutParams(params);
        return view;
    }

    private void styleTab(TextView view, boolean focused) {
        boolean selected = (view == tabSearch) == searchMode;
        view.setTextColor(focused || selected ? Color.WHITE : 0x9EFFFFFF);
        view.setTypeface(Typeface.create("sans-serif", focused || selected ? Typeface.BOLD : Typeface.NORMAL));
        // Foco = línea fina blanca debajo del texto, nunca un fondo.
        GradientDrawable line = new GradientDrawable();
        line.setColor(Color.WHITE);
        view.setForeground(focused ? underline() : null);
        view.animate().scaleX(focused ? 1.07f : 1f).scaleY(focused ? 1.07f : 1f)
                .setInterpolator(SPRING).setDuration(350).start();
    }

    private android.graphics.drawable.Drawable underline() {
        GradientDrawable bar = new GradientDrawable();
        bar.setColor(Color.WHITE);
        android.graphics.drawable.LayerDrawable layer = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{bar});
        layer.setLayerGravity(0, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL);
        layer.setLayerHeight(0, dp(2));
        layer.setLayerInsetLeft(0, dp(14));
        layer.setLayerInsetRight(0, dp(14));
        return layer;
    }

    private void refreshTabs() {
        styleTab(tabHome, tabHome.hasFocus());
        styleTab(tabSearch, tabSearch.hasFocus());
        searchBox.setVisibility(searchMode ? View.VISIBLE : View.GONE);
    }

    private ContentService content() {
        return YouTubeServiceManager.instance().getContentService();
    }

    private void loadHome() {
        rows.removeAllViews();
        status.setText("Cargando el inicio…");
        disposables.add(content().getHomeObserve()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::addGroups, error -> showError("el inicio", error), this::loaded));
    }

    private void search(String query) {
        rows.removeAllViews();
        status.setText("Buscando «" + query + "»…");
        disposables.add(content().getSearchObserve(query)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::addGroups, error -> showError("la búsqueda", error), this::loaded));
    }

    private void loaded() {
        if (rows.getChildCount() == 0) status.setText("No llegó nada. Revisa la conexión o prueba de nuevo.");
        else status.setText("");
    }

    private void showError(String what, Throwable error) {
        Log.e(TAG, "Falló " + what, error);
        status.setText("No se pudo cargar " + what + ": " + error.getClass().getSimpleName()
                + (error.getMessage() != null ? " · " + error.getMessage() : ""));
    }

    private void addGroups(List<MediaGroup> groups) {
        if (groups == null) return;
        for (MediaGroup group : groups) addGroup(group);
        if (rows.getChildCount() > 0) status.setText("");
    }

    private void addGroup(MediaGroup group) {
        if (group == null || group.getMediaItems() == null) return;
        LinearLayout track = new LinearLayout(this);
        track.setClipChildren(false);
        track.setPadding(0, dp(6), dp(48), dp(6));
        int added = 0;
        for (MediaItem item : group.getMediaItems()) {
            if (item == null || TextUtils.isEmpty(item.getVideoId())) continue;
            track.addView(tile(item));
            added++;
        }
        if (added == 0) return;
        boolean first = rows.getChildCount() == 0;
        String title = TextUtils.isEmpty(group.getTitle()) ? (searchMode ? "Resultados" : "Para ti") : group.getTitle();
        TextView label = eyebrow(title, 0x6BFFFFFF);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-2, -2);
        labelParams.topMargin = first ? 0 : dp(18);
        labelParams.bottomMargin = dp(8);
        rows.addView(label, labelParams);
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipChildren(false);
        scroller.setClipToPadding(false);
        scroller.addView(track);
        rows.addView(scroller, new LinearLayout.LayoutParams(-1, -2));
        if (first) {
            View firstTile = track.getChildAt(0);
            firstTile.post(firstTile::requestFocus);
        }
    }

    private View tile(MediaItem item) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setFocusable(true);
        tile.setClipChildren(false);
        LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(dp(208), -2);
        tileParams.setMarginEnd(dp(18));
        tile.setLayoutParams(tileParams);

        FrameLayout thumb = new FrameLayout(this);
        GradientDrawable thumbShape = new GradientDrawable();
        thumbShape.setColor(0xFF1A1F27);
        thumbShape.setCornerRadius(dp(4));
        thumb.setBackground(thumbShape);
        thumb.setClipToOutline(true);
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.addView(image, new FrameLayout.LayoutParams(-1, -1));
        Images.load(image, item.getCardImageUrl(), dp(240));
        TextView badge = text(10.5f, Color.WHITE, true);
        badge.setText(item.isLive() ? "● EN VIVO" : (item.getBadgeText() != null ? item.getBadgeText() : ""));
        if (item.isLive()) badge.setTextColor(LIVE);
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.BOTTOM);
        badgeParams.setMargins(0, 0, dp(7), dp(5));
        thumb.addView(badge, badgeParams);
        if (item.getPercentWatched() > 0) {
            View progress = new View(this);
            progress.setBackgroundColor(NOW);
            FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                    dp(208) * Math.min(100, item.getPercentWatched()) / 100, dp(3), Gravity.BOTTOM);
            thumb.addView(progress, progressParams);
        }
        tile.addView(thumb, new LinearLayout.LayoutParams(dp(208), dp(117)));

        TextView caption = text(12, 0x9EFFFFFF, false);
        caption.setText(item.getTitle());
        caption.setSingleLine(true);
        caption.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(-1, -2);
        captionParams.topMargin = dp(9);
        tile.addView(caption, captionParams);
        TextView second = text(10.5f, 0x6BFFFFFF, false);
        second.setText(item.getSecondTitle() != null ? item.getSecondTitle() : item.getAuthor());
        second.setSingleLine(true);
        second.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams secondParams = new LinearLayout.LayoutParams(-1, -2);
        secondParams.topMargin = dp(3);
        tile.addView(second, secondParams);

        tile.setOnFocusChangeListener((view, focused) -> {
            // El bloque con foco se levanta 6 % con resorte, como en la Guía de VibeM3U.
            thumb.setPivotX(dp(104));
            thumb.setPivotY(dp(117));
            thumb.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f)
                    .translationZ(focused ? dp(14) : 0).setInterpolator(SPRING).setDuration(350).start();
            caption.setTextColor(focused ? Color.WHITE : 0x9EFFFFFF);
            caption.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            if (focused) showHero(item);
        });
        tile.setOnClickListener(view -> play(item));
        return tile;
    }

    private void showHero(MediaItem item) {
        heroTitle.setText(item.getTitle());
        CharSequence second = item.getSecondTitle();
        heroEyebrow.setText(item.isLive() ? "● En vivo · " + safe(item.getAuthor()) : safe(second != null ? second.toString() : item.getAuthor()));
        heroEyebrow.setTextColor(item.isLive() ? LIVE : 0x99FFFFFF);
        heroMeta.setText(item.getBadgeText() != null ? item.getBadgeText() : "");
        String background = item.getBackgroundImageUrl() != null ? item.getBackgroundImageUrl() : item.getCardImageUrl();
        Images.load(hero, background, dp(960));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private void play(MediaItem item) {
        Intent intent = new Intent(this, PlayerActivity.class)
                .putExtra(PlayerActivity.EXTRA_VIDEO_ID, item.getVideoId())
                .putExtra(PlayerActivity.EXTRA_TITLE, item.getTitle())
                .putExtra(PlayerActivity.EXTRA_AUTHOR, item.getAuthor());
        startActivity(intent);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && searchMode) {
            searchMode = false;
            refreshTabs();
            loadHome();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
