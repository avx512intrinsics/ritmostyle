package com.amber.ritmostyle.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import com.amber.ritmostyle.game.Level;
import com.amber.ritmostyle.game.RhythmSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@SuppressLint("ViewConstructor")
public final class RitmoGameView extends View {
    private static final float TAU = (float) (Math.PI * 2.0);
    private static final int DISK_SLOTS = 12;

    private final Host host;
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint logoPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bladePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint slashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF playBounds = new RectF();
    private final RectF makeBounds = new RectF();
    private final RectF selectedBounds = new RectF();
    private final RectF backBounds = new RectF();
    private final ArrayList<Level> levels = new ArrayList<>();

    private Screen screen = Screen.MENU;
    private Level selectedLevel;
    private Level lastPlayedLevel;
    private RhythmSession session;
    private long firstFrameMs;
    private long lastFrameMs;
    private long loadingStartMs;
    private String loadingMessage = "loading vibes";
    private int selectedIndex;
    private int finalScore;
    private float diskRotation;
    private float lastDiskAngle;
    private float downX;
    private float downY;
    private long downTimeMs;
    private boolean draggingDisk;

    private static final String[] MOTDS = {
            "one more run and it totally counts as practice",
            "loading the part where your thumbs become lore",
            "chat is this a rhythm game",
            "sword calibrated by pure vibes",
            "rendering stylish misses as character development",
            "syncing lyrics with main character energy",
            "the beat dropped, we are picking it back up"
    };

    public RitmoGameView(Context context, Host host) {
        super(context);
        this.host = host;
        setFocusable(true);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        configurePaints();
    }

    public void setLevels(List<Level> incomingLevels, Level defaultLevel) {
        levels.clear();
        levels.addAll(incomingLevels);
        selectedLevel = defaultLevel;
        lastPlayedLevel = defaultLevel;
        selectedIndex = Math.max(0, levels.indexOf(defaultLevel));
    }

    public boolean isSelectingLevel() {
        return screen == Screen.SELECT;
    }

    public void onVideoCompleted() {
        if (screen == Screen.PLAYING) {
            startEndScreen();
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        if (firstFrameMs == 0L) {
            firstFrameMs = now;
        }
        float dt = lastFrameMs == 0L ? 1f / 60f : Math.min(0.05f, (now - lastFrameMs) / 1000f);
        lastFrameMs = now;

        if (screen == Screen.LOADING && now - loadingStartMs >= 1700L) {
            beginGameplay();
        }

        if (screen != Screen.LOADING) {
            drawOverlay(canvas, screen == Screen.PLAYING ? 120 : 165);
        }

        switch (screen) {
            case MENU:
                drawMenu(canvas, now);
                break;
            case MAKE:
                drawMake(canvas, now);
                break;
            case SELECT:
                drawSelector(canvas, now);
                break;
            case LOADING:
                drawLoading(canvas, now);
                break;
            case PLAYING:
                drawGameplay(canvas, dt);
                break;
            case END:
                drawEnd(canvas, now);
                break;
        }
        postInvalidateOnAnimation();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (screen) {
            case MENU:
                handleMenuTouch(event);
                return true;
            case MAKE:
                handleMakeTouch(event);
                return true;
            case SELECT:
                handleSelectorTouch(event);
                return true;
            case PLAYING:
                if (session != null) {
                    session.onTouchEvent(event);
                }
                return true;
            case END:
                if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    performClick();
                    enterSelector();
                }
                return true;
            default:
                return true;
        }
    }

    private void configurePaints() {
        fillPaint.setStyle(Paint.Style.FILL);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setSubpixelText(true);

        logoPaint.setColor(Color.WHITE);
        logoPaint.setTextAlign(Paint.Align.CENTER);
        logoPaint.setFakeBoldText(true);
        logoPaint.setSubpixelText(true);
        logoPaint.setShadowLayer(18f, 0f, 0f, Color.argb(180, 255, 255, 255));

        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeCap(Paint.Cap.ROUND);
        ringPaint.setColor(Color.WHITE);
        ringPaint.setStrokeWidth(4f);

        bladePaint.setStyle(Paint.Style.STROKE);
        bladePaint.setStrokeCap(Paint.Cap.ROUND);
        bladePaint.setColor(Color.WHITE);
        bladePaint.setStrokeWidth(10f);

        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setColor(Color.WHITE);

        slashPaint.setStyle(Paint.Style.STROKE);
        slashPaint.setStrokeCap(Paint.Cap.ROUND);
        slashPaint.setStrokeWidth(7f);
        slashPaint.setColor(Color.WHITE);
    }

    private void handleMenuTouch(MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_UP) {
            return;
        }
        performClick();
        float x = event.getX();
        float y = event.getY();
        if (playBounds.contains(x, y)) {
            enterSelector();
        } else if (makeBounds.contains(x, y)) {
            screen = Screen.MAKE;
        }
    }

    private void handleMakeTouch(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            performClick();
            if (backBounds.contains(event.getX(), event.getY())) {
                screen = Screen.MENU;
                host.playMenuBackground(lastPlayedLevel);
            }
        }
    }

    private void handleSelectorTouch(MotionEvent event) {
        int action = event.getActionMasked();
        float centerX = getWidth() / 2f;
        float centerY = getHeight() * 0.72f;
        if (action == MotionEvent.ACTION_DOWN) {
            downX = event.getX();
            downY = event.getY();
            downTimeMs = SystemClock.uptimeMillis();
            lastDiskAngle = (float) Math.atan2(event.getY() - centerY, event.getX() - centerX);
            draggingDisk = true;
        } else if (action == MotionEvent.ACTION_MOVE && draggingDisk) {
            float angle = (float) Math.atan2(event.getY() - centerY, event.getX() - centerX);
            diskRotation += normalizeAngle(angle - lastDiskAngle);
            lastDiskAngle = angle;
            updateSelectedFromRotation();
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            performClick();
            draggingDisk = false;
            boolean quickTap = SystemClock.uptimeMillis() - downTimeMs < 260L
                    && distance(downX, downY, event.getX(), event.getY()) < 34f;
            if (quickTap && selectedBounds.contains(event.getX(), event.getY())) {
                startLoading();
            }
        }
    }

    private void enterSelector() {
        screen = Screen.SELECT;
        updateSelectedLevel();
        host.playLevelPreview(selectedLevel);
    }

    private void startLoading() {
        screen = Screen.LOADING;
        loadingStartMs = SystemClock.uptimeMillis();
        loadingMessage = MOTDS[Math.floorMod((int) (loadingStartMs / 997L) + selectedIndex, MOTDS.length)];
    }

    private void beginGameplay() {
        try {
            session = new RhythmSession(getContext(), selectedLevel);
            lastPlayedLevel = selectedLevel;
            screen = Screen.PLAYING;
            host.playGameplay(selectedLevel);
        } catch (IOException exception) {
            loadingMessage = "lyrics failed to spawn";
            screen = Screen.SELECT;
        }
    }

    private void startEndScreen() {
        finalScore = session == null ? 0 : session.score();
        screen = Screen.END;
        host.playEndLoop(lastPlayedLevel);
    }

    private void updateSelectedFromRotation() {
        if (levels.isEmpty()) {
            return;
        }
        float slotAngle = TAU / DISK_SLOTS;
        int newIndex = Math.floorMod(Math.round(-diskRotation / slotAngle), levels.size());
        if (newIndex != selectedIndex) {
            selectedIndex = newIndex;
            updateSelectedLevel();
            host.playLevelPreview(selectedLevel);
        }
    }

    private void updateSelectedLevel() {
        if (!levels.isEmpty()) {
            selectedIndex = Math.floorMod(selectedIndex, levels.size());
            selectedLevel = levels.get(selectedIndex);
        }
    }

    private void drawMenu(Canvas canvas, long now) {
        int width = getWidth();
        int height = getHeight();
        float centerX = width / 2f;
        float logoY = height * 0.34f;
        float logoPulse = 1f + heartbeat(now, 0f) * 0.035f;

        fillPaint.setColor(Color.argb(58, 255, 255, 255));
        canvas.drawCircle(centerX, logoY - height * 0.015f, height * 0.165f * logoPulse, fillPaint);
        ringPaint.setStrokeWidth(3.5f);
        ringPaint.setAlpha(170);
        canvas.drawCircle(centerX, logoY - height * 0.015f, height * 0.19f * logoPulse, ringPaint);
        ringPaint.setAlpha(255);

        logoPaint.setTextSize(height * 0.145f);
        canvas.drawText("ritmostyle", centerX, logoY, logoPaint);

        textPaint.setTextSize(height * 0.073f);
        drawPulseLabel(canvas, "Play", centerX, height * 0.60f, heartbeat(now, 0.12f), playBounds);
        drawPulseLabel(canvas, "Make", centerX, height * 0.735f, heartbeat(now, 0.38f), makeBounds);
    }

    private void drawMake(Canvas canvas, long now) {
        int width = getWidth();
        int height = getHeight();
        drawOverlay(canvas, 185);
        logoPaint.setTextSize(height * 0.13f);
        canvas.drawText("Make", width / 2f, height * 0.38f, logoPaint);
        textPaint.setTextSize(height * 0.052f);
        drawPulseLabel(canvas, "Back", width / 2f, height * 0.68f, heartbeat(now, 0.25f), backBounds);
    }

    private void drawSelector(Canvas canvas, long now) {
        int width = getWidth();
        int height = getHeight();
        float centerX = width / 2f;
        float centerY = height * 0.72f;
        float radius = Math.min(width, height) * 0.46f;

        fillPaint.setColor(Color.argb(82, 255, 255, 255));
        canvas.drawCircle(centerX, centerY, radius, fillPaint);
        fillPaint.setColor(Color.argb(180, 0, 0, 0));
        canvas.drawCircle(centerX, centerY, radius * 0.88f, fillPaint);
        ringPaint.setStrokeWidth(5f);
        ringPaint.setAlpha(205);
        canvas.drawCircle(centerX, centerY, radius, ringPaint);
        canvas.drawCircle(centerX, centerY, radius * 0.27f, ringPaint);
        ringPaint.setAlpha(70);
        for (int i = 0; i < DISK_SLOTS; i++) {
            float angle = diskRotation + i * TAU / DISK_SLOTS;
            float x1 = centerX + (float) Math.cos(angle) * radius * 0.34f;
            float y1 = centerY + (float) Math.sin(angle) * radius * 0.34f;
            float x2 = centerX + (float) Math.cos(angle) * radius * 0.94f;
            float y2 = centerY + (float) Math.sin(angle) * radius * 0.94f;
            canvas.drawLine(x1, y1, x2, y2, ringPaint);
        }
        ringPaint.setAlpha(255);

        textPaint.setTextSize(height * 0.043f);
        float slotAngle = TAU / DISK_SLOTS;
        int selectedSlot = Math.round(-diskRotation / slotAngle);
        for (int slot = 0; slot < DISK_SLOTS; slot++) {
            int levelIndex = levels.isEmpty() ? 0 : Math.floorMod(selectedIndex + slot - selectedSlot, levels.size());
            Level level = levels.get(levelIndex);
            float angle = -((float) Math.PI / 2f) + normalizeAngle(diskRotation) + slot * slotAngle;
            float closeness = Math.abs(normalizeAngle(angle + (float) Math.PI / 2f));
            boolean selected = closeness < slotAngle * 0.48f;
            float scale = selected ? 1.18f + heartbeat(now, 0.1f) * 0.045f : 0.86f;
            float x = centerX + (float) Math.cos(angle) * radius;
            float y = centerY + (float) Math.sin(angle) * radius;
            drawLevelLabel(canvas, level.title, x, y, scale, selected);
            if (selected) {
                selectedBounds.set(x - width * 0.19f, y - height * 0.075f, x + width * 0.19f, y + height * 0.075f);
            }
        }
    }

    private void drawGameplay(Canvas canvas, float dt) {
        if (session == null) {
            return;
        }
        int width = getWidth();
        int height = getHeight();
        textPaint.setTextSize(Math.max(28f, height * 0.052f));
        ringPaint.setStrokeWidth(Math.max(3f, height * 0.006f));
        session.updateAndDraw(
                canvas,
                host.currentVideoPositionMs(),
                dt,
                width,
                height,
                textPaint,
                ringPaint,
                bladePaint,
                glowPaint,
                slashPaint
        );

        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTextSize(height * 0.045f);
        textPaint.setShadowLayer(10f, 0f, 0f, Color.argb(140, 255, 255, 255));
        canvas.drawText(String.format(Locale.US, "%07d", session.score()), width * 0.035f, height * 0.075f, textPaint);
        textPaint.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    private void drawLoading(Canvas canvas, long now) {
        int width = getWidth();
        int height = getHeight();
        fillPaint.setColor(Color.BLACK);
        canvas.drawRect(0f, 0f, width, height, fillPaint);

        int progress = Math.min(100, Math.round((now - loadingStartMs) / 17f));
        textPaint.setTextSize(height * 0.052f);
        textPaint.setShadowLayer(12f, 0f, 0f, Color.argb(120, 255, 255, 255));
        canvas.drawText(loadingMessage, width / 2f, height / 2f, textPaint);
        textPaint.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT);

        float barWidth = width * 0.22f;
        float barHeight = Math.max(4f, height * 0.012f);
        float left = width - barWidth - width * 0.035f;
        float top = height - height * 0.07f;
        ringPaint.setStrokeWidth(2f);
        ringPaint.setAlpha(150);
        canvas.drawRect(left, top, left + barWidth, top + barHeight, ringPaint);
        ringPaint.setAlpha(255);
        fillPaint.setColor(Color.WHITE);
        canvas.drawRect(left, top, left + barWidth * progress / 100f, top + barHeight, fillPaint);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        textPaint.setTextSize(height * 0.034f);
        canvas.drawText("loading (" + progress + "%)", width - width * 0.035f, top - height * 0.018f, textPaint);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    private void drawEnd(Canvas canvas, long now) {
        int width = getWidth();
        int height = getHeight();
        drawOverlay(canvas, 190);
        logoPaint.setTextSize(height * 0.105f);
        canvas.drawText("Score", width / 2f, height * 0.35f, logoPaint);
        textPaint.setTextSize(height * 0.13f);
        textPaint.setShadowLayer(18f, 0f, 0f, Color.argb(150, 255, 255, 255));
        canvas.drawText(String.format(Locale.US, "%,d", finalScore), width / 2f, height * 0.56f, textPaint);
        textPaint.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT);
        textPaint.setTextSize(height * 0.04f);
        drawPulseLabel(canvas, "Play", width / 2f, height * 0.78f, heartbeat(now, 0.3f), playBounds);
    }

    private void drawPulseLabel(Canvas canvas, String label, float x, float y, float pulse, RectF bounds) {
        float originalSize = textPaint.getTextSize();
        float scale = 1f + pulse * 0.08f;
        textPaint.setTextSize(originalSize * scale);
        textPaint.setShadowLayer(12f + pulse * 6f, 0f, 0f, Color.argb(170, 255, 255, 255));
        canvas.drawText(label, x, y, textPaint);
        float width = textPaint.measureText(label) * 1.55f;
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        bounds.set(x - width / 2f, y + metrics.ascent * 1.35f, x + width / 2f, y + metrics.descent * 1.65f);
        textPaint.setTextSize(originalSize);
        textPaint.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT);
    }

    private void drawLevelLabel(Canvas canvas, String title, float x, float y, float scale, boolean selected) {
        float originalSize = textPaint.getTextSize();
        textPaint.setTextSize(originalSize * scale);
        textPaint.setAlpha(selected ? 255 : 170);
        textPaint.setShadowLayer(selected ? 16f : 6f, 0f, 0f, Color.argb(selected ? 170 : 80, 255, 255, 255));
        canvas.drawText(title, x, y, textPaint);
        textPaint.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT);
        textPaint.setAlpha(255);
        textPaint.setTextSize(originalSize);
    }

    private void drawOverlay(Canvas canvas, int alpha) {
        fillPaint.setColor(Color.argb(alpha, 0, 0, 0));
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), fillPaint);
    }

    private static float heartbeat(long now, float phase) {
        float t = ((now % 1150L) / 1150f + phase) % 1f;
        float first = gaussian(t, 0.10f, 0.035f);
        float second = gaussian(t, 0.22f, 0.045f) * 0.72f;
        return Math.min(1f, first + second);
    }

    private static float gaussian(float value, float center, float width) {
        float d = (value - center) / width;
        return (float) Math.exp(-d * d);
    }

    private static float normalizeAngle(float angle) {
        while (angle > Math.PI) {
            angle -= TAU;
        }
        while (angle < -Math.PI) {
            angle += TAU;
        }
        return angle;
    }

    private static float distance(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private enum Screen {
        MENU,
        MAKE,
        SELECT,
        LOADING,
        PLAYING,
        END
    }

    public interface Host {
        void playMenuBackground(Level level);

        void playLevelPreview(Level level);

        void playGameplay(Level level);

        void playEndLoop(Level level);

        int currentVideoPositionMs();

        int videoDurationMs();
    }
}
