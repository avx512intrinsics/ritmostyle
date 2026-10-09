package com.amber.ritmostyle.game;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class PositionHasher {
    private final String hash;

    public PositionHasher(String lrcText) {
        this.hash = sha256Hex(lrcText == null ? "" : lrcText);
    }

    public SpawnPoint pointForWord(int wordIndex) {
        int xValue = valueFromHash(wordIndex, 3);
        int yValue = valueFromHash(wordIndex, 19);
        int driftValue = valueFromHash(wordIndex, 41);
        float x = 0.12f + (xValue % 760) / 1000f;
        float y = 0.16f + (yValue % 640) / 1000f;
        float drift = ((driftValue % 200) - 100) / 1000f;
        return new SpawnPoint(clamp01(x + drift * 0.25f), clamp01(y - drift * 0.18f));
    }

    private int valueFromHash(int wordIndex, int salt) {
        int value = 0;
        for (int i = 0; i < 6; i++) {
            int position = Math.floorMod(wordIndex * 11 + salt + i * 7, hash.length());
            value = value * 16 + Character.digit(hash.charAt(position), 16);
        }
        return value;
    }

    private static float clamp01(float value) {
        return Math.max(0.08f, Math.min(0.92f, value));
    }

    private static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            char[] chars = new char[bytes.length * 2];
            char[] alphabet = "0123456789abcdef".toCharArray();
            for (int i = 0; i < bytes.length; i++) {
                int value = bytes[i] & 0xff;
                chars[i * 2] = alphabet[value >>> 4];
                chars[i * 2 + 1] = alphabet[value & 0x0f];
            }
            return new String(chars);
        } catch (NoSuchAlgorithmException exception) {
            return "0123456789abcdef0123456789abcdef";
        }
    }

    public static final class SpawnPoint {
        public final float x;
        public final float y;

        SpawnPoint(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }
}
