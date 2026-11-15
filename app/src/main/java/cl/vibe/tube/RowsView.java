package cl.vibe.tube;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

/**
 * Filas de tarjetas de una pantalla. Cada fila tiene alto fijo (rótulo 14 + 12 + tarjetas 161 =
 * 187) para que las filas nunca se pisen: si no caben, la columna se desplaza.
 */
final class RowsView extends FrameLayout {
    /** Una fila que se llena con una lista de videos. */
    interface Source {
        Observable<List<MediaItem>> items();
    }

    /** Varias filas, una por grupo que devuelve el núcleo. */
    interface GroupsSource {
        Observable<List<MediaGroup>> groups();
    }

    static final int ROW_HEIGHT = 187;
    static final int STRIP_HEIGHT = 161;
    static final int ROW_GAP = 8;
    static final int TILE_GAP = 20;

    private final Context context;
    private final Tiles.Actions actions;
    private final CompositeDisposable disposables;
    private final LinearLayout column;
    private final List<Row> rows = new ArrayList<>();
    private boolean wantFocus;

    /**
     * @param topDesign    arriba de la columna, en unidades del mockup (340 en Inicio).
     * @param heightDesign alto de la zona de filas, en unidades del mockup.
     */
    RowsView(Context context, Tiles.Actions actions, CompositeDisposable disposables, int topDesign, int heightDesign) {
        super(context);
        this.context = context;
        this.actions = actions;
        this.disposables = disposables;
        ScrollView scroll = new ScrollView(context);
        // Los contenedores no toman el foco: solo las tarjetas y el texto elegible.
        scroll.setFocusable(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        // Sin relleno inferior: si la columna cabe en la zona, no se desplaza al enfocar y OK/Arriba
        // mueven el foco y no el contenido.
        column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setClipChildren(false);
        scroll.addView(column, new ScrollView.LayoutParams(-1, -2));
        LayoutParams params = new LayoutParams(-1, Ui.px(heightDesign));
        params.leftMargin = Ui.px(64);
        params.topMargin = Ui.px(topDesign);
        addView(scroll, params);
    }

    /** Agrega una fila vacía (con «Cargando…») al final. */
    Row row(String label, boolean live) {
        Row row = new Row(label, live);
        rows.add(row);
        column.addView(row.root, new LinearLayout.LayoutParams(-1, Ui.px(ROW_HEIGHT)));
        relayout();
        return row;
    }

    void remove(Row row) {
        rows.remove(row);
        column.removeView(row.root);
        relayout();
    }

    /** Una fila por grupo, en el lugar de la fila marcador. */
    void groups(GroupsSource source, boolean live) {
        attachGroups(row("", live), source, live);
    }

    private void attachGroups(Row marker, GroupsSource source, boolean live) {
        marker.loading();
        disposables.add(source.groups()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(groups -> {
                    List<MediaGroup> shown = new ArrayList<>();
                    List<List<MediaItem>> contents = new ArrayList<>();
                    if (groups != null) {
                        for (MediaGroup group : groups) {
                            List<MediaItem> items = Tiles.items(group);
                            if (!items.isEmpty()) {
                                shown.add(group);
                                contents.add(items);
                            }
                        }
                    }
                    if (shown.isEmpty()) {
                        marker.message("Sin videos por ahora.");
                        return;
                    }
                    int at = rows.indexOf(marker);
                    remove(marker);
                    if (at < 0) at = rows.size();
                    for (int i = 0; i < shown.size(); i++) {
                        Row row = new Row(label(shown.get(i)), live);
                        rows.add(at, row);
                        column.addView(row.root, at, new LinearLayout.LayoutParams(-1, Ui.px(ROW_HEIGHT)));
                        row.fill(contents.get(i));
                        at++;
                    }
                    relayout();
                    focusIfWanted();
                }, error -> {
                    Log.e(MainActivity.TAG, "Falló un grupo de filas", error);
                    marker.fail(() -> attachGroups(marker, source, live));
                }));
    }

    private static String label(MediaGroup group) {
        String title = group.getTitle();
        return title == null || title.isEmpty() ? "Videos" : title;
    }

    /** Pone el foco en la primera tarjeta; si todavía no hay ninguna, la pone al llegar. */
    void focusFirst() {
        for (Row row : rows) {
            if (row.strip.getChildCount() > 0 && row.strip.getChildAt(0).isFocusable()) {
                row.strip.getChildAt(0).requestFocus();
                wantFocus = false;
                return;
            }
        }
        wantFocus = true;
    }

    void cancelFocus() {
        wantFocus = false;
    }

    /** Las filas entran en escalera, como en el resto de la app. */
    void enter() {
        Ui.enter(column);
    }

    private void focusIfWanted() {
        if (!wantFocus) return;
        for (Row row : rows) {
            if (row.strip.getChildCount() > 0 && row.strip.getChildAt(0).isFocusable()) {
                wantFocus = false;
                row.strip.getChildAt(0).requestFocus();
                return;
            }
        }
    }

    private void relayout() {
        for (int i = 0; i < column.getChildCount(); i++) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) column.getChildAt(i).getLayoutParams();
            params.topMargin = i == 0 ? 0 : Ui.px(ROW_GAP);
            column.getChildAt(i).setLayoutParams(params);
        }
    }

    /** Una fila: rótulo arriba y, debajo, las tarjetas que se desplazan con el foco. */
    final class Row {
        final LinearLayout root;
        final LinearLayout strip;
        final String label;

        Row(String label, boolean live) {
            this.label = label;
            root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setClipChildren(false);

            LinearLayout head = new LinearLayout(context);
            head.setGravity(Gravity.CENTER_VERTICAL);
            if (live) {
                View dot = new View(context);
                GradientDrawable shape = new GradientDrawable();
                shape.setShape(GradientDrawable.OVAL);
                shape.setColor(Ui.LIVE);
                dot.setBackground(shape);
                LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(Ui.px(7), Ui.px(7));
                dotParams.setMarginEnd(Ui.px(8));
                head.addView(dot, dotParams);
            }
            head.addView(Ui.label(context, label), new LinearLayout.LayoutParams(-2, -2));
            root.addView(head, new LinearLayout.LayoutParams(-1, Ui.px(14)));

            HorizontalScrollView scroller = new HorizontalScrollView(context);
            scroller.setFocusable(false);
            scroller.setHorizontalScrollBarEnabled(false);
            scroller.setClipChildren(false);
            scroller.setClipToPadding(false);
            strip = new LinearLayout(context);
            strip.setOrientation(LinearLayout.HORIZONTAL);
            strip.setClipChildren(false);
            scroller.addView(strip);
            LinearLayout.LayoutParams stripParams = new LinearLayout.LayoutParams(-1, Ui.px(STRIP_HEIGHT));
            stripParams.topMargin = Ui.px(12);
            root.addView(scroller, stripParams);
        }

        void loading() {
                        message("Cargando…");
        }

        void message(String text) {
            strip.removeAllViews();
            TextView view = Ui.text(context, 13, Ui.DIM, false);
            view.setText(text);
            strip.addView(view);
        }

        void fill(List<MediaItem> items) {
            // Si el foco estaba en esta fila, vuelve a la primera tarjeta nueva (el núcleo a veces
            // entrega la misma fila dos veces).
            boolean hadFocus = strip.hasFocus();
            strip.removeAllViews();
            if (items.isEmpty()) {
                message("Sin videos por ahora.");
                return;
            }
            for (MediaItem item : items) {
                strip.addView(Tiles.tile(context, item, actions, TILE_GAP));
            }
            if (hadFocus) strip.getChildAt(0).requestFocus();
        }

        /** Error de la fila: una línea que se puede marcar; OK vuelve a pedir los datos. */
        void fail(Runnable retry) {
            strip.removeAllViews();
            TextView view = Ui.option(context, Focus.LINE, "No se pudo cargar. OK para reintentar.", 15);
            view.setOnClickListener(v -> retry.run());
            strip.addView(view);
        }

        void load(Source source) {
            loading();
            final boolean[] received = {false};
            disposables.add(source.items()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(items -> {
                        received[0] = true;
                        fill(items);
                        focusIfWanted();
                    }, error -> {
                        Log.e(MainActivity.TAG, "Falló una fila", error);
                        fail(() -> load(source));
                    }, () -> {
                        // El núcleo a veces termina sin datos (p. ej. Tendencias): la fila no queda cargando.
                        if (!received[0]) {
                            fill(new ArrayList<MediaItem>());
                            focusIfWanted();
                        }
                    }));
        }
    }
}
