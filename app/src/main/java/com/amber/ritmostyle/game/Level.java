package com.amber.ritmostyle.game;

public final class Level {
    public final String id;
    public final String title;
    public final String artist;
    public final int videoResId;
    public final int lrcResId;
    public final int previewStartMs;

    public Level(String id, String title, String artist, int videoResId, int lrcResId, int previewStartMs) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.videoResId = videoResId;
        this.lrcResId = lrcResId;
        this.previewStartMs = previewStartMs;
    }
}
