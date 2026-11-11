package tv.samurai.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/**
 * Renders Cyberpunk 2077 caution / hazard stripes (diagonal black and yellow bars).
 */
public class HazardStripeView extends View {
    private final Paint yellowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private static final int STRIPE_WIDTH = 24;

    public HazardStripeView(Context context) {
        super(context);
        init();
    }

    public HazardStripeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        yellowPaint.setColor(Color.parseColor("#FFD600"));
        yellowPaint.setStyle(Paint.Style.FILL);

        blackPaint.setColor(Color.parseColor("#090B0E"));
        blackPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // Draw solid background black first
        canvas.drawRect(0, 0, w, h, blackPaint);

        // Draw 45-degree yellow stripes
        int step = STRIPE_WIDTH * 2;
        int total = h + w + step;

        for (int y = -w; y < total; y += step) {
            path.reset();
            path.moveTo(0, y);
            path.lineTo(w, y + w);
            path.lineTo(w, y + w + STRIPE_WIDTH);
            path.lineTo(0, y + STRIPE_WIDTH);
            path.close();
            canvas.drawPath(path, yellowPaint);
        }
    }
}
