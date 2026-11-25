#!/usr/bin/env python3
"""Check that the launcher icon is wired end to end, not just drawn.

Why this exists: the manifest once pointed `android:icon` at
`@drawable/ic_launcher_background`, the adaptive icon's own background layer. The build stayed
green, the PNG files were all there, and every phone showed an empty navy square. Nothing else in
the toolchain compares the manifest reference with the icon files, so this check does.

    python3 bin/icon-check.py [--res RES] [--manifest AndroidManifest.xml]
"""

import argparse
import os
import struct
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
DEFAULT_RES = os.path.join(ROOT, "client/app/src/main/res")
DEFAULT_MANIFEST = os.path.join(ROOT, "client/app/src/main/AndroidManifest.xml")
DENSITIES = ["mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"]
ANDROID = "{http://schemas.android.com/apk/res/android}"


def png_is_square(path):
    """Reads only the IHDR width/height, so the check does not need Pillow."""
    with open(path, "rb") as fh:
        head = fh.read(33)
    if head[:8] != b"\x89PNG\r\n\x1a\n" or head[12:16] != b"IHDR":
        return None
    width, height = struct.unpack(">II", head[16:24])
    return width, height


def check(res, manifest_path):
    problems = []

    try:
        app = ET.parse(manifest_path).getroot().find("application")
    except (ET.ParseError, OSError) as exc:
        return ["manifest unreadable: %s" % exc]
    if app is None:
        return ["manifest has no <application>"]

    icon = (app.get(ANDROID + "icon") or "").strip()
    round_icon = (app.get(ANDROID + "roundIcon") or "").strip()
    if icon != "@mipmap/ic_launcher":
        problems.append("android:icon is %r, so the launcher draws whatever that points at "
                        "instead of the adaptive icon" % icon)
    if round_icon != "@mipmap/ic_launcher_round":
        problems.append("android:roundIcon is %r, round launchers fall back to the square icon"
                        % round_icon)

    for name in ("ic_launcher", "ic_launcher_round"):
        path = os.path.join(res, "mipmap-anydpi-v26", name + ".xml")
        if not os.path.isfile(path):
            problems.append("missing adaptive icon mipmap-anydpi-v26/" + name + ".xml")
            continue
        try:
            node = ET.parse(path).getroot()
        except ET.ParseError as exc:
            problems.append("%s does not parse: %s" % (name, exc))
            continue
        layers = {}
        for child in node:
            if isinstance(child.tag, str):
                layers[child.tag] = (child.get(ANDROID + "drawable") or "").strip()
        if layers.get("foreground") != "@mipmap/ic_launcher_foreground":
            problems.append("%s foreground is %r, not the per-density artwork"
                            % (name, layers.get("foreground")))
        if layers.get("background") != "@drawable/ic_launcher_background":
            problems.append("%s background is %r" % (name, layers.get("background")))

    for density in DENSITIES:
        path = os.path.join(res, "mipmap-%s" % density, "ic_launcher_foreground.png")
        if not os.path.isfile(path):
            problems.append("missing mipmap-%s/ic_launcher_foreground.png" % density)
            continue
        size = png_is_square(path)
        if size is None:
            problems.append("mipmap-%s/ic_launcher_foreground.png is not a PNG" % density)
        elif size[0] != size[1]:
            problems.append("mipmap-%s/ic_launcher_foreground.png is %dx%d, not square" % (density, size[0], size[1]))

    # A leftover bitmap icon would be shipped to every device and, on API 26+, silently ignored.
    for density in DENSITIES:
        stale = os.path.join(res, "mipmap-%s" % density, "ic_launcher.webp")
        if os.path.isfile(stale):
            problems.append("stale legacy icon mipmap-%s/ic_launcher.webp" % density)

    for density in DENSITIES:
        path = os.path.join(res, "drawable-%s" % density, "ic_brand.png")
        if not os.path.isfile(path):
            problems.append("missing drawable-%s/ic_brand.png" % density)

    # The splash window and the loading screen have to show the same mark as the launcher.
    for rel in ("drawable/splash_window.xml", "layout/loadingscreen.xml"):
        path = os.path.join(res, rel)
        if not os.path.isfile(path):
            problems.append("missing " + rel)
            continue
        with open(path, encoding="utf-8") as fh:
            text = fh.read()
        if "ic_brand" not in text:
            problems.append(rel + " does not use @drawable/ic_brand")
        if "ic_splash_mark" in text:
            problems.append(rel + " still points at the deleted ic_splash_mark")
    if os.path.isfile(os.path.join(res, "drawable/ic_splash_mark.xml")):
        problems.append("drawable/ic_splash_mark.xml came back; the mark is ic_brand now")

    return problems


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--res", default=DEFAULT_RES)
    ap.add_argument("--manifest", default=DEFAULT_MANIFEST)
    args = ap.parse_args()
    problems = check(args.res, args.manifest)
    if problems:
        for line in problems:
            print("  " + line)
        return 1
    count = len(DENSITIES)
    print("%d icon densities, adaptive icon, splash and loading mark all agree" % count)
    return 0


if __name__ == "__main__":
    sys.exit(main())
