package cl.vibe.tube;

import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

/**
 * Foco de una pantalla: una línea blanca fina debajo del texto marcado (LINE) o un marco blanco
 * alrededor de la miniatura marcada (RING). Ambos viajan de uno a otro con resorte, como en
 * VibeM3U. Se dibujan encima de todo y no reciben toques.
 */
final class Focus implements ViewTreeObserver.OnGlobalFocusChangeListener {
    static final int LINE = 1;
    static final int RING = 2;

    private final FrameLayout root;
    private final Travel line;
    private final Travel ring;

    Focus(FrameLayout root) {
        this.root = root;
        GradientDrawable bar = new GradientDrawable();
        bar.setColor(Color.WHITE);
        bar.setCornerRadius(Ui.px(1));
        View lineView = new View(root.getContext());
        lineView.setBackground(bar);
        GradientDrawable frame = new GradientDrawable();
        frame.setColor(Color.TRANSPARENT);
        frame.setCornerRadius(Ui.px(7));
        frame.setStroke(Ui.px(2), Color.WHITE);
        View ringView = new View(root.getContext());
        ringView.setBackground(frame);
        line = new Travel(root, lineView);
        ring = new Travel(root, ringView);
    }

    @Override
    public void onGlobalFocusChanged(View oldFocus, View newFocus) {
        Object mode = newFocus == null ? null : newFocus.getTag(R.id.focus_mode);
        if (Integer.valueOf(LINE).equals(mode)) {
            ring.hide();
            moveSoon(newFocus, LINE);
        } else if (Integer.valueOf(RING).equals(mode)) {
            line.hide();
            moveSoon(newFocus, RING);
        } else {
            line.hide();
            ring.hide();
        }
    }

    /** Se mueve ya y otra vez al terminar el desplazamiento de la fila o la pantalla. */
    private void moveSoon(View target, int mode) {
        move(target, mode);
        root.postDelayed(() -> {
            if (target.hasFocus()) move(target, mode);
        }, 420);
    }

    private void move(View target, int mode) {
        if (mode == LINE) line.moveTo(target, LINE);
        else ring.moveTo(target, RING);
    }

    /** Un marco o una línea que se desplaza hasta un destino con el resorte suave de VibeM3U. */
    static final class Travel {
        private final FrameLayout root;
        private final View view;
        private final FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(0, 0, Gravity.TOP | Gravity.START);
        private final int[] origin = new int[2];
        private final int[] where = new int[2];
        private ValueAnimator animator;
        private boolean shown;
        private float left, top, width, height;

        Travel(FrameLayout root, View view) {
            this.root = root;
            this.view = view;
            view.setAlpha(0f);
            view.setClickable(false);
            view.setFocusable(false);
            root.addView(view, params);
        }

        void moveTo(View target, int mode) {
            root.getLocationInWindow(origin);
            // En una miniatura, el marco rodea solo la imagen (primer hijo del bloque).
            View box = target;
            if (mode == RING && target instanceof ViewGroup && ((ViewGroup) target).getChildCount() > 0) {
                box = ((ViewGroup) target).getChildAt(0);
            }
            box.getLocationInWindow(where);
            float l = where[0] - origin[0];
            float t = where[1] - origin[1];
            float w = box.getWidth() * box.getScaleX();
            float h = box.getHeight() * box.getScaleY();
            float targetLeft, targetTop, targetWidth, targetHeight;
            if (mode == LINE) {
                targetLeft = l;
                targetTop = t + h + Ui.px(6);
                targetWidth = w;
                targetHeight = Ui.px(2);
            } else {
                float outset = Ui.px(6);
                targetLeft = l - outset;
                targetTop = t - outset;
                targetWidth = w + 2 * outset;
                targetHeight = h + 2 * outset;
            }
            if (animator != null) animator.cancel();
            if (!shown) {
                shown = true;
                left = targetLeft;
                top = targetTop;
                width = targetWidth;
                height = targetHeight;
                apply();
                view.animate().alpha(1f).setDuration(200).start();
                return;
            }
            final float fromL = left, fromT = top, fromW = width, fromH = height;
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(420);
            animator.setInterpolator(Ui.SPRING_SOFT);
            animator.addUpdateListener(a -> {
                float f = (float) a.getAnimatedValue();
                left = fromL + (targetLeft - fromL) * f;
                top = fromT + (targetTop - fromT) * f;
                width = fromW + (targetWidth - fromW) * f;
                height = fromH + (targetHeight - fromH) * f;
                apply();
            });
            animator.start();
            view.animate().alpha(1f).setDuration(200).start();
        }

        void hide() {
            if (!shown) return;
            shown = false;
            if (animator != null) animator.cancel();
            view.animate().alpha(0f).setDuration(200).start();
        }

        private void apply() {
            params.leftMargin = Math.round(left);
            params.topMargin = Math.round(top);
            params.width = Math.max(1, Math.round(width));
            params.height = Math.max(1, Math.round(height));
            view.setLayoutParams(params);
        }
    }
}
