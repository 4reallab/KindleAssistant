package com.kindle.photodrop;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** 黑白沙漏：上仓剩余、下仓已用，沙量与剩余时间成线性关系。 */
public class HourglassView extends View {

    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sand = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stream = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint backing = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float ratio = 1f;
    private long remainingMinutes;
    private long elapsedMinutes;
    private boolean running;

    public HourglassView(Context context, AttributeSet attrs) {
        super(context, attrs);
        outline.setColor(Color.BLACK);
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(dp(3));
        sand.setColor(Color.parseColor("#B8B8B8"));
        sand.setStyle(Paint.Style.FILL);
        stream.setColor(Color.BLACK);
        stream.setStrokeWidth(dp(2));
        text.setColor(Color.BLACK);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(dp(48));
        label.setColor(Color.BLACK);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTextSize(dp(22));
        backing.setColor(Color.WHITE);
        backing.setStyle(Paint.Style.FILL);
    }

    void update(float remainingRatio, long remainingMin, long elapsedMin, boolean isRunning) {
        ratio = Math.max(0f, Math.min(1f, remainingRatio));
        remainingMinutes = remainingMin;
        elapsedMinutes = elapsedMin;
        running = isRunning;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        float cx = w / 2f;
        float halfWidth = Math.min(w * 0.34f, dp(230));
        float neck = Math.max(dp(8), halfWidth * 0.12f);
        float marginY = dp(24);
        float topY = marginY;
        float bottomY = h - marginY;
        float midY = h / 2f;
        float gap = dp(9);
        float chamberHeight = midY - gap - topY;
        if (chamberHeight <= dp(20)) {
            return;
        }

        Path top = new Path();
        top.moveTo(cx - halfWidth, topY);
        top.lineTo(cx + halfWidth, topY);
        top.lineTo(cx + neck, midY - gap);
        top.lineTo(cx - neck, midY - gap);
        top.close();

        Path bottom = new Path();
        bottom.moveTo(cx - neck, midY + gap);
        bottom.lineTo(cx + neck, midY + gap);
        bottom.lineTo(cx + halfWidth, bottomY);
        bottom.lineTo(cx - halfWidth, bottomY);
        bottom.close();

        float topSandY = topY + (1f - ratio) * chamberHeight;
        canvas.save();
        canvas.clipPath(top);
        canvas.clipRect(cx - halfWidth - 2, topSandY, cx + halfWidth + 2, midY - gap + 1);
        canvas.drawPath(top, sand);
        canvas.restore();

        float bottomSandY = bottomY - (1f - ratio) * (bottomY - midY - gap);
        canvas.save();
        canvas.clipPath(bottom);
        canvas.clipRect(cx - halfWidth - 2, bottomSandY, cx + halfWidth + 2, bottomY + 1);
        canvas.drawPath(bottom, sand);
        canvas.restore();

        if (running && ratio > 0f && ratio < 1f) {
            float y = midY - gap + dp(6);
            while (y < bottomSandY - dp(3)) {
                canvas.drawLine(cx, y, cx, Math.min(y + dp(7), bottomSandY - dp(2)), stream);
                y += dp(14);
            }
        }

        drawNumber(canvas, "剩余", format(remainingMinutes), cx, topY + chamberHeight * 0.38f);
        drawNumber(canvas, "已用", format(elapsedMinutes), cx,
                midY + gap + (bottomY - midY - gap) * 0.68f);

        // 轮廓线最后绘制，避免文字背景框盖住漏斗两侧斜边。
        canvas.drawPath(top, outline);
        canvas.drawPath(bottom, outline);
        canvas.drawLine(cx - halfWidth, topY, cx + halfWidth, topY, outline);
        canvas.drawLine(cx - halfWidth, bottomY, cx + halfWidth, bottomY, outline);
    }

    private void drawNumber(Canvas canvas, String name, String value, float cx, float centerY) {
        Paint.FontMetrics labelMetrics = label.getFontMetrics();
        Paint.FontMetrics valueMetrics = text.getFontMetrics();

        // 数字的整体上下边界由字体度量计算，按视觉中心摆放，避免与标签重叠。
        float valueBaseline = centerY - (valueMetrics.ascent + valueMetrics.descent) / 2f;
        float valueTop = valueBaseline + valueMetrics.ascent;
        float valueBottom = valueBaseline + valueMetrics.descent;

        float padX = dp(8);
        float padY = dp(4);
        float labelGap = dp(6);
        float labelBaseline = valueTop - padY - labelGap - labelMetrics.descent;
        float labelHalfWidth = label.measureText(name) / 2f + dp(4);
        float labelTop = labelBaseline + labelMetrics.ascent - dp(2);
        float labelBottom = labelBaseline + labelMetrics.descent + dp(2);
        canvas.drawRect(cx - labelHalfWidth, labelTop, cx + labelHalfWidth, labelBottom, backing);
        canvas.drawText(name, cx, labelBaseline, label);

        // 背景框用 measureText 计算宽度，不受 Paint 对齐方式影响，保证与数字严格对齐。
        float halfWidth = text.measureText(value) / 2f + padX;
        RectF box = new RectF(cx - halfWidth, valueTop - padY,
                cx + halfWidth, valueBottom + padY);
        canvas.drawRect(box, backing);
        canvas.drawText(value, cx, valueBaseline, text);
    }

    /** 只显示分钟，不再显示秒。 */
    private static String format(long minutes) {
        return Math.max(0, minutes) + " 分钟";
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
