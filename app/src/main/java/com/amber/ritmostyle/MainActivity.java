package com.amber.ritmostyle;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.VideoView;

import com.amber.ritmostyle.game.Level;
import com.amber.ritmostyle.game.LevelCatalog;
import com.amber.ritmostyle.ui.RitmoGameView;

import java.util.List;

public final class MainActivity extends Activity implements RitmoGameView.Host {
    private static final String TAG = "RitmoMain";

    private final Handler handler = new Handler(Looper.getMainLooper());

    private VideoView videoView;
    private RitmoGameView gameView;
    private Runnable fallbackCompletion;
    private Level currentLevel;
    private boolean fallbackPlayheadActive;
    private boolean videoSourceActive;
    private long fallbackStartUptimeMs;
    private int fallbackStartPositionMs;
    private int fallbackDurationMs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUi();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        videoView = new VideoView(this);
        videoView.setBackgroundColor(Color.BLACK);
        videoView.setVisibility(View.INVISIBLE);
        root.addView(videoView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        List<Level> levels = LevelCatalog.load();
        currentLevel = levels.get(0);
        gameView = new RitmoGameView(this, this);
        gameView.setLevels(levels, currentLevel);
        root.addView(gameView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        if (videoView != null && videoSourceActive && !fallbackPlayheadActive && !videoView.isPlaying()) {
            videoView.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoView != null && videoSourceActive && !fallbackPlayheadActive && videoView.isPlaying()) {
            videoView.pause();
        }
    }

    @Override
    protected void onDestroy() {
        stopVideoPlayback();
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUi();
        }
    }

    @Override
    public void playMenuBackground(Level level) {
        showStaticBackdrop(level);
    }

    @Override
    public void playLevelPreview(Level level) {
        showStaticBackdrop(level);
    }

    @Override
    public void playGameplay(Level level) {
        playVideo(level, false, 0, 1.0f, true);
    }

    @Override
    public void playEndLoop(Level level) {
        showStaticBackdrop(level);
    }

    @Override
    public int currentVideoPositionMs() {
        if (fallbackPlayheadActive) {
            return fallbackVideoPositionMs();
        }
        return videoView == null ? 0 : videoView.getCurrentPosition();
    }

    @Override
    public int videoDurationMs() {
        if (fallbackPlayheadActive) {
            return fallbackDurationMs;
        }
        if (videoView == null) {
            return currentLevel == null ? 0 : currentLevel.durationMs;
        }
        int duration = videoView.getDuration();
        if (duration > 0) {
            return duration;
        }
        return currentLevel == null ? 0 : currentLevel.durationMs;
    }

    private void playVideo(Level level, boolean loop, int startMs, float volume, boolean notifyGameCompletion) {
        stopVideoPlayback();
        currentLevel = level;
        if (!shouldUsePlatformVideo()) {
            Log.i(TAG, "Using timer fallback instead of platform video playback");
            startFallbackPlayhead(level, startMs, loop, notifyGameCompletion);
            return;
        }
        Uri uri = Uri.parse("android.resource://" + getPackageName() + "/" + level.videoResId);
        videoSourceActive = true;
        videoView.setVisibility(View.VISIBLE);
        videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mediaPlayer, int what, int extra) {
                Log.w(TAG, "Video playback failed: " + what + "/" + extra);
                startFallbackPlayhead(level, startMs, loop, notifyGameCompletion);
                return true;
            }
        });
        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                fallbackPlayheadActive = false;
                cancelFallbackCompletion();
                mediaPlayer.setLooping(loop);
                mediaPlayer.setVolume(volume, volume);
                if (startMs > 0) {
                    videoView.seekTo(startMs);
                }
                videoView.start();
            }
        });
        videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                if (notifyGameCompletion && gameView != null) {
                    gameView.onVideoCompleted();
                }
            }
        });
        videoView.setVideoURI(uri);
    }

    private void showStaticBackdrop(Level level) {
        currentLevel = level;
        stopVideoPlayback();
    }

    private void stopVideoPlayback() {
        videoSourceActive = false;
        fallbackPlayheadActive = false;
        cancelFallbackCompletion();
        if (videoView == null) {
            return;
        }
        videoView.setOnErrorListener(null);
        videoView.setOnPreparedListener(null);
        videoView.setOnCompletionListener(null);
        videoView.stopPlayback();
        videoView.setVisibility(View.INVISIBLE);
    }

    private static boolean shouldUsePlatformVideo() {
        return Build.VERSION.SDK_INT > Build.VERSION_CODES.M;
    }

    private void startFallbackPlayhead(Level level, int startMs, boolean loop, boolean notifyGameCompletion) {
        videoSourceActive = false;
        fallbackPlayheadActive = true;
        fallbackStartUptimeMs = SystemClock.uptimeMillis();
        fallbackStartPositionMs = clamp(startMs, 0, level.durationMs);
        fallbackDurationMs = level.durationMs;
        if (videoView != null) {
            videoView.setVisibility(View.INVISIBLE);
        }
        if (notifyGameCompletion && gameView != null && !loop) {
            fallbackCompletion = new Runnable() {
                @Override
                public void run() {
                    if (fallbackPlayheadActive && currentLevel == level && gameView != null) {
                        gameView.onVideoCompleted();
                    }
                }
            };
            handler.postDelayed(fallbackCompletion, Math.max(0, level.durationMs - fallbackStartPositionMs));
        }
    }

    private int fallbackVideoPositionMs() {
        long elapsedMs = SystemClock.uptimeMillis() - fallbackStartUptimeMs;
        return clamp((int) Math.min(Integer.MAX_VALUE, fallbackStartPositionMs + elapsedMs), 0, fallbackDurationMs);
    }

    private void cancelFallbackCompletion() {
        if (fallbackCompletion != null) {
            handler.removeCallbacks(fallbackCompletion);
            fallbackCompletion = null;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void hideSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            View decorView = getWindow().getDecorView();
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }
    }
}
