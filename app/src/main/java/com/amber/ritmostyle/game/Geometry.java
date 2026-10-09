package com.amber.ritmostyle.game;

import android.graphics.RectF;

public final class Geometry {
    private Geometry() {
    }

    public static boolean segmentIntersectsRect(float x1, float y1, float x2, float y2, RectF rect) {
        if (rect.contains(x1, y1) || rect.contains(x2, y2)) {
            return true;
        }
        return segmentsIntersect(x1, y1, x2, y2, rect.left, rect.top, rect.right, rect.top)
                || segmentsIntersect(x1, y1, x2, y2, rect.right, rect.top, rect.right, rect.bottom)
                || segmentsIntersect(x1, y1, x2, y2, rect.right, rect.bottom, rect.left, rect.bottom)
                || segmentsIntersect(x1, y1, x2, y2, rect.left, rect.bottom, rect.left, rect.top);
    }

    public static float distance(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    public static float distancePointToSegment(float px, float py, float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float lengthSquared = dx * dx + dy * dy;
        if (lengthSquared <= 0.0001f) {
            return distance(px, py, x1, y1);
        }
        float t = ((px - x1) * dx + (py - y1) * dy) / lengthSquared;
        t = Math.max(0f, Math.min(1f, t));
        return distance(px, py, x1 + t * dx, y1 + t * dy);
    }

    private static boolean segmentsIntersect(float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy) {
        float d1 = direction(cx, cy, dx, dy, ax, ay);
        float d2 = direction(cx, cy, dx, dy, bx, by);
        float d3 = direction(ax, ay, bx, by, cx, cy);
        float d4 = direction(ax, ay, bx, by, dx, dy);
        return ((d1 > 0f && d2 < 0f) || (d1 < 0f && d2 > 0f))
                && ((d3 > 0f && d4 < 0f) || (d3 < 0f && d4 > 0f));
    }

    private static float direction(float ax, float ay, float bx, float by, float cx, float cy) {
        return (cx - ax) * (by - ay) - (cy - ay) * (bx - ax);
    }
}
