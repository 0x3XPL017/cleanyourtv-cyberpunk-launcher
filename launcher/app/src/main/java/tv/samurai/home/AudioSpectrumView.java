package tv.samurai.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import java.util.Random;

/**
 * Animated Cyberpunk Audio Equalizer / Spectrum Visualizer.
 */
public class AudioSpectrumView extends View {
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float[] heights = new float[26];
    private final float[] targetHeights = new float[26];
    private final Random random = new Random();
    private boolean isPlaying = false;

    public AudioSpectrumView(Context context) {
        super(context);
        init();
    }

    public AudioSpectrumView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        barPaint.setStyle(Paint.Style.FILL);
        glowPaint.setStyle(Paint.Style.FILL);
        glowPaint.setColor(Color.argb(80, 255, 23, 68));

        // Initial idle pattern (flat subtle bars until playback begins)
        for (int i = 0; i < heights.length; i++) {
            heights[i] = 0.08f;
            targetHeights[i] = 0.08f;
        }

        startAnimationLoop();
    }

    private void startAnimationLoop() {
        post(new Runnable() {
            @Override
            public void run() {
                if (isPlaying) {
                    for (int i = 0; i < heights.length; i++) {
                        if (Math.abs(heights[i] - targetHeights[i]) < 0.05f) {
                            // Pick new random target modulated by frequency curve
                            float bell = (float) Math.sin((double) i / heights.length * Math.PI);
                            targetHeights[i] = 0.15f + bell * (0.3f + 0.55f * random.nextFloat());
                        } else {
                            heights[i] += (targetHeights[i] - heights[i]) * 0.25f;
                        }
                    }
                    invalidate();
                }
                postDelayed(this, 40); // ~25 FPS smooth audio bounce
            }
        });
    }

    public void setPlaying(boolean playing) {
        this.isPlaying = playing;
        if (!playing) {
            for (int i = 0; i < heights.length; i++) {
                heights[i] = 0.08f;
                targetHeights[i] = 0.08f;
            }
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // Gradient from bright neon orange at top to intense red at bottom
        barPaint.setShader(new LinearGradient(0, 0, 0, h,
                Color.parseColor("#FF6D00"), Color.parseColor("#FF1744"),
                Shader.TileMode.CLAMP));

        int barCount = heights.length;
        float gap = 3f;
        float totalGaps = (barCount - 1) * gap;
        float barWidth = Math.max(3f, (w - totalGaps) / barCount);

        for (int i = 0; i < barCount; i++) {
            float barH = h * heights[i];
            float left = i * (barWidth + gap);
            float top = h - barH;
            float right = left + barWidth;
            float bottom = h;

            rect.set(left, top, right, bottom);
            canvas.drawRoundRect(rect, 2f, 2f, barPaint);
        }
    }
}
