package cl.vibe.tube;

import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

/**
 * Buscar: a la izquierda lo escrito, el teclado propio de letras sueltas, «Espacio · Borrar ·
 * Dictar» y las sugerencias; a la derecha, los resultados en 3 columnas. La búsqueda se dispara
 * 600 ms después de dejar de escribir.
 */
final class SearchView extends FrameLayout {
    static final int DEBOUNCE_MS = 600;
    static final int REQUEST_VOICE = 7;
    private static final String KEYS = "abcdefghijklmnñopqrstuvwxyz123";
    private static final int KEYS_PER_ROW = 7;
    private static final int RESULT_COLUMNS = 3;

    private final Context context;
    private final Tiles.Actions actions;
    private final CompositeDisposable disposables;
    private final StringBuilder query = new StringBuilder();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable debounced = this::search;
    private TextView queryText;
    private View caret;
    private TextView firstKey;
    private LinearLayout suggestions;
    private TextView resultsLabel;
    private LinearLayout results;
    private int sequence;
    private Disposable resultsRequest;
    private Disposable tagsRequest;

    SearchView(Context context, Tiles.Actions actions, CompositeDisposable disposables) {
        super(context);
        this.context = context;
        this.actions = actions;
        this.disposables = disposables;
        buildLeft();
        buildRight();
        startCaret();
    }

    /** Foco al entrar: la primera letra. */
    void focusStart() {
        if (firstKey != null) firstKey.requestFocus();
    }

    /** Lo que dictó el usuario: va al cuadro y se busca enseguida. */
    void setVoice(String text) {
        if (TextUtils.isEmpty(text)) return;
        query.setLength(0);
        query.append(text.trim());
        showQuery();
        handler.removeCallbacks(debounced);
        search();
    }

    private void buildLeft() {
        LinearLayout left = new LinearLayout(context);
        left.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams leftParams = new FrameLayout.LayoutParams(Ui.px(420), -2, Gravity.TOP | Gravity.START);
        leftParams.leftMargin = Ui.px(64);
        leftParams.topMargin = Ui.px(120);
        addView(left, leftParams);

        left.addView(Ui.eyebrow(context, "Buscar", Ui.EYEBROW));

        LinearLayout queryRow = new LinearLayout(context);
        queryRow.setGravity(Gravity.CENTER_VERTICAL);
        queryText = Ui.text(context, 34, Ui.WHITE, true);
        queryText.setLetterSpacing(-0.015f);
        queryText.setSingleLine(true);
        queryText.setEllipsize(TextUtils.TruncateAt.START);
        queryRow.addView(queryText, new LinearLayout.LayoutParams(Ui.px(360), -2));
        caret = new View(context);
        caret.setBackgroundColor(Ui.NOW);
        queryRow.addView(caret, new LinearLayout.LayoutParams(Ui.px(2), Ui.px(34)));
        LinearLayout.LayoutParams queryParams = new LinearLayout.LayoutParams(-2, -2);
        queryParams.topMargin = Ui.px(14);
        left.addView(queryRow, queryParams);
        showQuery();

        for (int start = 0; start < KEYS.length(); start += KEYS_PER_ROW) {
            LinearLayout keyRow = new LinearLayout(context);
            int end = Math.min(KEYS.length(), start + KEYS_PER_ROW);
            for (int i = start; i < end; i++) {
                TextView key = Ui.option(context, Focus.LINE, String.valueOf(KEYS.charAt(i)), 19);
                key.setGravity(Gravity.CENTER);
                key.setSingleLine(true);
                key.setOnClickListener(v -> typed(((TextView) v).getText().toString()));
                LinearLayout.LayoutParams keyParams = new LinearLayout.LayoutParams(Ui.px(34), Ui.px(34));
                if (i < end - 1) keyParams.setMarginEnd(Ui.px(8));
                keyRow.addView(key, keyParams);
                if (firstKey == null) firstKey = key;
            }
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-2, -2);
            rowParams.topMargin = start == 0 ? Ui.px(24) : Ui.px(10);
            left.addView(keyRow, rowParams);
        }

        LinearLayout actionRow = new LinearLayout(context);
        actionRow.addView(action("Espacio", v -> typed(" ")), actionParams(26));
        actionRow.addView(action("Borrar", v -> erase()), actionParams(26));
        actionRow.addView(action("Dictar", v -> dictate()), actionParams(0));
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-2, -2);
        actionsParams.topMargin = Ui.px(18);
        left.addView(actionRow, actionsParams);

        LinearLayout.LayoutParams suggestionsLabelParams = new LinearLayout.LayoutParams(-2, -2);
        suggestionsLabelParams.topMargin = Ui.px(34);
        left.addView(Ui.label(context, "Sugerencias"), suggestionsLabelParams);
        suggestions = new LinearLayout(context);
        suggestions.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams suggestionsParams = new LinearLayout.LayoutParams(-1, -2);
        suggestionsParams.topMargin = Ui.px(12);
        left.addView(suggestions, suggestionsParams);
    }

    private TextView action(String label, View.OnClickListener onClick) {
        TextView view = Ui.option(context, Focus.LINE, label, 15);
        view.setOnClickListener(onClick);
        return view;
    }

    private static LinearLayout.LayoutParams actionParams(int gapEnd) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        if (gapEnd > 0) params.setMarginEnd(Ui.px(gapEnd));
        return params;
    }

    private void buildRight() {
        FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        labelParams.leftMargin = Ui.px(560);
        labelParams.topMargin = Ui.px(120);
        resultsLabel = Ui.label(context, "Resultados");
        addView(resultsLabel, labelParams);

        ScrollView scroll = new ScrollView(context);
        scroll.setFocusable(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, 0, 0, Ui.px(24));
        results = new LinearLayout(context);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setClipChildren(false);
        scroll.addView(results, new ScrollView.LayoutParams(-1, -2));
        FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(Ui.px(668), -1, Gravity.TOP | Gravity.START);
        scrollParams.leftMargin = Ui.px(560);
        scrollParams.topMargin = Ui.px(160);
        addView(scroll, scrollParams);
    }

    /** Cursor cian que parpadea: encendido la mitad del tiempo. */
    private void startCaret() {
        ObjectAnimator blink = ObjectAnimator.ofFloat(caret, View.ALPHA, 1f, 0f);
        blink.setDuration(1000);
        blink.setRepeatCount(ValueAnimator.INFINITE);
        blink.setRepeatMode(ValueAnimator.RESTART);
        blink.setInterpolator((TimeInterpolator) input -> input < 0.5f ? 0f : 1f);
        blink.start();
    }

    private void typed(String text) {
        if (text.equals(" ") && (query.length() == 0 || query.charAt(query.length() - 1) == ' ')) return;
        query.append(text);
        showQuery();
        scheduleSearch();
    }

    private void erase() {
        if (query.length() == 0) return;
        query.setLength(query.length() - 1);
        showQuery();
        scheduleSearch();
    }

    private void dictate() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CL");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "¿Qué quieres ver?");
        try {
            ((android.app.Activity) context).startActivityForResult(intent, REQUEST_VOICE);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(context, "Esta TV no tiene reconocimiento de voz", Toast.LENGTH_SHORT).show();
        }
    }

    private void showQuery() {
        queryText.setText(query.toString());
    }

    private void scheduleSearch() {
        handler.removeCallbacks(debounced);
        if (query.toString().trim().isEmpty()) {
            clearResults();
            return;
        }
        handler.postDelayed(debounced, DEBOUNCE_MS);
    }

    private void clearResults() {
        sequence++;
        cancel();
        suggestions.removeAllViews();
        results.removeAllViews();
        resultsLabel.setText("Resultados");
    }

    private void cancel() {
        if (resultsRequest != null) resultsRequest.dispose();
        if (tagsRequest != null) tagsRequest.dispose();
    }

    private void search() {
        String text = query.toString().trim();
        if (text.isEmpty()) return;
        final int current = ++sequence;
        cancel();
        resultsLabel.setText("Buscando…");
        results.removeAllViews();
        suggestions.removeAllViews();

        resultsRequest = YouTubeServiceManager.instance().getContentService()
                .getSearchObserve(text)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(groups -> {
                    if (current == sequence) showResults(text, groups);
                }, error -> {
                    Log.e(MainActivity.TAG, "Falló la búsqueda", error);
                    if (current == sequence) showFailure(text);
                });
        disposables.add(resultsRequest);

        tagsRequest = YouTubeServiceManager.instance().getContentService()
                .getSearchTagsObserve(text)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(tags -> {
                    if (current == sequence) showSuggestions(text, tags);
                }, error -> Log.w(MainActivity.TAG, "Sin sugerencias", error));
        disposables.add(tagsRequest);
    }

    private void showResults(String text, List<MediaGroup> groups) {
        List<MediaItem> items = new ArrayList<>();
        if (groups != null) {
            for (MediaGroup group : groups) items.addAll(Tiles.items(group));
        }
        results.removeAllViews();
        resultsLabel.setText("Resultados · " + items.size() + (items.size() == 1 ? " video" : " videos"));
        if (items.isEmpty()) {
            TextView none = Ui.text(context, 13, Ui.DIM, false);
            none.setText("Sin resultados para «" + text + "»");
            results.addView(none);
            return;
        }
        LinearLayout row = null;
        for (int i = 0; i < items.size(); i++) {
            if (i % RESULT_COLUMNS == 0) {
                row = new LinearLayout(context);
                row.setClipChildren(false);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
                rowParams.bottomMargin = Ui.px(30);
                results.addView(row, rowParams);
            }
            boolean last = i % RESULT_COLUMNS == RESULT_COLUMNS - 1 || i == items.size() - 1;
            row.addView(Tiles.tile(context, items.get(i), actions, last ? 0 : 22));
        }
    }

    private void showFailure(String text) {
        results.removeAllViews();
        resultsLabel.setText("Resultados");
        TextView retry = Ui.option(context, Focus.LINE, "No se pudo cargar. OK para reintentar.", 15);
        retry.setOnClickListener(v -> {
            query.setLength(0);
            query.append(text);
            showQuery();
            search();
        });
        results.addView(retry);
    }

    private void showSuggestions(String text, List<String> tags) {
        suggestions.removeAllViews();
        if (tags == null) return;
        int shown = 0;
        for (String tag : tags) {
            if (tag == null || tag.isEmpty() || shown == 3) continue;
            shown++;
            SpannableString label = new SpannableString(tag);
            int prefix = Math.min(text.length(), tag.length());
            label.setSpan(new StyleSpan(Typeface.BOLD), 0, prefix, 0);
            TextView option = Ui.option(context, Focus.LINE, label, 15);
            option.setOnClickListener(v -> {
                handler.removeCallbacks(debounced);
                query.setLength(0);
                query.append(tag);
                showQuery();
                search();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
            params.bottomMargin = Ui.px(10);
            suggestions.addView(option, params);
        }
    }
}
