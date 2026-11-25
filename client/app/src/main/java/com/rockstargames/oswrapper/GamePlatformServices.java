// Clean-room rewrite. Provenance, pinned names and what libGame.so expects: docs/OSWRAPPER.md.
package com.rockstargames.oswrapper;

import com.rockstargames.oswrapper.services.LanguageServices;
import com.rockstargames.oswrapper.services.MiscServices;
import com.rockstargames.oswrapper.services.MovieServices;
import com.rockstargames.oswrapper.services.PlaylistServices;
import com.rockstargames.oswrapper.services.RockstarServices;
import com.rockstargames.oswrapper.services.SplashScreenServices;

/**
 * The Java side of the engine's platform callbacks: movies, playlists, HTTP, the splash
 * screen, the locale and the app version.
 *
 * Every method name and signature below is looked up by literal name from libGame.so, so
 * the shape of this class is fixed even though the bodies are ours. The class deliberately
 * has no interface hierarchy: an interface here would only mirror a C++ vtable, and nothing
 * in the binary ever mentions those interface names.
 */
public final class GamePlatformServices {

    /** Read by Java callers and by the engine. Do not make private. */
    public final GameActivityBase activity;
    public final GameView view;

    private final LanguageServices languageServices;
    private final MovieServices movieServices;
    private final PlaylistServices playlistServices;
    private final RockstarServices rockstarServices;
    private final SplashScreenServices splashScreenServices;
    private final MiscServices miscServices;

    public GamePlatformServices(GameActivityBase activity, GameView view) {
        this.activity = require(activity, "activity");
        this.view = require(view, "view");

        this.languageServices = new LanguageServices(this.activity);
        this.movieServices = new MovieServices(this.view);
        this.playlistServices = new PlaylistServices(this.view);
        this.rockstarServices = new RockstarServices(this.activity);
        this.splashScreenServices = new SplashScreenServices(this.view);
        this.miscServices = new MiscServices(this.activity);
    }

    /**
     * The engine passes strings it received from the network or from a data file. A null
     * one is a bug in the caller, so it fails loudly here instead of turning into a
     * NullPointerException inside a collaborator, where the stack would name the wrong class.
     */
    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new NullPointerException(name + " is null");
        }
        return value;
    }

    // --- locale -------------------------------------------------------------

    public int getDeviceLocale() {
        return languageServices.getDeviceLocale();
    }

    // --- movies -------------------------------------------------------------

    public boolean isMoviePlaying() {
        return movieServices.isMoviePlaying();
    }

    public void playMovie(String filename, boolean skippable) {
        movieServices.playMovie(require(filename, "filename"), skippable);
    }

    public void stopMovie() {
        movieServices.stopMovie();
    }

    public void setMovieText(String text) {
        movieServices.setMovieText(require(text, "text"));
    }

    public void setMovieTextScale(int scale) {
        movieServices.setMovieTextScale(scale);
    }

    // --- playlists ----------------------------------------------------------

    public int playlistCount() {
        return playlistServices.playlistCount();
    }

    public boolean playlistIsPlaying() {
        return playlistServices.playlistIsPlaying();
    }

    public void playlistOpen(String name) {
        playlistServices.playlistOpen(require(name, "name"));
    }

    public void playlistPause() {
        playlistServices.playlistPause();
    }

    public void playlistPlay() {
        playlistServices.playlistPlay();
    }

    public void playlistSetVolume(float volume) {
        playlistServices.playlistSetVolume(volume);
    }

    public void playlistStop() {
        playlistServices.playlistStop();
    }

    // --- HTTP and the Rockstar account --------------------------------------
    // The account surface is kept because the engine calls it; it reports "signed out"
    // and answers nothing, because this build has no Rockstar account.

    public void httpCancel(int id) {
        rockstarServices.httpCancel(id);
    }

    public void httpGet(int id, String url, String[] headerNames, String[] headerValues) {
        rockstarServices.httpGet(
                id,
                require(url, "url"),
                require(headerNames, "headerNames"),
                require(headerValues, "headerValues"));
    }

    public void httpHead(int id, String url) {
        rockstarServices.httpHead(id, require(url, "url"));
    }

    public void httpPost(
            int id, String url, String[] headerNames, String[] headerValues, byte[] data) {
        rockstarServices.httpPost(
                id,
                require(url, "url"),
                require(headerNames, "headerNames"),
                require(headerValues, "headerValues"),
                require(data, "data"));
    }

    public void rockstarAccountDeletion() {
        rockstarServices.rockstarAccountDeletion();
    }

    public void rockstarFetchId() {
        rockstarServices.rockstarFetchId();
    }

    public boolean rockstarInTrial() {
        return rockstarServices.rockstarInTrial();
    }

    public void rockstarRequestReview() {
        rockstarServices.rockstarRequestReview();
    }

    public void rockstarSetLocalePriority(String localeTag) {
        rockstarServices.rockstarSetLocalePriority(localeTag);
    }

    public void rockstarShowCloudDisabled() {
        rockstarServices.rockstarShowCloudDisabled();
    }

    public void rockstarShowGate(int id) {
        rockstarServices.rockstarShowGate(id);
    }

    public void rockstarShowInitial() {
        rockstarServices.rockstarShowInitial();
    }

    public void rockstarSignIn() {
        rockstarServices.rockstarSignIn();
    }

    public void rockstarSignOut() {
        rockstarServices.rockstarSignOut();
    }

    // --- splash screen ------------------------------------------------------

    public void hideSplashScreen() {
        splashScreenServices.hideSplashScreen();
    }

    public boolean isSplashScreenVisible() {
        return splashScreenServices.isSplashScreenVisible();
    }

    public void setSplashImage(String filename) {
        splashScreenServices.setSplashImage(require(filename, "filename"));
    }

    public void setSplashText(String text) {
        splashScreenServices.setSplashText(require(text, "text"));
    }

    public void showSplashScreen() {
        splashScreenServices.showSplashScreen();
    }

    // --- misc ---------------------------------------------------------------

    public String getAppVersion() {
        return miscServices.getAppVersion();
    }

    public void openLink(String url) {
        miscServices.openLink(require(url, "url"));
    }

    public void quit() {
        miscServices.quit();
    }
}
