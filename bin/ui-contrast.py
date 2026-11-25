#!/usr/bin/env python3
"""Assert the UI palette in Ui.java is readable, instead of trusting it.

Reads the colour constants out of the Kotlin/Java source, then checks every pair the screens
actually use against WCAG 2.2 AA: 4.5:1 for body text, 3:1 for large text and for the outline of
an interactive control. No Android SDK, no rendering: the numbers are computed from the source
values, so a too-dark hint colour fails here and not on a phone.

    bin/ui-contrast.py            check, exit 1 on failure
    bin/ui-contrast.py --verbose  print every ratio, including the passing ones
"""

import argparse
import re
import sys

SOURCE = "client/app/src/main/java/com/ferzdevzgta/game/ui/Ui.java"

# name: (minimum ratio, reason)
PAIRS = {
    ("TEXT", "BG"): (4.5, "body text on the page"),
    ("TEXT", "SURFACE"): (4.5, "body text on a card"),
    ("TEXT", "SURFACE_2"): (4.5, "input text on its field"),
    ("MUTED", "BG"): (4.5, "secondary text on the page"),
    ("MUTED", "SURFACE"): (4.5, "secondary text on a card"),
    ("MUTED", "SURFACE_2"): (4.5, "hint text on an input"),
    ("FAINT", "BG"): (4.5, "labels on the page"),
    ("FAINT", "SURFACE"): (4.5, "labels on a card"),
    ("FAINT", "SURFACE_2"): (4.5, "eyebrow and hint on a field"),
    ("ACCENT", "BG"): (4.5, "accent text on the page"),
    ("ACCENT", "SURFACE"): (4.5, "accent text on a card"),
    ("ACCENT_INK", "ACCENT"): (4.5, "label on the primary button"),
    ("OK", "SURFACE"): (4.5, "success pill text"),
    ("WARN", "SURFACE"): (4.5, "warning pill text"),
    ("ERR", "SURFACE"): (4.5, "error text"),
    ("ERR", "BG"): (4.5, "error text on the page"),
    ("STROKE", "BG"): (1.2, "card edge must be visible at all"),
}


def read_palette(path):
    text = open(path, encoding="utf-8").read()
    found = re.findall(r"public static final int ([A-Z_0-9]+) = 0x([0-9A-Fa-f]{8});", text)
    if not found:
        raise SystemExit("no colour constants found in " + path)
    palette = {}
    for name, argb in found:
        palette[name] = (int(argb[2:4], 16), int(argb[4:6], 16), int(argb[6:8], 16))
    return palette


def channel(value):
    value /= 255.0
    return value / 12.92 if value <= 0.03928 else ((value + 0.055) / 1.055) ** 2.4


def luminance(rgb):
    r, g, b = (channel(c) for c in rgb)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def ratio(first, second):
    high, low = sorted((luminance(first), luminance(second)), reverse=True)
    return (high + 0.05) / (low + 0.05)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--verbose", action="store_true")
    parser.add_argument("--source", default=SOURCE)
    args = parser.parse_args()

    palette = read_palette(args.source)
    failures = 0
    for (front, back), (minimum, reason) in PAIRS.items():
        if front not in palette or back not in palette:
            print("  FAIL %s on %s: colour is missing from %s" % (front, back, args.source))
            failures += 1
            continue
        value = ratio(palette[front], palette[back])
        passed = value >= minimum
        if args.verbose or not passed:
            print("  %s %-10s on %-10s %5.2f (needs %.1f, %s)"
                  % ("ok  " if passed else "FAIL", front, back, value, minimum, reason))
        if not passed:
            failures += 1

    if failures:
        print("\n%d contrast failure(s)" % failures)
        return 1
    print("%d colour pairs meet WCAG 2.2 AA" % len(PAIRS))
    return 0


if __name__ == "__main__":
    sys.exit(main())
