package com.xianyunb.serverpanel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

public class BarView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private double value;
    private int colorRes = R.color.blue;

    public BarView(Context context) {
        super(context);
    }

    public BarView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public BarView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setValue(double percent, int colorRes) {
        this.value = percent;
        this.colorRes = colorRes;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float r = h / 2f;

        paint.setColor(ContextCompat.getColor(getContext(), R.color.bar_bg));
        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, r, r, paint);

        float fill = (float) Math.max(0, Math.min(100, value)) / 100f * w;
        if (fill > 0) {
            paint.setColor(ContextCompat.getColor(getContext(), colorRes));
            rect.set(0, 0, fill, h);
            canvas.drawRoundRect(rect, r, r, paint);
        }
    }
}
