package tv.samurai.home;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * Cyberpunk circular dial gauge for CPU and RAM readouts.
 */
public class CircularGaugeView extends View {
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF oval = new RectF();
    private float progress = 0.5f; // 0.0 to 1.0
    private float targetProgress = 0.5f;
    private String title = "CPU";
    private String subtitle = "USAGE";
    private int accentColor = Color.parseColor("#FFE600");
    private ValueAnimator animator;

    public CircularGaugeView(Context context) {
        super(context);
        init();
    }

    public CircularGaugeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(12f);
        trackPaint.setColor(Color.parseColor("#2A313C"));

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(14f);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        arcPaint.setColor(accentColor);

        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(22f);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setColor(Color.argb(60, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)));

        tickPaint.setColor(Color.parseColor("#4A5568"));
        tickPaint.setStrokeWidth(2f);

        titlePaint.setColor(Color.WHITE);
        titlePaint.setTextAlign(Paint.Align.CENTER);
        titlePaint.setFakeBoldText(true);

        valPaint.setColor(accentColor);
        valPaint.setTextAlign(Paint.Align.CENTER);
        valPaint.setFakeBoldText(true);

        subPaint.setColor(Color.parseColor("#94A3B8"));
        subPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setAccentColor(int color) {
        this.accentColor = color;
        arcPaint.setColor(color);
        valPaint.setColor(color);
        glowPaint.setColor(Color.argb(70, Color.red(color), Color.green(color), Color.blue(color)));
        invalidate();
    }

    public void setTitle(String title) {
        this.title = title;
        invalidate();
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
        invalidate();
    }

    public void setProgress(float val) {
        val = Math.max(0f, Math.min(1f, val));
        this.targetProgress = val;
        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(progress, targetProgress);
        animator.setDuration(400);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float size = Math.min(w, h);
        float pad = 24f;
        oval.set(pad, pad, size - pad, size - pad);

        // Center on canvas
        float cx = w / 2f;
        float cy = h / 2f;
        oval.offset(cx - size / 2f, cy - size / 2f);

        // Draw outer ticks
        float r = (size - pad) / 2f;
        for (int i = 0; i < 30; i++) {
            double angle = Math.toRadians(135 + (270.0 * i / 29.0));
            float x1 = (float) (cx + (r + 4) * Math.cos(angle));
            float y1 = (float) (cy + (r + 4) * Math.sin(angle));
            float x2 = (float) (cx + (r + 10) * Math.cos(angle));
            float y2 = (float) (cy + (r + 10) * Math.sin(angle));
            canvas.drawLine(x1, y1, x2, y2, tickPaint);
        }

        // Base track arc (270 degrees sweep starting from 135 deg)
        canvas.drawArc(oval, 135f, 270f, false, trackPaint);

        // Active glowing sweep
        float sweep = 270f * progress;
        canvas.drawArc(oval, 135f, sweep, false, glowPaint);
        canvas.drawArc(oval, 135f, sweep, false, arcPaint);

        // Typography inside gauge
        titlePaint.setTextSize(size * 0.18f);
        valPaint.setTextSize(size * 0.16f);
        subPaint.setTextSize(size * 0.10f);

        // Title e.g. "CPU"
        canvas.drawText(title, cx, cy - size * 0.08f, titlePaint);

        // Percentage e.g. "48%"
        int percent = Math.round(progress * 100);
        canvas.drawText(percent + "%", cx, cy + size * 0.10f, valPaint);

        // Subtitle e.g. "USAGE"
        canvas.drawText(subtitle, cx, cy + size * 0.22f, subPaint);
    }
}
