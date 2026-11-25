package com.ferzdevzgta.game.ui;

import com.ferzdevzgta.game.Check;

/**
 * Pins the support invite. A typo here is a button on the launcher that opens a dead page, which
 * no compiler and no screenshot would catch.
 */
public final class CommunityTest {

    private CommunityTest() {
    }

    public static void run() {
        Check.ok("the invite is the project room",
                "fkhs4TXf".equals(Community.INVITE.substring("https://discord.gg/".length())));
        Check.eq("the invite is https", true, Community.inviteIsValid());
        Check.eq("https is required", false, Community.looksValid("http://discord.gg/fkhs4TXf"));
        Check.eq("the host is discord.gg, not discord.com",
                false, Community.looksValid("https://discord.com/invite/fkhs4TXf"));
        Check.eq("a bare host is not an invite", false, Community.looksValid("https://discord.gg/"));
        Check.eq("a space would break the link",
                false, Community.looksValid("https://discord.gg/fkh s4TX"));
        Check.eq("a query string is a redirect, not an invite",
                false, Community.looksValid("https://discord.gg/fkhs4TXf?utm=x"));
        Check.eq("null is refused", false, Community.looksValid(null));
        Check.eq("an overlong code is refused",
                false, Community.looksValid("https://discord.gg/" + "a".repeat(33)));
        Check.ok("a hand written invite still passes",
                Community.looksValid("https://discord.gg/abc123"));
    }
}
