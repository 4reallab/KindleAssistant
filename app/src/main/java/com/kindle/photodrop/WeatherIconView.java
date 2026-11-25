package com.kindle.photodrop;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/** 黑白简笔天气图标，使用 Canvas 绘制，适配 Kindle 电子墨水屏，不依赖 emoji 字体。 */
public class WeatherIconView extends View {

    private static final int COLOR = Color.parseColor("#333333");
    private int code = 0;

    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);

    public WeatherIconView(Context context) {
        super(context);
        init();
    }

    public WeatherIconView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        stroke.setColor(COLOR);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(5f);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        fill.setColor(COLOR);
        fill.setStyle(Paint.Style.FILL);
    }

    public void setCode(int value) {
        code = value;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        canvas.save();
        canvas.scale(w / 100f, h / 100f);
        drawIcon(canvas, code);
        canvas.restore();
    }

    private void drawIcon(Canvas c, int code) {
        if (code == 0) {
            sun(c, 50, 46, 1f);
            return;
        }
        if (code == 1) {
            sun(c, 32, 30, 0.55f);
            cloud(c, 58, 58, 0.85f);
            return;
        }
        if (code == 45 || code == 48) {
            cloud(c, 50, 40, 0.95f);
            line(c, 26, 76, 74, 76);
            line(c, 34, 86, 66, 86);
            return;
        }
        if (code == 95 || code == 96 || code == 97 || code == 99) {
            cloud(c, 50, 36, 0.95f);
            bolt(c);
            return;
        }
        if (isSnow(code)) {
            cloud(c, 50, 36, 0.95f);
            snow(c);
            return;
        }
        if (isRain(code)) {
            cloud(c, 50, 36, 0.95f);
            rain(c);
            return;
        }
        cloud(c, 50, 46, 1.05f);
    }

    private static boolean isRain(int code) {
        return code == 51 || code == 53 || code == 55 || code == 56 || code == 57
                || code == 61 || code == 63 || code == 65
                || code == 80 || code == 81 || code == 82;
    }

    private static boolean isSnow(int code) {
        return code == 71 || code == 73 || code == 75 || code == 77
                || code == 85 || code == 86;
    }

    private void sun(Canvas c, float cx, float cy, float s) {
        c.drawCircle(cx, cy, 15 * s, fill);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8;
            float dx = (float) Math.cos(a);
            float dy = (float) Math.sin(a);
            line(c, cx + dx * 22 * s, cy + dy * 22 * s,
                    cx + dx * 32 * s, cy + dy * 32 * s);
        }
    }

    private void cloud(Canvas c, float cx, float cy, float s) {
        c.drawCircle(cx - 17 * s, cy + 3 * s, 13 * s, fill);
        c.drawCircle(cx + 1 * s, cy - 6 * s, 19 * s, fill);
        c.drawCircle(cx + 21 * s, cy + 3 * s, 13 * s, fill);
        c.drawRect(cx - 30 * s, cy + 1 * s, cx + 34 * s, cy + 16 * s, fill);
    }

    private void rain(Canvas c) {
        line(c, 34, 68, 28, 84);
        line(c, 52, 68, 46, 84);
        line(c, 70, 68, 64, 84);
    }

    private void snow(Canvas c) {
        flake(c, 36, 76);
        flake(c, 54, 84);
        flake(c, 70, 74);
    }

    private void flake(Canvas c, float x, float y) {
        line(c, x - 7, y, x + 7, y);
        line(c, x, y - 7, x, y + 7);
        line(c, x - 5, y - 5, x + 5, y + 5);
        line(c, x - 5, y + 5, x + 5, y - 5);
    }

    private void bolt(Canvas c) {
        Paint p = new Paint(fill);
        p.setColor(Color.parseColor("#333333"));
        android.graphics.Path path = new android.graphics.Path();
        path.moveTo(56, 60);
        path.lineTo(42, 82);
        path.lineTo(51, 82);
        path.lineTo(44, 98);
        path.lineTo(64, 74);
        path.lineTo(54, 74);
        path.lineTo(62, 60);
        path.close();
        c.drawPath(path, p);
    }

    private void line(Canvas c, float x1, float y1, float x2, float y2) {
        c.drawLine(x1, y1, x2, y2, stroke);
    }
}
