package com.rockstargames.oswrapper.services;

import com.rockstargames.oswrapper.GameActivityBase;
import com.rockstargames.oswrapper.GameThread;

/**
 * ئەم کلاسە بە تەواوی پەکخراوە.
 * هیچ پەیوەندییەک بە ئینتەرنێت یان سێرڤەرەکانی ڕۆکستارەوە ئەنجام نادات.
 */
public final class RockstarServices {

    public RockstarServices(GameActivityBase activity) {
        // هیچ ناکات
    }

    // --- بەشی HTTP (هەمووی بەتاڵ کراوەتەوە) ---

public void httpCancel(int id) {}
public void httpGet(int id, String url, String[] names, String[] values) {}
public void httpHead(int id, String url) {}
public void httpPost(int id, String url, String[] names, String[] values, byte[] data) {}

    // --- بەشی خزمەتگوزارییەکان (تەنها وەڵامی سەرکەوتنی وەهمی دەنێرێتەوە بۆ ئەوەی یارییەکە نەوەستێت) ---

public void rockstarFetchId() {
        // ناردنی ناسنامەیەکی بەتاڵ بۆ ئەوەی یارییەکە بەردەوام بێت
        GameThread.INSTANCE.onRockstarIdChanged("OFFLINE_USER");
    }

public boolean rockstarInTrial() {
        return false;
    }

public void rockstarRequestReview() {}
public void rockstarSetLocalePriority(String localeTag) {}

public void rockstarShowCloudDisabled() {
        GameThread.INSTANCE.onRockstarCloudDisabledComplete();
    }

public void rockstarShowGate(int id) {
        // ڕاستەوخۆ دەڵێین سەرکەوتوو بوو (true) تاوەکو یارییەکە یەکسەر دەست پێ بکات
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