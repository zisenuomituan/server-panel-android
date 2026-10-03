package com.xianyunb.serverpanel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import java.util.List;

public class LineChart extends View {

    private final Paint grid = new Paint();
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint area = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private List<float[]> series;
    private List<Integer> colors;
    private float maxY = 100f;
    private boolean autoMax = false;
    private boolean withArea = false;

    public LineChart(Context context) {
        super(context);
        init();
    }

    public LineChart(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LineChart(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        grid.setColor(ContextCompat.getColor(getContext(), R.color.grid));
        grid.setStrokeWidth(1f);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1.5f));
        stroke.setStrokeJoin(Paint.Join.ROUND);
        area.setStyle(Paint.Style.FILL);
        area.setColor(ContextCompat.getColor(getContext(), R.color.chart_area));
    }

    public void setData(List<float[]> series, List<Integer> colors, float maxY, boolean autoMax, boolean withArea) {
        this.series = series;
        this.colors = colors;
        this.maxY = maxY;
        this.autoMax = autoMax;
        this.withArea = withArea;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (series == null || series.isEmpty()) return;

        float w = getWidth();
        float h = getHeight();
        float padL = dp(6);
        float padR = dp(6);
        float padT = dp(6);
        float padB = dp(6);
        float cw = w - padL - padR;
        float ch = h - padT - padB;
        if (cw <= 0 || ch <= 0) return;

        for (int i = 0; i <= 3; i++) {
            float y = padT + ch * i / 3f;
            canvas.drawLine(padL, y, w - padR, y, grid);
        }

        float mx = maxY;
        if (autoMax) {
            mx = 1f;
            for (float[] s : series) {
                if (s == null) continue;
                for (float v : s) if (v > mx) mx = v;
            }
            mx *= 1.15f;
        }
        if (mx <= 0) mx = 1f;

        for (int si = 0; si < series.size(); si++) {
            float[] s = series.get(si);
            if (s == null || s.length == 0) continue;
            int n = s.length;
            path.reset();
            for (int i = 0; i < n; i++) {
                float x = n == 1 ? padL + cw / 2f : padL + cw * i / (float) (n - 1);
                float v = Math.max(0, Math.min(mx, s[i]));
                float y = padT + ch * (1f - v / mx);
                if (i == 0) path.moveTo(x, y);
                else path.lineTo(x, y);
            }
            if (si < colors.size()) {
                stroke.setColor(ContextCompat.getColor(getContext(), colors.get(si)));
            }
            canvas.drawPath(path, stroke);
            if (withArea && si == 0 && n > 1) {
                Path a = new Path(path);
                a.lineTo(padL + cw, padT + ch);
                a.lineTo(padL, padT + ch);
                a.close();
                canvas.drawPath(a, area);
            }
        }
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
