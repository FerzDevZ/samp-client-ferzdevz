// Clean-room rewrite. Provenance, pinned names and what libGame.so expects: docs/OSWRAPPER.md.
package com.rockstargames.oswrapper.services;

import com.rockstargames.oswrapper.GameActivityBase;
import com.rockstargames.oswrapper.GameThread;

/**
 * Offline stand-in for the Rockstar account layer.
 *
 * The engine expects to be able to ask about sign-in, an initial gate and a user id before it
 * reaches the menu. Every call here answers immediately with a local value, so a player who
 * never touches a Rockstar server still reaches the menu. No HTTP request is made, and that is
 * the point: this client talks to SA-MP servers, not to Social Club.
 */
public final class RockstarServices {

    /** Reported to the engine as the signed-in user. */
    private static final String OFFLINE_USER = "OFFLINE_USER";

    public RockstarServices(GameActivityBase activity) {
        if (activity == null) {
            throw new IllegalArgumentException("activity is null");
        }
    }

    // --- HTTP surface, intentionally inert ----------------------------------

    public void httpCancel(int id) {
        // Nothing was ever started, so there is nothing to cancel.
    }

    public void httpGet(int id, String url, String[] names, String[] values) {
        // Deliberately empty: see the class comment.
    }

    public void httpHead(int id, String url) {
        // Deliberately empty: see the class comment.
    }

    public void httpPost(int id, String url, String[] names, String[] values, byte[] data) {
        // Deliberately empty: see the class comment.
    }

    // --- account surface, answered locally ---------------------------------

    public void rockstarFetchId() {
        GameThread.INSTANCE.onRockstarIdChanged(OFFLINE_USER);
    }

    public boolean rockstarInTrial() {
        return false;
    }

    public void rockstarRequestReview() {
        // No store review flow in a sideloaded client.
    }

    public void rockstarSetLocalePriority(String localeTag) {
        // Language priority is decided by the device locale, see LanguageServices.
    }

    public void rockstarShowCloudDisabled() {
        GameThread.INSTANCE.onRockstarCloudDisabledComplete();
    }

    /** Gates are answered as passed so the boot sequence does not stop at a dialog. */
    public void rockstarShowGate(int id) {
        GameThread.INSTANCE.onRockstarGateComplete(id, true);
    }

    public void rockstarShowInitial() {
        GameThread.INSTANCE.onRockstarInitialComplete();
    }

    public void rockstarSignIn() {
        GameThread.INSTANCE.onRockstarSignInComplete();
    }

    public void rockstarSignOut() {
        GameThread.INSTANCE.onRockstarSignOutComplete();
    }

    public void rockstarAccountDeletion() {
        GameThread.INSTANCE.onRockstarAccountDeletionComplete();
    }
}
