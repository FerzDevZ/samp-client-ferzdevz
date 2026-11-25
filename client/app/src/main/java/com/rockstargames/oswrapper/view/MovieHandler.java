// Clean-room rewrite. Provenance, pinned names and what libGame.so expects: docs/OSWRAPPER.md.
package com.rockstargames.oswrapper.view;

import android.content.res.AssetFileDescriptor;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.media.MediaPlayer;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.ferzdevzgta.game.R;
import com.rockstargames.oswrapper.GameActivityBase;

/**
 * Plays the intro movie from the APK assets onto its own surface, with a second surface used
 * as a subtitle layer drawn by hand.
 *
 * The movie path is currently disabled from the engine side: MovieServices answers "not
 * playing" and never starts one, so nothing here blocks the game on a video. The handler
 * stays complete because the surfaces are wired in the layout and the engine may ask again.
 */
public final class MovieHandler implements GameViewHandler {

    private static final String TAG = "GameView.MovieHandler";

    /** Translucent pixel format: the movie surface is composited over the game surface. */
    private static final int FORMAT_TRANSLUCENT = -3;

    private static final int SUBTITLE_COLOR = 0xFFFFFFFF;
    private static final int SUBTITLE_SHADOW_COLOR = 0xFF000000;
    private static final int SUBTITLE_DEFAULT_SIZE = 32;
    private static final int SUBTITLE_MARGIN = 50;
    private static final int SUBTITLE_LEFT = 52;
    private static final int SUBTITLE_BOTTOM = 5;
    private static final int SUBTITLE_SHADOW_OFFSET = 2;

    private GameActivityBase activity;
    private SurfaceView viewMovie;
    private SurfaceView viewMovieText;
    private MediaPlayer moviePlayer;
    private boolean moviePlaying;
    private boolean movieSurfaceReady;
    private boolean movieTextSurfaceReady;
    private String movieFilename = "";
    private String movieCurrentText = "";
    private int movieCurrentTextScale = SUBTITLE_DEFAULT_SIZE;

    // --- setup --------------------------------------------------------------

    @Override
    public void setup(GameActivityBase activity) {
        if (activity == null) {
            throw new NullPointerException("activity is null");
        }
        this.activity = activity;

        viewMovie = activity.findViewById(R.id.viewMovie);
        viewMovieText = activity.findViewById(R.id.viewMovieText);
        if (viewMovie == null) {
            throw new IllegalStateException("viewMovie not found in layout");
        }
        if (viewMovieText == null) {
            throw new IllegalStateException("viewMovieText not found in layout");
        }

        viewMovie.getHolder().setFormat(FORMAT_TRANSLUCENT);
        viewMovie.setZOrderOnTop(true);
        viewMovie.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                Log.i(TAG, "movie surface created");
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                movieSurfaceReady = true;
                tryStartPlayback();
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                movieSurfaceReady = false;
                movieCurrentText = "";
            }
        });

        viewMovieText.getHolder().setFormat(FORMAT_TRANSLUCENT);
        viewMovieText.setZOrderOnTop(true);
        viewMovieText.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                Log.i(TAG, "subtitle surface created");
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                if (!movieTextSurfaceReady) {
                    clearSurface(holder);
                }
                movieTextSurfaceReady = true;
                tryStartPlayback();
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                movieTextSurfaceReady = false;
            }
        });
    }

    // --- playback -----------------------------------------------------------

    public void playMovie(String filename, boolean skippable) throws Exception {
        if (filename == null) {
            throw new NullPointerException("filename is null");
        }
        Log.i(TAG, "playMovie: " + filename + " skippable=" + skippable);
        stopMovie();

        runOnUiThreadAndWait(() -> {
            moviePlaying = true;
            movieFilename = filename;
            if (viewMovie != null) {
                viewMovie.setVisibility(SurfaceView.VISIBLE);
            }
            if (viewMovieText != null) {
                viewMovieText.setVisibility(SurfaceView.VISIBLE);
            }
        });
    }

    public void stopMovie() throws Exception {
        Log.i(TAG, "stopMovie");
        runOnUiThreadAndWait(() -> stopMovieLocked());
    }

    /** Runs on the UI thread. Releases the player and hides both surfaces. */
    private void stopMovieLocked() {
        moviePlaying = false;
        if (viewMovie != null) {
            viewMovie.setVisibility(SurfaceView.GONE);
        }
        if (viewMovieText != null) {
            viewMovieText.setVisibility(SurfaceView.GONE);
        }
        if (moviePlayer != null) {
            moviePlayer.release();
            moviePlayer = null;
        }
    }

    private void tryStartPlayback() {
        if (!movieSurfaceReady || !movieTextSurfaceReady || movieFilename.isEmpty()) {
            return;
        }
        getActivity().runOnUiThread(() -> {
            try {
                startPlayerOnUiThread();
            } catch (Exception failure) {
                Log.e(TAG, "cannot start movie " + movieFilename, failure);
                stopMovieLocked();
            }
        });
    }

    private void startPlayerOnUiThread() throws Exception {
        if (moviePlayer != null) {
            moviePlayer.release();
            moviePlayer = null;
        }

        MediaPlayer player = new MediaPlayer();
        player.setOnPreparedListener(MediaPlayer::start);
        player.setOnErrorListener((mp, what, extra) -> {
            Log.e(TAG, "movie error " + what + " / " + extra + " for " + movieFilename);
            stopMovieLocked();
            return false;
        });
        player.setOnCompletionListener(mp -> stopMovieLocked());

        try (AssetFileDescriptor descriptor = getActivity().getAssets().openFd(movieFilename)) {
            player.setDataSource(
                    descriptor.getFileDescriptor(),
                    descriptor.getStartOffset(),
                    descriptor.getLength());
        }
        player.setDisplay(viewMovie.getHolder());
        player.prepareAsync();
        moviePlayer = player;
    }

    // --- subtitles ----------------------------------------------------------

    public void setMovieText(String text) {
        if (text == null) {
            throw new NullPointerException("text is null");
        }
        Log.i(TAG, "setMovieText: " + text);
        getActivity().runOnUiThread(() -> {
            movieCurrentText = text;
            drawMovieTextNow();
        });
    }

    public void setMovieTextScale(int scale) {
        getActivity().runOnUiThread(() -> {
            movieCurrentTextScale = scale;
            drawMovieTextNow();
        });
    }

    /** Draws the subtitle. Must be called on the UI thread, the canvas is not thread safe. */
    private void drawMovieTextNow() {
        if (!movieTextSurfaceReady || viewMovieText == null) {
            return;
        }
        SurfaceHolder holder = viewMovieText.getHolder();
        Canvas canvas = holder.lockCanvas();
        if (canvas == null) {
            return;
        }
        try {
            canvas.drawColor(0, PorterDuff.Mode.CLEAR);
            if (!movieCurrentText.isEmpty()) {
                drawSubtitle(canvas, movieCurrentText);
            }
        } finally {
            holder.unlockCanvasAndPost(canvas);
        }
    }

    private void drawSubtitle(Canvas canvas, String text) {
        int width = canvas.getWidth() - SUBTITLE_MARGIN * 2;
        if (width <= 0) {
            return;
        }
        int height = canvas.getHeight();

        TextPaint paint = new TextPaint();
        paint.setTextSize(movieCurrentTextScale);
        paint.setColor(SUBTITLE_COLOR);

        TextPaint shadow = new TextPaint();
        shadow.setTextSize(paint.getTextSize());
        shadow.setColor(SUBTITLE_SHADOW_COLOR);

        StaticLayout layout = buildLayout(text, paint, width);
        StaticLayout shadowLayout = buildLayout(text, shadow, width);

        canvas.translate(SUBTITLE_LEFT, height - shadowLayout.getHeight() - SUBTITLE_BOTTOM);
        shadowLayout.draw(canvas);
        canvas.translate(-SUBTITLE_SHADOW_OFFSET, -SUBTITLE_SHADOW_OFFSET);
        layout.draw(canvas);
    }

    private static StaticLayout buildLayout(String text, TextPaint paint, int width) {
        return StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(1.0f, 1.0f)
                .setIncludePad(true)
                .build();
    }

    private static void clearSurface(SurfaceHolder holder) {
        Canvas canvas = holder.lockCanvas();
        if (canvas == null) {
            return;
        }
        try {
            canvas.drawColor(0, PorterDuff.Mode.CLEAR);
        } finally {
            holder.unlockCanvasAndPost(canvas);
        }
    }

    // --- GameViewHandler ----------------------------------------------------

    @Override
    public void onPause() {
        if (!moviePlaying) {
            return;
        }
        try {
            stopMovie();
        } catch (Exception failure) {
            Log.w(TAG, "stopMovie failed on pause", failure);
        }
    }

    @Override
    public void onResume() {
        // The movie does not resume: the engine restarts it from the beginning.
    }

    public boolean isMoviePlaying() {
        return moviePlaying;
    }

    // --- helpers ------------------------------------------------------------

    private interface UiTask {
        void run();
    }

    /** Posts to the UI thread and waits, so the engine sees a synchronous call. */
    private void runOnUiThreadAndWait(UiTask task) throws Exception {
        java.util.concurrent.FutureTask<Void> future = new java.util.concurrent.FutureTask<>(() -> {
            task.run();
            return null;
        });
        getActivity().runOnUiThread(future);
        future.get();
    }

    private GameActivityBase getActivity() {
        if (activity == null) {
            throw new IllegalStateException("activity not set, call setup() first");
        }
        return activity;
    }
}
