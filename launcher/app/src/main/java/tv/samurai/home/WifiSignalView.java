package tv.samurai.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Dynamic Cyberpunk Wi-Fi Signal Bars View (0 to 4 bars).
 */
public class WifiSignalView extends View {
    private final Paint activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint inactivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int signalLevel = 4; // 0 to 4
    private boolean connected = true;

    public WifiSignalView(Context context) {
        super(context);
        init();
    }

    public WifiSignalView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        activePaint.setStyle(Paint.Style.FILL);
        activePaint.setColor(Color.parseColor("#FFE600"));
        inactivePaint.setStyle(Paint.Style.FILL);
        inactivePaint.setColor(Color.parseColor("#33FFE600"));
    }

    public void setSignal(boolean connected, int level) {
        this.connected = connected;
        this.signalLevel = Math.max(0, Math.min(4, level));
        if (!connected) {
            activePaint.setColor(Color.parseColor("#FF1744"));
            inactivePaint.setColor(Color.parseColor("#44FF1744"));
        } else {
            activePaint.setColor(Color.parseColor("#FFE600"));
            inactivePaint.setColor(Color.parseColor("#33FFE600"));
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float barW = w / 6.2f;
        float gap = (w - 4 * barW) / 3f;
        float[] heights = new float[]{0.30f, 0.52f, 0.76f, 1.0f};

        for (int i = 0; i < 4; i++) {
            float bh = h * heights[i];
            float left = i * (barW + gap);
            float top = h - bh;
            rect.set(left, top, left + barW, h);
            boolean lit = connected && (i < signalLevel);
            canvas.drawRoundRect(rect, 1.5f, 1.5f, lit ? activePaint : inactivePaint);
        }
    }
}
