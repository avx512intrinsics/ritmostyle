package com.amber.ritmostyle.game;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LrcParser {
    private static final Pattern TIME_TAG = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?]");

    private LrcParser() {
    }

    public static Result parse(Context context, int rawResourceId) throws IOException {
        String raw = readRawText(context, rawResourceId);
        ArrayList<StampedLine> stampedLines = new ArrayList<>();
        String[] lines = raw.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (String line : lines) {
            Matcher matcher = TIME_TAG.matcher(line);
            ArrayList<Long> stamps = new ArrayList<>();
            while (matcher.find()) {
                stamps.add(parseTimeMs(matcher.group(1), matcher.group(2), matcher.group(3)));
            }
            if (stamps.isEmpty()) {
                continue;
            }
            String lyricText = matcher.replaceAll("").trim();
            if (lyricText.isEmpty()) {
                continue;
            }
            for (Long stamp : stamps) {
                stampedLines.add(new StampedLine(stamp, lyricText));
            }
        }
        Collections.sort(stampedLines, new Comparator<StampedLine>() {
            @Override
            public int compare(StampedLine first, StampedLine second) {
                return Long.compare(first.timeMs, second.timeMs);
            }
        });

        ArrayList<LyricWord> words = new ArrayList<>();
        int wordIndex = 0;
        for (int lineIndex = 0; lineIndex < stampedLines.size(); lineIndex++) {
            StampedLine line = stampedLines.get(lineIndex);
            String[] pieces = line.text.split("\\s+");
            long nextTime = lineIndex + 1 < stampedLines.size()
                    ? stampedLines.get(lineIndex + 1).timeMs
                    : line.timeMs + Math.max(2200L, pieces.length * 360L);
            long duration = clamp(nextTime - line.timeMs, Math.max(900L, pieces.length * 180L), Math.max(1800L, pieces.length * 620L));
            for (int i = 0; i < pieces.length; i++) {
                String word = pieces[i].trim();
                if (word.isEmpty()) {
                    continue;
                }
                long wordTime = line.timeMs + (duration * i) / Math.max(1, pieces.length);
                words.add(new LyricWord(word, wordTime, wordIndex));
                wordIndex++;
            }
        }
        return new Result(raw, words);
    }

    private static String readRawText(Context context, int rawResourceId) throws IOException {
        InputStream inputStream = context.getResources().openRawResource(rawResourceId);
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = inputStream.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            String text = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
            if (text.startsWith("\uFEFF")) {
                return text.substring(1);
            }
            return text;
        } finally {
            inputStream.close();
        }
    }

    private static long parseTimeMs(String minuteText, String secondText, String fractionText) {
        long minutes = Long.parseLong(minuteText);
        long seconds = Long.parseLong(secondText);
        long fraction = 0L;
        if (fractionText != null && !fractionText.isEmpty()) {
            if (fractionText.length() == 1) {
                fraction = Long.parseLong(fractionText) * 100L;
            } else if (fractionText.length() == 2) {
                fraction = Long.parseLong(fractionText) * 10L;
            } else {
                fraction = Long.parseLong(fractionText.substring(0, 3));
            }
        }
        return minutes * 60_000L + seconds * 1000L + fraction;
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    public static final class Result {
        public final String rawText;
        public final List<LyricWord> words;

        public Result(String rawText, List<LyricWord> words) {
            this.rawText = rawText;
            this.words = words;
        }
    }

    private static final class StampedLine {
        final long timeMs;
        final String text;

        StampedLine(long timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text;
        }
    }
}
