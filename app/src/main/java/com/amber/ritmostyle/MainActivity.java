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
    private final Handler handler = new Handler(Looper.getMainLooper());

    private VideoView videoView;
    private RitmoGameView gameView;
    private Runnable previewStopper;
    private Level currentLevel;

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
        playMenuBackground(currentLevel);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        if (videoView != null && !videoView.isPlaying()) {
            videoView.start();
        }
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
        playVideo(level, true, 0, 0.7f, false);
    }

    @Override
    public void playLevelPreview(Level level) {
        currentLevel = level;
        playVideo(level, false, level.previewStartMs, 0.65f, false);
        previewStopper = new Runnable() {
            @Override
            public void run() {
                if (gameView != null && gameView.isSelectingLevel() && currentLevel == level) {
                    videoView.pause();
                }
            }
        };
        handler.postDelayed(previewStopper, 5000L);
    }

    @Override
    public void playGameplay(Level level) {
        playVideo(level, false, 0, 1.0f, true);
    }

    @Override
    public void playEndLoop(Level level) {
        playVideo(level, true, 0, 0.55f, false);
    }

    @Override
    public int currentVideoPositionMs() {
        return videoView == null ? 0 : videoView.getCurrentPosition();
    }

    @Override
    public int videoDurationMs() {
        return videoView == null ? 0 : videoView.getDuration();
    }

    private void playVideo(Level level, boolean loop, int startMs, float volume, boolean notifyGameCompletion) {
        cancelPreviewStopper();
        currentLevel = level;
        Uri uri = Uri.parse("android.resource://" + getPackageName() + "/" + level.videoResId);
        videoView.stopPlayback();
        videoView.setVideoURI(uri);
        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
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
    }

    private void cancelPreviewStopper() {
        if (previewStopper != null) {
            handler.removeCallbacks(previewStopper);
            previewStopper = null;
        }
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
