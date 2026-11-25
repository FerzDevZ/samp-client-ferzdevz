// Clean-room rewrite. Provenance, pinned names and what libGame.so expects: docs/OSWRAPPER.md.
package com.rockstargames.oswrapper.view;

import com.rockstargames.oswrapper.GameActivityBase;

/**
 * Everything {@code GameView} owns and drives through one lifecycle.
 *
 * {@code GameView} calls these in list order, so a handler that needs another one to be ready
 * first is ordered earlier in {@code GameView}'s constructor.
 */
public interface GameViewHandler {

    void onPause();

    void onResume();

    /** Called once the activity has laid out its views. */
    void setup(GameActivityBase activity);
}
