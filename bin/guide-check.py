#!/usr/bin/env python3
"""Checks the offline guide and the only bridge it is allowed to touch.

The page is a local asset with JavaScript enabled, so the interesting question is not whether it
renders but what it can reach. Everything here is a refusal test: an unknown Panel method, a remote
asset, or a bridge that grew a fourth method fails the check.
"""

import argparse
import re
import sys

PAGE = "client/app/src/main/assets/help/help.html"
BRIDGE = "client/app/src/main/java/com/ferzdevzgta/game/ui/WebPanelActivity.java"
ALLOWED = {"locale", "version", "openDiagnostics", "openDiscord"}


def fail(message):
    print(message)
    return 1


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--page", default=PAGE)
    parser.add_argument("--bridge", default=BRIDGE)
    args = parser.parse_args()

    page = open(args.page, encoding="utf-8").read()
    bridge = open(args.bridge, encoding="utf-8").read()

    used = set(re.findall(r"Panel\.([A-Za-z_]+)", page))
    unknown = sorted(used - ALLOWED)
    if unknown:
        return fail("page calls methods the bridge does not define: " + ", ".join(unknown))
    if not used:
        return fail("page calls no bridge method at all")

    declared = set(re.findall(r"@JavascriptInterface\s+public \w+ (\w+)\(", bridge))
    if declared != ALLOWED:
        return fail("bridge surface changed: " + ", ".join(sorted(declared)))

    # Nothing may be fetched: the page ships inside the APK.
    if re.search(r"<(script|link)[^>]+(src|href)=", page):
        return fail("page loads a remote asset")
    if re.search(r"https?://", page):
        return fail("page contains an absolute network URL")

    for table in ("en:", "in:"):
        if table not in page:
            return fail("page is missing the " + table[:-1] + " table")

    print("three bridge methods, no remote assets, both languages present")
    return 0


if __name__ == "__main__":
    sys.exit(main())
