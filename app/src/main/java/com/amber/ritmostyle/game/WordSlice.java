package com.amber.ritmostyle.game;

import android.graphics.Canvas;
import android.graphics.Paint;

public final class WordSlice {
    private static final float GRAVITY = 980f;
    private static final float LIFE_MS = 2800f;

    private final String text;
    private float x;
    private float y;
    private float vx;
    private float vy;
    private float rotation;
    private final float rotationVelocity;
    private float ageMs;

    public WordSlice(String text, float x, float y, float vx, float vy, float rotationVelocity) {
        this.text = text;
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.rotationVelocity = rotationVelocity;
    }

    public boolean isAlive() {
        return ageMs < LIFE_MS && text.length() > 0;
    }

    public void update(float dtSeconds) {
        ageMs += dtSeconds * 1000f;
        vy += GRAVITY * dtSeconds;
        x += vx * dtSeconds;
        y += vy * dtSeconds;
        rotation += rotationVelocity * dtSeconds;
        vx *= 0.992f;
    }

    public void draw(Canvas canvas, Paint paint) {
        if (!isAlive()) {
            return;
        }
        int alpha = Math.round(255f * Math.max(0f, 1f - ageMs / LIFE_MS));
        paint.setAlpha(alpha);
        canvas.save();
        canvas.translate(x, y);
        canvas.rotate(rotation);
        canvas.drawText(text, 0f, 0f, paint);
        canvas.restore();
        paint.setAlpha(255);
    }
}
