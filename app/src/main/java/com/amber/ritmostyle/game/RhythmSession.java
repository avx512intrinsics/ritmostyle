package com.amber.ritmostyle.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class RhythmSession {
    private final Level level;
    private final ArrayList<WordTarget> targets = new ArrayList<>();
    private final ArrayList<WordSlice> slices = new ArrayList<>();
    private final ArrayList<SlashEffect> slashEffects = new ArrayList<>();
    private final SwordPhysics sword = new SwordPhysics();
    private final double scorePerWord;
    private double score;

    public RhythmSession(Context context, Level level) throws IOException {
        this.level = level;
        LrcParser.Result result = LrcParser.parse(context, level.lrcResId);
        PositionHasher hasher = new PositionHasher(result.rawText);
        List<LyricWord> words = result.words;
        for (LyricWord word : words) {
            PositionHasher.SpawnPoint point = hasher.pointForWord(word.index);
            targets.add(new WordTarget(word, point.x, point.y));
        }
        scorePerWord = targets.isEmpty() ? 0.0 : 1_000_000.0 / targets.size();
    }

    public Level level() {
        return level;
    }

    public int totalWords() {
        return targets.size();
    }

    public int score() {
        return Math.min(1_000_000, (int) Math.round(score));
    }

    public void onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            sword.touchDown(event.getX(event.getActionIndex()), event.getY(event.getActionIndex()));
        } else if (action == MotionEvent.ACTION_MOVE) {
            sword.touchMove(event.getX(), event.getY());
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_UP) {
            sword.touchUp();
        }
    }

    public void updateAndDraw(
            Canvas canvas,
            long playheadMs,
            float dtSeconds,
            int width,
            int height,
            Paint wordPaint,
            Paint ringPaint,
            Paint bladePaint,
            Paint glowPaint,
            Paint slashPaint
    ) {
        sword.update(dtSeconds, width, height);
        updateFallingPieces(dtSeconds);
        collideCuttableWords(wordPaint, playheadMs, width, height);

        for (WordTarget target : targets) {
            target.draw(canvas, wordPaint, ringPaint, playheadMs, width, height);
        }
        for (WordSlice slice : slices) {
            slice.draw(canvas, wordPaint);
        }
        for (SlashEffect effect : slashEffects) {
            effect.draw(canvas, slashPaint);
        }
        sword.draw(canvas, bladePaint, glowPaint);
    }

    private void collideCuttableWords(Paint wordPaint, long playheadMs, int width, int height) {
        if (!sword.isGrabbed()) {
            for (WordTarget target : targets) {
                target.updateMissed(playheadMs);
            }
            return;
        }

        float bladeStartX = sword.bladeStartX();
        float bladeStartY = sword.bladeStartY();
        float bladeTipX = sword.tipX();
        float bladeTipY = sword.tipY();
        for (WordTarget target : targets) {
            target.updateMissed(playheadMs);
            if (!target.isCuttable(playheadMs)) {
                continue;
            }
            target.layout(wordPaint, width, height);
            boolean bladeTouches = Geometry.segmentIntersectsRect(bladeStartX, bladeStartY, bladeTipX, bladeTipY, target.bounds)
                    || Geometry.distancePointToSegment(target.screenX, target.screenY, sword.previousTipX(), sword.previousTipY(), bladeTipX, bladeTipY) < Math.max(target.bounds.width(), target.bounds.height()) * 0.55f;
            if (bladeTouches) {
                cutTarget(target, bladeTipX, bladeTipY);
            }
        }
    }

    private void cutTarget(WordTarget target, float cutX, float cutY) {
        target.cut = true;
        score += scorePerWord;
        int split = target.splitIndexForCut(cutX);
        String left = target.word.text.substring(0, split);
        String right = target.word.text.substring(split);
        float centerY = target.screenY;
        float outward = 210f + (target.word.index % 7) * 18f;
        slices.add(new WordSlice(left, target.bounds.left + target.bounds.width() * 0.22f, centerY, -outward, -210f, -150f - target.word.index % 60));
        slices.add(new WordSlice(right, target.bounds.left + target.bounds.width() * 0.58f, centerY, outward, -180f, 140f + target.word.index % 70));
        slashEffects.add(new SlashEffect(sword.previousTipX(), sword.previousTipY(), cutX, cutY));
    }

    private void updateFallingPieces(float dtSeconds) {
        Iterator<WordSlice> sliceIterator = slices.iterator();
        while (sliceIterator.hasNext()) {
            WordSlice slice = sliceIterator.next();
            slice.update(dtSeconds);
            if (!slice.isAlive()) {
                sliceIterator.remove();
            }
        }
        Iterator<SlashEffect> effectIterator = slashEffects.iterator();
        while (effectIterator.hasNext()) {
            SlashEffect effect = effectIterator.next();
            effect.update(dtSeconds);
            if (!effect.isAlive()) {
                effectIterator.remove();
            }
        }
    }
}
