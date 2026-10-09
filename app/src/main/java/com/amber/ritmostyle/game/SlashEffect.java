package com.amber.ritmostyle.game;

import android.graphics.Canvas;
import android.graphics.Paint;

public final class SlashEffect {
    private static final float LIFE_MS = 220f;

    private final float x1;
    private final float y1;
    private final float x2;
    private final float y2;
    private float ageMs;

    public SlashEffect(float x1, float y1, float x2, float y2) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
    }

    public boolean isAlive() {
        return ageMs < LIFE_MS;
    }

    public void update(float dtSeconds) {
        ageMs += dtSeconds * 1000f;
    }

    public void draw(Canvas canvas, Paint paint) {
        if (!isAlive()) {
            return;
        }
        int alpha = Math.round(210f * Math.max(0f, 1f - ageMs / LIFE_MS));
        paint.setAlpha(alpha);
        canvas.drawLine(x1, y1, x2, y2, paint);
        paint.setAlpha(255);
    }
}
