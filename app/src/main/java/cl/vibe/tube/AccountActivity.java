package cl.vibe.tube;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.liskovsoft.googlecommon.common.models.auth.UserCode;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;

import java.util.List;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

/**
 * Cuenta (pantalla de la v2 del mockup, la única que la v1 no tenía). Sin cuenta: la dirección y
 * el código en grande; se confirma en el teléfono y la TV entra sola. Con cuenta: el nombre con
 * punto verde, las cuentas guardadas (se elige una) y «Cerrar sesión» en rosa.
 */
public class AccountActivity extends Activity {
    private static final String TAG = MainActivity.TAG;
    private static final int DANGER = 0xFFFF9A9A;

    private final CompositeDisposable disposables = new CompositeDisposable();
    /** Descarta el ACTION_UP de OK que abrió esta pantalla (clic fantasma). */
    private final ClickGuard clicks = new ClickGuard();
    private FrameLayout root;
    private LinearLayout body;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        root = new FrameLayout(this);
        // Las acciones crecen al tener el foco (1,07×): ningún contenedor las recorta.
        root.setClipChildren(false);
        // Sin video de fondo: un degradado oscuro en lugar de la miniatura del mockup.
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF1A2230, 0xFF07090C}));

        TextView eyebrow = Ui.eyebrow(this, "Cuenta", Ui.EYEBROW);
        FrameLayout.LayoutParams eyebrowParams = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        eyebrowParams.setMargins(Ui.px(64), Ui.px(40), 0, 0);
        root.addView(eyebrow, eyebrowParams);

        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setClipChildren(false);
        body.setClipToPadding(false);
        FrameLayout.LayoutParams bodyParams = new FrameLayout.LayoutParams(Ui.px(760), -2, Gravity.TOP | Gravity.START);
        bodyParams.setMargins(Ui.px(64), Ui.px(150), 0, 0);
        root.addView(body, bodyParams);

        setContentView(root);
        root.getViewTreeObserver().addOnGlobalFocusChangeListener(new Focus(root));
        if (Session.isSigned()) showAccounts();
        else startSignIn();
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

    /** Pide un código nuevo al servicio y lo muestra. */
    private void startSignIn() {
        body.removeAllViews();
        addText("Pidiendo el código…", 17, Ui.MUTED, false, 0);
        disposables.add(Observable.fromCallable(Session::requestCode)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::showCode, error -> {
                    Log.e(TAG, "No se pidió el código de cuenta", error);
                    showError();
                }));
    }

    /** La dirección y el código en la TV; en segundo plano espera la confirmación. */
    private void showCode(UserCode code) {
        body.removeAllViews();
        addText("En tu teléfono o computador entra a", 17, Ui.MUTED, false, 0);
        addText(shortUrl(code.getVerificationUrl()), 34, Ui.WHITE, true, 6);
        addText("y escribe este código", 17, Ui.MUTED, false, 34);
        TextView codeView = addText(code.getUserCode(), 84, Ui.WHITE, false, 10);
        codeView.setLetterSpacing(0.12f);
        codeView.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));

        LinearLayout waiting = new LinearLayout(this);
        waiting.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(this);
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(Ui.NOW);
        dot.setBackground(shape);
        waiting.addView(dot, new LinearLayout.LayoutParams(Ui.px(7), Ui.px(7)));
        TextView waitingText = Ui.text(this, 13, Ui.DIM, false);
        waitingText.setText("Esperando la confirmación. La TV entra sola cuando termines.");
        LinearLayout.LayoutParams waitingTextParams = new LinearLayout.LayoutParams(-2, -2);
        waitingTextParams.setMarginStart(Ui.px(10));
        waiting.addView(waitingText, waitingTextParams);
        LinearLayout.LayoutParams waitingParams = new LinearLayout.LayoutParams(-2, -2);
        waitingParams.topMargin = Ui.px(30);
        body.addView(waiting, waitingParams);

        LinearLayout actions = actionRow(40);
        action(actions, "Seguir sin cuenta", 18, this::finish);

        focusFirst();
        // Espera la confirmación en segundo plano; al confirmar, la cuenta queda guardada y se vuelve.
        disposables.add(Observable.fromCallable(() -> {
                    Session.waitForConfirmation(code);
                    return true;
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(done -> Session.isSignedObserve().subscribe(signed -> {
                    if (signed) finish();
                }), error -> {
                    Log.w(TAG, "El código no se confirmó", error);
                    showExpired();
                }));
    }

    private void showError() {
        body.removeAllViews();
        addText("No se pudo pedir el código.", 17, Ui.MUTED, false, 0);
        addText("Revisa la conexión a internet y vuelve a intentarlo.", 13, Ui.DIM, false, 10);
        LinearLayout actions = actionRow(34);
        action(actions, "Reintentar", 18, this::startSignIn);
        action(actions, "Seguir sin cuenta", 18, this::finish);
        focusFirst();
    }

    private void showExpired() {
        body.removeAllViews();
        addText("El código venció o no se confirmó.", 17, Ui.MUTED, false, 0);
        addText("Pide otro y vuelve a escribirlo en tu teléfono.", 13, Ui.DIM, false, 10);
        LinearLayout actions = actionRow(34);
        action(actions, "Pedir otro código", 18, this::startSignIn);
        action(actions, "Seguir sin cuenta", 18, this::finish);
        focusFirst();
    }

    /** Con sesión: la cuenta activa, las guardadas (se elige una) y las acciones. */
    private void showAccounts() {
        body.removeAllViews();
        Account current = Session.selected();

        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = Ui.text(this, 40, Ui.WHITE, true);
        name.setLetterSpacing(-0.02f);
        name.setText(current == null ? "Sin cuenta" : Session.label(current));
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nameRow.addView(name, new LinearLayout.LayoutParams(-2, -2));
        nameRow.addView(dot(), dotParams(14));
        body.addView(nameRow, new LinearLayout.LayoutParams(-1, -2));

        addText("Con tu cuenta: Inicio, Suscripciones, historial y «Seguir viendo».", 15, Ui.MUTED, false, 10);

        List<Account> accounts = Session.accounts();
        if (accounts.size() > 1) {
            addText("Cuentas guardadas", 11, Ui.DIM, false, 34);
        }
        for (Account account : accounts) {
            final Account pick = account;
            boolean chosen = current != null && current.equals(account);
            TextView row = Ui.option(this, Focus.LINE, Session.label(account), 18);
            if (chosen) row.setText(withDot(Session.label(account)));
            row.setOnClickListener(v -> switchTo(pick));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-2, -2);
            rowParams.topMargin = Ui.px(14);
            body.addView(row, rowParams);
        }

        LinearLayout actions = actionRow(40);
        action(actions, "Agregar otra cuenta", 18, this::startSignIn);
        action(actions, "Volver", 18, this::finish);
        LinearLayout signOut = actionRow(26);
        TextView signOutView = action(signOut, "Cerrar sesión", 18, () -> signOut(current));
        // Rosa en reposo, como «peligro»; con foco, blanco en negrita como el resto.
        signOutView.setOnFocusChangeListener((v, focused) -> {
            Ui.restyle((TextView) v, focused, false);
            if (!focused) ((TextView) v).setTextColor(DANGER);
        });
        signOutView.setTextColor(DANGER);

        focusFirst();
    }

    /** Elige una cuenta guardada. Va en segundo plano: refresca el token. */
    private void switchTo(Account account) {
        disposables.add(Observable.fromCallable(() -> {
                    Session.switchTo(account);
                    return true;
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(done -> showAccounts(), error -> {
                    Log.w(TAG, "No se pudo elegir la cuenta", error);
                    showAccounts();
                }));
    }

    /**
     * Cierra la sesión de la cuenta elegida. Si queda otra guardada, se muestra; si no, se pide un
     * código nuevo para entrar.
     */
    private void signOut(Account account) {
        if (account == null) return;
        disposables.add(Observable.fromCallable(() -> {
                    Session.signOut(account);
                    return true;
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(done -> {
                    if (Session.isSigned()) showAccounts();
                    else startSignIn();
                }, error -> {
                    Log.w(TAG, "No se cerró la sesión", error);
                    showAccounts();
                }));
    }

    /** Texto de una línea o más, dentro de la columna de 760. */
    private TextView addText(String text, float sp, int color, boolean bold, int topDesign) {
        TextView view = Ui.text(this, sp, color, bold);
        view.setText(text);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = Ui.px(topDesign);
        body.addView(view, params);
        return view;
    }

    /** Fila horizontal de acciones con el margen superior dado (unidades del mockup). */
    private LinearLayout actionRow(int topDesign) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.topMargin = Ui.px(topDesign);
        body.addView(row, params);
        return row;
    }

    private TextView action(LinearLayout row, String text, float sp, Runnable onClick) {
        TextView view = Ui.option(this, Focus.LINE, text, sp);
        view.setOnClickListener(v -> onClick.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.setMarginEnd(Ui.px(34));
        row.addView(view, params);
        return view;
    }

    /** Punto verde de «en uso». */
    private View dot() {
        View dot = new View(this);
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(Ui.LIVE);
        dot.setBackground(shape);
        return dot;
    }

    private LinearLayout.LayoutParams dotParams(int marginDesign) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(Ui.px(7), Ui.px(7));
        params.setMarginStart(Ui.px(marginDesign));
        return params;
    }

    /** Texto con punto verde al final: la cuenta elegida. */
    private CharSequence withDot(String text) {
        android.text.SpannableStringBuilder out = new android.text.SpannableStringBuilder(text + "  ●");
        out.setSpan(new android.text.style.ForegroundColorSpan(Ui.LIVE), text.length(), out.length(),
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return out;
    }

    /** La dirección sin «https://» ni la barra final, como se lee en el teléfono. */
    private static String shortUrl(String url) {
        if (url == null) return "";
        String out = url.replaceFirst("^https?://", "");
        return out.endsWith("/") ? out.substring(0, out.length() - 1) : out;
    }

    /** Enfoca la primera acción de la pantalla, para que el control tenga por dónde empezar. */
    private void focusFirst() {
        root.post(() -> {
            View first = findFocusable(body);
            if (first != null) first.requestFocus();
        });
    }

    private static View findFocusable(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.isFocusable()) return child;
            if (child instanceof ViewGroup) {
                View inner = findFocusable((ViewGroup) child);
                if (inner != null) return inner;
            }
        }
        return null;
    }
}
