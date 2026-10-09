package com.amber.ritmostyle.game;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

public final class WordTarget {
    private static final long CLOSE_MS = 500L;
    private static final long FADE_START_MS = 760L;
    private static final long VISIBLE_MS = 1650L;

    public final LyricWord word;
    public final float xNorm;
    public final float yNorm;
    public final RectF bounds = new RectF();

    public boolean cut;
    public boolean missed;
    public float screenX;
    public float screenY;

    public WordTarget(LyricWord word, float xNorm, float yNorm) {
        this.word = word;
        this.xNorm = xNorm;
        this.yNorm = yNorm;
    }

    public boolean isCuttable(long playheadMs) {
        long age = playheadMs - word.timeMs;
        return !cut && !missed && age >= 0L && age <= VISIBLE_MS;
    }

    public void updateMissed(long playheadMs) {
        if (!cut && !missed && playheadMs - word.timeMs > VISIBLE_MS) {
            missed = true;
        }
    }

    public void layout(Paint textPaint, int width, int height) {
        screenX = xNorm * width;
        screenY = yNorm * height;
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        float textWidth = textPaint.measureText(word.text);
        float textHeight = metrics.descent - metrics.ascent;
        bounds.set(
                screenX - textWidth / 2f - 16f,
                screenY - textHeight / 2f - 12f,
                screenX + textWidth / 2f + 16f,
                screenY + textHeight / 2f + 12f
        );
    }

    public void draw(Canvas canvas, Paint textPaint, Paint ringPaint, long playheadMs, int width, int height) {
        long age = playheadMs - word.timeMs;
        if (cut || missed || age < 0L || age > VISIBLE_MS) {
            return;
        }
        layout(textPaint, width, height);
        float close = Math.min(1f, age / (float) CLOSE_MS);
        float fade = age <= FADE_START_MS ? 1f : Math.max(0f, 1f - (age - FADE_START_MS) / (float) (VISIBLE_MS - FADE_START_MS));
        int alpha = Math.round(255f * fade);

        float textRadius = Math.max(bounds.width(), bounds.height()) * 0.58f;
        float startRadius = textRadius + Math.min(width, height) * 0.10f;
        float radius = startRadius + (textRadius - startRadius) * easeOutCubic(close);

        ringPaint.setAlpha(alpha);
        canvas.drawCircle(screenX, screenY, radius, ringPaint);
        textPaint.setAlpha(alpha);
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        float baseline = screenY - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(word.text, screenX, baseline, textPaint);
        textPaint.setAlpha(255);
        ringPaint.setAlpha(255);
    }

    public int splitIndexForCut(float cutX) {
        int length = word.text.length();
        if (length <= 1 || bounds.width() <= 1f) {
            return Math.max(1, length);
        }
        float normalized = (cutX - bounds.left) / bounds.width();
        int split = Math.round(normalized * length);
        return Math.max(1, Math.min(length - 1, split));
    }

    private static float easeOutCubic(float value) {
        float inverted = 1f - value;
        return 1f - inverted * inverted * inverted;
    }
}
