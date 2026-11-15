package cl.vibe.tube;

import android.content.Context;
import android.graphics.Typeface;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.TextView;

import java.util.Locale;

/**
 * Medidas y estilos de «Flotante minimalista» (VibeM3U/DISENO.md) en la escala del mockup.
 *
 * Todas las medidas de VibeTube están escritas en unidades del mockup (1280 × 720). Una unidad
 * equivale a un píxel del mockup: en una TV de 720p de densidad 1 es 1 dp; en un 1080p (el
 * emulador VibeTV, 320 dpi) es 1,5 px. Así el diseño sale igual en cualquier TV, sin que
 * Android lo escale por su cuenta, y las filas de alto fijo siempre caben.
 */
final class Ui {
    static final int WHITE = 0xFFFFFFFF;
    /** Texto normal: blanco al 62 %. */
    static final int MUTED = 0x9EFFFFFF;
    /** Etiquetas y datos secundarios: blanco al 42 %. */
    static final int DIM = 0x6BFFFFFF;
    /** Eyebrow: blanco al 60 %. */
    static final int EYEBROW = 0x99FFFFFF;
    static final int LIVE = 0xFF5BE9A0;
    static final int NOW = 0xFF00B8D9;
    static final PathInterpolator SPRING = new PathInterpolator(0.34f, 1.42f, 0.64f, 1f);
    static final PathInterpolator SPRING_SOFT = new PathInterpolator(0.34f, 1.3f, 0.64f, 1f);

    private static float unit = 1f;

    private Ui() {}

    /** Fija la escala de la pantalla actual. Se llama al crear cada actividad. */
    static void init(Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        unit = Math.min(metrics.widthPixels / 1280f, metrics.heightPixels / 720f);
        if (unit <= 0f) unit = 1f;
    }

    /** Unidades del mockup a píxeles reales. */
    static int px(float design) {
        return Math.round(design * unit);
    }

    /** Tamaño de letra en sp del mockup, convertido a píxeles de la pantalla. */
    static void size(TextView view, float designSp) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, designSp * unit);
    }

    static TextView text(Context context, float designSp, int color, boolean bold) {
        TextView view = new TextView(context);
        size(view, designSp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        view.setShadowLayer(px(5), 0, px(1), 0x73000000);
        view.setIncludeFontPadding(false);
        return view;
    }

    /** Eyebrow: MAYÚSCULAS, espaciado 0,2 em, una línea. */
    static TextView eyebrow(Context context, String value, int color) {
        TextView view = text(context, 12, color, false);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setLetterSpacing(0.2f);
        view.setMaxLines(1);
        view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        view.setAllCaps(true);
        view.setText(value);
        return view;
    }

    /** Rótulo de fila: mayúsculas, 11 unidades, blanco al 42 %. */
    static TextView label(Context context, String value) {
        TextView view = text(context, 11, DIM, false);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setLetterSpacing(0.2f);
        view.setMaxLines(1);
        view.setAllCaps(true);
        view.setText(value);
        return view;
    }

    /**
     * Texto que se puede elegir con el control. El foco es blanco en negrita con la línea
     * (LINE) o el marco (RING) que viaja hasta él; «selected» lo deja blanco sin foco.
     */
    static TextView option(Context context, int mode, CharSequence value, float designSp) {
        TextView view = text(context, designSp, MUTED, false);
        view.setText(value);
        view.setFocusable(true);
        view.setTag(R.id.focus_mode, mode);
        view.setTag(R.id.option_selected, Boolean.FALSE);
        view.setOnFocusChangeListener((v, focused) ->
                restyle((TextView) v, focused, Boolean.TRUE.equals(v.getTag(R.id.option_selected))));
        return view;
    }

    static void markSelected(TextView view, boolean selected) {
        view.setTag(R.id.option_selected, selected);
        restyle(view, view.hasFocus(), selected);
    }

    static void restyle(TextView view, boolean focused, boolean selected) {
        view.setTextColor(focused || selected ? WHITE : MUTED);
        view.setTypeface(Typeface.create("sans-serif", focused ? Typeface.BOLD : Typeface.NORMAL));
        view.setPivotX(view.getWidth() / 2f);
        view.setPivotY(view.getHeight() * 0.6f);
        view.animate().scaleX(focused ? 1.07f : 1f).scaleY(focused ? 1.07f : 1f)
                .setInterpolator(SPRING).setDuration(350).start();
    }

    /** Entrada escalonada de los hijos directos: sube 22 unidades con resorte, 55 ms entre ellos. */
    static void enter(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            child.setAlpha(0f);
            child.setTranslationY(px(22));
            child.animate().alpha(1f).translationY(0f).setStartDelay(i * 55L)
                    .setDuration(620).setInterpolator(SPRING).start();
        }
    }

    /** 18:42, o 1:02:00 para más de una hora; vacío si no hay duración. */
    static String duration(long ms) {
        if (ms <= 0) return "";
        long seconds = ms / 1000;
        long hours = seconds / 3600;
        long minutes = seconds % 3600 / 60;
        long rest = seconds % 60;
        return hours > 0
                ? String.format(Locale.US, "%d:%02d:%02d", hours, minutes, rest)
                : String.format(Locale.US, "%d:%02d", minutes, rest);
    }

}
