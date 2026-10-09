package com.amber.ritmostyle.game;

public final class LyricWord {
    public final String text;
    public final long timeMs;
    public final int index;

    public LyricWord(String text, long timeMs, int index) {
        this.text = text;
        this.timeMs = timeMs;
        this.index = index;
    }
}
