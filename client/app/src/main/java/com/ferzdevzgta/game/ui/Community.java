package com.ferzdevzgta.game.ui;

/**
 * Where bug reports and build questions go.
 *
 * <p>The invite lives in exactly one place. It is a constant rather than a string resource so a
 * translation cannot send Indonesian users to a different room, and this class has no Android
 * imports so {@code CommunityTest} can pin its shape in the plain JVM suite: a typo fails the
 * build instead of producing a dead button on a phone.
 */
public final class Community {

    /** Bug reports, build questions, server reports. */
    public static final String INVITE = "https://discord.gg/fkhs4TXf";

    private Community() {
    }

    /** True when the shipped constant still looks like an invite. Called by the test. */
    public static boolean inviteIsValid() {
        return looksValid(INVITE);
    }

    /**
     * Shape check. Discord invites are an https link on {@code discord.gg} with a short
     * alphanumeric code; anything else is a typo or somebody's tracking redirect.
     */
    public static boolean looksValid(String url) {
        if (url == null) {
            return false;
        }
        String trimmed = url.trim();
        if (!trimmed.startsWith("https://discord.gg/")) {
            return false;
        }
        String code = trimmed.substring("https://discord.gg/".length());
        if (code.isEmpty() || code.length() > 32) {
            return false;
        }
        for (int i = 0; i < code.length(); i++) {
            char ch = code.charAt(i);
            boolean ok = (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')
                    || (ch >= '0' && ch <= '9');
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}
