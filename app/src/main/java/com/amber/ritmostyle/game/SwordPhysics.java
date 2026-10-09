package com.amber.ritmostyle.game;

import android.graphics.Canvas;
import android.graphics.Paint;

public final class SwordPhysics {
    private float handleX;
    private float handleY;
    private float angle = (float) Math.PI / 2f;
    private float angularVelocity;
    private float bladeLength;
    private float previousTipX;
    private float previousTipY;
    private float touchX;
    private float touchY;
    private boolean initialized;
    private boolean grabbed;

    public void update(float dtSeconds, int width, int height) {
        bladeLength = Math.min(width, height) * 0.27f;
        if (!initialized) {
            handleX = width / 2f;
            handleY = height / 2f;
            initialized = true;
        }

        previousTipX = tipX();
        previousTipY = tipY();

        if (grabbed) {
            handleX += (touchX - handleX) * 0.72f;
            handleY += (touchY - handleY) * 0.72f;
        } else {
            handleX += (width / 2f - handleX) * Math.min(1f, dtSeconds * 5f);
            handleY += (height / 2f - handleY) * Math.min(1f, dtSeconds * 5f);
        }

        float down = (float) Math.PI / 2f;
        float gravityTorque = normalizeAngle(down - angle) * 10.5f;
        angularVelocity += gravityTorque * dtSeconds;
        angularVelocity *= grabbed ? 0.965f : 0.91f;
        angle += angularVelocity * dtSeconds;
        angle = normalizeAngle(angle);
    }

    public void touchDown(float x, float y) {
        touchX = x;
        touchY = y;
        handleX = x;
        handleY = y;
        grabbed = true;
    }

    public void touchMove(float x, float y) {
        if (!grabbed) {
            return;
        }
        float dx = x - touchX;
        float dy = y - touchY;
        float tangentImpulse = (float) (-Math.sin(angle) * dx + Math.cos(angle) * dy);
        angularVelocity += tangentImpulse * 0.018f;
        touchX = x;
        touchY = y;
    }

    public void touchUp() {
        grabbed = false;
    }

    public boolean isGrabbed() {
        return grabbed;
    }

    public float bladeStartX() {
        return handleX + (float) Math.cos(angle) * bladeLength * 0.10f;
    }

    public float bladeStartY() {
        return handleY + (float) Math.sin(angle) * bladeLength * 0.10f;
    }

    public float tipX() {
        return handleX + (float) Math.cos(angle) * bladeLength;
    }

    public float tipY() {
        return handleY + (float) Math.sin(angle) * bladeLength;
    }

    public float previousTipX() {
        return previousTipX;
    }

    public float previousTipY() {
        return previousTipY;
    }

    public float bladeLength() {
        return bladeLength;
    }

    public void draw(Canvas canvas, Paint bladePaint, Paint glowPaint) {
        float startX = bladeStartX();
        float startY = bladeStartY();
        float tipX = tipX();
        float tipY = tipY();

        glowPaint.setStrokeWidth(bladePaint.getStrokeWidth() + 16f);
        glowPaint.setAlpha(grabbed ? 95 : 48);
        canvas.drawLine(startX, startY, tipX, tipY, glowPaint);
        glowPaint.setAlpha(255);

        canvas.drawLine(startX, startY, tipX, tipY, bladePaint);

        float guardAngle = angle + (float) Math.PI / 2f;
        float guard = bladeLength * 0.16f;
        float gx = (float) Math.cos(guardAngle) * guard;
        float gy = (float) Math.sin(guardAngle) * guard;
        canvas.drawLine(handleX - gx, handleY - gy, handleX + gx, handleY + gy, bladePaint);
        canvas.drawCircle(handleX, handleY, Math.max(6f, bladeLength * 0.035f), bladePaint);
    }

    private static float normalizeAngle(float value) {
        while (value > Math.PI) {
            value -= (float) Math.PI * 2f;
        }
        while (value < -Math.PI) {
            value += (float) Math.PI * 2f;
        }
        return value;
    }
}
