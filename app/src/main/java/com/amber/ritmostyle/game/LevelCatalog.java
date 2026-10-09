package com.amber.ritmostyle.game;

import com.amber.ritmostyle.R;

import java.util.ArrayList;
import java.util.List;

public final class LevelCatalog {
    private LevelCatalog() {
    }

    public static List<Level> load() {
        ArrayList<Level> levels = new ArrayList<>();
        levels.add(new Level(
                "bad_apple",
                "Bad Apple!!",
                "Alstroemeria Records / nomico",
                R.raw.bad_apple_video,
                R.raw.bad_apple_lrc,
                44000,
                219_183
        ));
        return levels;
    }
}
