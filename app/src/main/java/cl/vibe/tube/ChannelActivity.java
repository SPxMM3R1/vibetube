package cl.vibe.tube;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

/**
 * Canal: el último video de fondo, nombre grande y acciones en una línea. Debajo, «MÁS RECIENTES»
 * y «MÁS VISTOS». Se abre con la tecla Menú o manteniendo OK sobre una tarjeta.
 */
public class ChannelActivity extends Activity implements Tiles.Actions {
    static final String EXTRA_CHANNEL_ID = "channel_id";
    static final String EXTRA_NAME = "name";
    static final String EXTRA_VIDEO_ID = "video_id";
    static final String EXTRA_IMAGE = "image";
    /** Debajo de las acciones del encabezado. */
    private static final int ROWS_TOP = 250;
    private static final int ROWS_HEIGHT = 470;

    private final CompositeDisposable disposables = new CompositeDisposable();
    /** Descarta el ACTION_UP de OK (o de la pulsación larga) que abrió esta pantalla. */
    private final ClickGuard clicks = new ClickGuard();
    private String channelId;
    private String videoId;
    private FrameLayout root;
    private ImageView hero;
    private TextView subscribe;
    private View subscribedDot;
    private TextView hideAction;
    private RowsView rows;
    private RowsView.Row recent;
    private RowsView.Row most;
    private Focus focus;
    /** Si la cuenta ya está suscrita a este canal. */
    private boolean subscribed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        channelId = getIntent().getStringExtra(EXTRA_CHANNEL_ID);
        videoId = getIntent().getStringExtra(EXTRA_VIDEO_ID);
        String name = getIntent().getStringExtra(EXTRA_NAME);
        String image = getIntent().getStringExtra(EXTRA_IMAGE);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        hero = new ImageView(this);
        hero.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(hero, new FrameLayout.LayoutParams(-1, -1));
        View shadeLeft = new View(this);
        shadeLeft.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xD9000000, 0x8C000000, 0x00000000}));
        root.addView(shadeLeft, new FrameLayout.LayoutParams(-1, -1));
        View shadeBottom = new View(this);
        shadeBottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xF2000000, 0xB3000000, 0x00000000}));
        root.addView(shadeBottom, new FrameLayout.LayoutParams(-1, Ui.px(430), Gravity.BOTTOM));

        rows = new RowsView(this, this, disposables, ROWS_TOP, ROWS_HEIGHT);
        root.addView(rows, new FrameLayout.LayoutParams(-1, -1));
        recent = rows.row("Más recientes", false);
        most = rows.row("Más vistos", false);

        root.addView(header(TextUtils.isEmpty(name) ? "Canal" : name), headerParams());
        setContentView(root);
        focus = new Focus(root);
        root.getViewTreeObserver().addOnGlobalFocusChangeListener(focus);
        Images.load(hero, image, Ui.px(960));

        fetch();
        loadSubscription();
    }

    @Override
    protected void onDestroy() {
        disposables.clear();
        super.onDestroy();
    }

    /** Descarta el ACTION_UP de OK que llega sin su DOWN (la tecla que abrió esta pantalla). */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (clicks.discard(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    private LinearLayout header(String name) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.addView(Ui.eyebrow(this, "Canal", Ui.EYEBROW));

        TextView title = Ui.text(this, 52, Ui.WHITE, true);
        title.setLetterSpacing(-0.02f);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setText(name);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = Ui.px(10);
        header.addView(title, titleParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout subscribeBox = new LinearLayout(this);
        subscribeBox.setGravity(Gravity.CENTER_VERTICAL);
        subscribe = Ui.option(this, Focus.LINE, "Suscribirse", 16);
        subscribe.setOnClickListener(v -> toggleSubscription());
        subscribeBox.addView(subscribe, new LinearLayout.LayoutParams(-2, -2));
        subscribedDot = new View(this);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(Ui.LIVE);
        subscribedDot.setBackground(dot);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(Ui.px(7), Ui.px(7));
        dotParams.setMarginStart(Ui.px(8));
        subscribedDot.setVisibility(View.GONE);
        subscribeBox.addView(subscribedDot, dotParams);
        actions.addView(subscribeBox, actionParams());

        TextView videos = Ui.option(this, Focus.LINE, "Videos", 16);
        videos.setOnClickListener(v -> rows.focusFirst());
        actions.addView(videos, actionParams());
        TextView live = Ui.option(this, Focus.LINE, "En vivo", 16);
        live.setOnClickListener(v -> pending());
        actions.addView(live, actionParams());
        TextView lists = Ui.option(this, Focus.LINE, "Listas", 16);
        lists.setOnClickListener(v -> pending());
        actions.addView(lists, actionParams());

        hideAction = Ui.option(this, Focus.LINE, "", 16);
        hideAction.setOnClickListener(v -> toggleHidden());
        actions.addView(hideAction, actionParams());
        showHideText();

        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-2, -2);
        actionsParams.topMargin = Ui.px(22);
        header.addView(actions, actionsParams);
        return header;
    }

    private static LinearLayout.LayoutParams actionParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.setMarginEnd(Ui.px(30));
        return params;
    }

    private FrameLayout.LayoutParams headerParams() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(Ui.px(900), -2, Gravity.TOP | Gravity.START);
        params.setMargins(Ui.px(64), Ui.px(52), 0, 0);
        return params;
    }

    private void fetch() {
        recent.loading();
        most.loading();
        disposables.add(YouTubeServiceManager.instance().getContentService()
                .getChannelObserve(channelId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(groups -> {
                    // Los grupos que son solo transmisiones en vivo no son «más recientes»: van a «En vivo».
                    List<List<MediaItem>> videos = new ArrayList<>();
                    if (groups != null) {
                        for (MediaGroup group : groups) {
                            List<MediaItem> items = Tiles.items(group);
                            if (!items.isEmpty() && !allLive(items)) videos.add(items);
                        }
                    }
                    recent.fill(videos.isEmpty() ? new ArrayList<MediaItem>() : videos.get(0));
                    if (videos.size() > 1) {
                        most.fill(videos.get(1));
                    } else {
                        rows.remove(most);
                    }
                    rows.focusFirst();
                }, error -> {
                    Log.e(MainActivity.TAG, "Falló el canal " + channelId, error);
                    recent.fail(this::fetch);
                    most.fail(this::fetch);
                }));
    }

    private static boolean allLive(List<MediaItem> items) {
        for (MediaItem item : items) {
            if (!item.isLive()) return false;
        }
        return true;
    }

    /** Suscrito o no, según la metadata del video de origen. Sin cuenta siempre es «Suscribirse». */
    private void loadSubscription() {
        if (TextUtils.isEmpty(videoId)) return;
        disposables.add(YouTubeServiceManager.instance().getMediaItemService().getMetadataObserve(videoId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(meta -> {
                    MediaItemMetadata data = meta;
                    subscribed = data != null && data.isSubscribed();
                    renderSubscription();
                }, error -> Log.w(MainActivity.TAG, "Sin estado de suscripción", error)));
    }

    /**
     * Sin cuenta, «Suscribirse» lleva a la pantalla de Cuenta. Con cuenta es un estado: se suscribe
     * o se quita, y se marca «Suscrito» con el punto verde.
     */
    private void toggleSubscription() {
        if (!Session.isSigned()) {
            startActivity(new Intent(this, AccountActivity.class));
            return;
        }
        if (TextUtils.isEmpty(channelId)) return;
        MediaItemService service = YouTubeServiceManager.instance().getMediaItemService();
        Observable<Void> call = subscribed
                ? service.unsubscribeObserve(channelId)
                : service.subscribeObserve(channelId);
        disposables.add(call.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(ignored -> {
                }, error -> {
                    Log.w(MainActivity.TAG, "No se cambió la suscripción", error);
                    Toast.makeText(this, "No se pudo cambiar la suscripción", Toast.LENGTH_SHORT).show();
                }, () -> {
                    subscribed = !subscribed;
                    renderSubscription();
                }));
    }

    private void renderSubscription() {
        subscribe.setText(subscribed ? "Suscrito" : "Suscribirse");
        Ui.markSelected(subscribe, subscribed);
        subscribedDot.setVisibility(subscribed ? View.VISIBLE : View.GONE);
    }

    private void toggleHidden() {
        boolean nowHidden = Hidden.toggle(channelId);
        showHideText();
        Toast.makeText(this, nowHidden ? "Oculto en esta TV" : "Visible otra vez en esta TV", Toast.LENGTH_SHORT).show();
    }

    private void showHideText() {
        boolean hidden = Hidden.isHidden(channelId);
        hideAction.setText(hidden ? "Mostrar en esta TV" : "Ocultar en esta TV");
    }

    private void pending() {
        Toast.makeText(this, "Pronto", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void focused(MediaItem item) {
        // La portada del canal no cambia con el foco: el fondo es el último video.
    }

    @Override
    public void play(MediaItem item) {
        Intent intent = new Intent(this, PlayerActivity.class)
                .putExtra(PlayerActivity.EXTRA_VIDEO_ID, item.getVideoId())
                .putExtra(PlayerActivity.EXTRA_TITLE, item.getTitle())
                .putExtra(PlayerActivity.EXTRA_AUTHOR, item.getAuthor());
        startActivity(intent);
    }

    @Override
    public void channel(MediaItem item) {
        if (TextUtils.isEmpty(item.getChannelId()) || item.getChannelId().equals(channelId)) return;
        startActivity(new Intent(this, ChannelActivity.class)
                .putExtra(EXTRA_CHANNEL_ID, item.getChannelId())
                .putExtra(EXTRA_NAME, item.getAuthor())
                .putExtra(EXTRA_VIDEO_ID, item.getVideoId())
                .putExtra(EXTRA_IMAGE, item.getCardImageUrl()));
    }
}
