package com.ferzdevzgta.game;

import com.ferzdevzgta.game.data.CacheDownloaderTest;
import com.ferzdevzgta.game.data.CacheManifestTest;
import com.ferzdevzgta.game.log.LogRotationTest;
import com.ferzdevzgta.game.servers.GameConfigTest;
import com.ferzdevzgta.game.servers.ServerEndpointTest;
import com.ferzdevzgta.game.servers.ServerListTest;
import com.ferzdevzgta.game.settings.IniFileTest;
import com.ferzdevzgta.game.ui.LanguageTest;

/**
 * Runs every JVM test. No JUnit, no Gradle: the classes under test avoid Android
 * imports on purpose, so {@code bin/self-test.sh} can compile them with javac and
 * fail the build in a second instead of in a Gradle task.
 */
public final class TestMain {

    public static void main(String[] args) throws Exception {
        System.out.println("-- CacheManifest");
        CacheManifestTest.run();
        System.out.println("-- CacheDownloader");
        CacheDownloaderTest.run();
        System.out.println("-- LogRotation");
        LogRotationTest.run();
        System.out.println("-- IniFile");
        IniFileTest.run();
        System.out.println("-- ServerEndpoint");
        ServerEndpointTest.run();
        System.out.println("-- ServerList");
        ServerListTest.run();
        System.out.println("-- GameConfig");
        GameConfigTest.run();
        System.out.println("-- Language");
        LanguageTest.run();
        System.exit(Check.report());
    }
}
