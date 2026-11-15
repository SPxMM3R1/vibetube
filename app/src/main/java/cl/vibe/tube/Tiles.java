package cl.vibe.tube;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Tarjeta de video (208 × 117) y el filtrado común de los datos del núcleo. */
final class Tiles {
    static final int WIDTH = 208;
    static final int THUMB_HEIGHT = 117;
    /** Alto de la tarjeta entera: miniatura, 10 de aire y dos líneas de 17. */
    static final int HEIGHT = 161;

    /** Lo que pasa cuando el control marca, elige o mantiene una tarjeta. */
    interface Actions {
        void focused(MediaItem item);
        void play(MediaItem item);
        void channel(MediaItem item);
    }

    private Tiles() {}

    /** Se muestra solo si es un video y su canal no está oculto en esta TV. */
    static boolean visible(MediaItem item) {
        return item != null && !TextUtils.isEmpty(item.getVideoId()) && !Hidden.isHidden(item.getChannelId());
    }

    static List<MediaItem> items(MediaGroup group) {
        List<MediaItem> out = new ArrayList<>();
        if (group == null || group.getMediaItems() == null) return out;
        for (MediaItem item : group.getMediaItems()) {
            if (visible(item)) out.add(item);
        }
        return out;
    }

    /** Las filas de Tendencias o En vivo traen varios grupos: la portada usa el primero. */
    static List<MediaItem> firstItems(List<MediaGroup> groups) {
        if (groups == null || groups.isEmpty()) return new ArrayList<>();
        return items(groups.get(0));
    }

    static List<MediaGroup> single(MediaGroup group) {
        return Collections.singletonList(group);
    }

    static LinearLayout tile(Context context, MediaItem item, Actions actions, int gapEnd) {
        LinearLayout tile = new LinearLayout(context);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setFocusable(true);
        tile.setClipChildren(false);
        tile.setTag(R.id.focus_mode, Focus.RING);
        LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(Ui.px(WIDTH), -2);
        tileParams.setMarginEnd(Ui.px(gapEnd));
        tile.setLayoutParams(tileParams);

        FrameLayout thumb = new FrameLayout(context);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(0xFF1A1F27);
        shape.setCornerRadius(Ui.px(4));
        thumb.setBackground(shape);
        thumb.setClipToOutline(true);
        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.addView(image, new FrameLayout.LayoutParams(-1, -1));
        Images.load(image, item.getCardImageUrl(), Ui.px(240));

        // La duración va sobre la miniatura, abajo a la derecha; en vivo, «● EN VIVO» en verde.
        TextView badge = Ui.text(context, 12, Ui.WHITE, false);
        badge.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        if (item.isLive()) {
            badge.setText("● EN VIVO");
            badge.setTextColor(Ui.LIVE);
        } else {
            badge.setText(Ui.duration(item.getDurationMs()));
        }
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.BOTTOM);
        badgeParams.setMargins(0, 0, Ui.px(8), Ui.px(6));
        thumb.addView(badge, badgeParams);

        // Avance: barra cian de 3 unidades en el borde inferior.
        if (item.getPercentWatched() > 0) {
            View progress = new View(context);
            progress.setBackgroundColor(Ui.NOW);
            int percent = Math.min(100, item.getPercentWatched());
            thumb.addView(progress, new FrameLayout.LayoutParams(
                    Ui.px(WIDTH) * percent / 100, Ui.px(3), Gravity.BOTTOM));
        }
        tile.addView(thumb, new LinearLayout.LayoutParams(Ui.px(WIDTH), Ui.px(THUMB_HEIGHT)));

        String second = item.getSecondTitle() != null ? item.getSecondTitle().toString() : item.getAuthor();
        TextView caption = Ui.text(context, 13, Ui.MUTED, false);
        caption.setText(item.getTitle());
        caption.setSingleLine(true);
        caption.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(-1, Ui.px(17));
        captionParams.topMargin = Ui.px(10);
        tile.addView(caption, captionParams);

        TextView detail = Ui.text(context, 11, Ui.DIM, false);
        detail.setText(second == null ? "" : second);
        detail.setSingleLine(true);
        detail.setEllipsize(TextUtils.TruncateAt.END);
        tile.addView(detail, new LinearLayout.LayoutParams(-1, Ui.px(17)));

        tile.setOnFocusChangeListener((view, focused) -> {
            // El bloque con foco se levanta 6 % con el pivote abajo, como en el mockup.
            thumb.setPivotX(Ui.px(WIDTH / 2f));
            thumb.setPivotY(Ui.px(THUMB_HEIGHT));
            thumb.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f)
                    .setInterpolator(Ui.SPRING).setDuration(350).start();
            caption.setTextColor(focused ? Ui.WHITE : Ui.MUTED);
            caption.setTypeface(Typeface.create("sans-serif-medium", focused ? Typeface.BOLD : Typeface.NORMAL));
            if (focused && actions != null) actions.focused(item);
        });
        tile.setOnClickListener(view -> {
            if (actions != null) actions.play(item);
        });
        tile.setOnLongClickListener(view -> {
            if (actions != null) actions.channel(item);
            return true;
        });
        // Tecla Menú del control: abre el canal del video marcado.
        tile.setOnKeyListener((view, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_MENU && event.getAction() == KeyEvent.ACTION_UP) {
                if (actions != null) actions.channel(item);
                return true;
            }
            return false;
        });
        return tile;
    }
}
