#!/usr/bin/env python3
"""Rebuild the launcher artwork from one source picture.

The app icon is a pen illustration, not a traced vector. Tracing it would throw away the line work
and the lettering, so the foreground is a bitmap per density instead:

    mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher_foreground.png

Each file is a rounded square of art on a transparent canvas, sized for the adaptive icon's 108 dp
viewport and left inside the safe zone, so a launcher mask crops transparent corners and never the
drawing. `drawable-{density}/ic_brand.png` is the same square at 96 dp, used by the splash window
and the loading screen, so all three places show one mark.

Source picture is not in the repository: pass it with --source. The generated PNGs are committed,
because the build needs them and the crop is reproducible from the numbers below.

    python3 bin/make-icons.py --source ~/icon-source.jpg [--box 872,256,1880,1264]
"""

import argparse
import os
import sys

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                   "..", "client", "app", "src", "main", "res")
DENSITIES = [("mdpi", 108), ("hdpi", 162), ("xhdpi", 216), ("xxhdpi", 324), ("xxxhdpi", 432)]
# Share of the adaptive-icon viewport the artwork occupies. Below 0.80 the icon looks shrunken next
# to its neighbours, above 0.86 a circular mask starts cutting the drawing's corners.
ART_SHARE = 0.78
CORNER_RATIO = 0.225


def squircle_mask(size):
    from PIL import Image, ImageDraw
    scale = 4
    big = Image.new("L", (size * scale, size * scale), 0)
    ImageDraw.Draw(big).rounded_rectangle(
        [0, 0, size * scale - 1, size * scale - 1],
        radius=int(size * scale * CORNER_RATIO), fill=255)
    return big.resize((size, size), Image.LANCZOS)


def find_box(im):
    """Locate the icon inside a screenshot by dropping the flat letterbox around it."""
    from PIL import Image
    w, h = im.size
    bg = im.getpixel((2, 2))
    px = im.load()
    step = max(1, min(w, h) // 400)

    def differs(x, y):
        r, g, b = px[x, y]
        return abs(r - bg[0]) + abs(g - bg[1]) + abs(b - bg[2]) > 24

    left, right, top, bottom = w, 0, h, 0
    for y in range(0, h, step):
        for x in range(0, w, step):
            if differs(x, y):
                left = min(left, x)
                right = max(right, x)
                top = min(top, y)
                bottom = max(bottom, y)
    if right <= left or bottom <= top:
        return None
    # Square it off around the centre; the artwork is a square icon on a wider screenshot.
    side = max(right - left, bottom - top)
    cx, cy = (left + right) // 2, (top + bottom) // 2
    x0, y0 = max(0, cx - side // 2), max(0, cy - side // 2)
    return (x0, y0, min(w, x0 + side), min(h, y0 + side))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--source", required=True, help="picture containing the app artwork")
    ap.add_argument("--box", help="crop as x0,y0,x1,y1 when detection picks the wrong square")
    args = ap.parse_args()

    try:
        from PIL import Image
    except ImportError:
        sys.stderr.write("Pillow is missing. python3 -m venv /tmp/venv && "
                         "/tmp/venv/bin/pip install pillow, then run with /tmp/venv/bin/python.\n")
        return 2

    im = Image.open(args.source).convert("RGB")
    box = tuple(int(v) for v in args.box.split(",")) if args.box else find_box(im)
    if box is None:
        sys.stderr.write("Could not find the artwork in %s. Pass --box.\n" % args.source)
        return 1
    art = im.crop(box)
    if art.width != art.height:
        sys.stderr.write("Crop is %dx%d, not square.\n" % art.size)
        return 1
    print("source %s, art %dx%d at %s" % (args.source, art.width, art.height, box))

    for name, size in DENSITIES:
        canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        inner = int(size * ART_SHARE)
        piece = art.resize((inner, inner), Image.LANCZOS).convert("RGBA")
        piece.putalpha(squircle_mask(inner))
        canvas.paste(piece, ((size - inner) // 2, (size - inner) // 2), piece)
        out = os.path.join(RES, "mipmap-%s" % name, "ic_launcher_foreground.png")
        os.makedirs(os.path.dirname(out), exist_ok=True)
        canvas.save(out, optimize=True)
        print("wrote %s %dx%d" % (out, size, size))

    brand = art.resize((384, 384), Image.LANCZOS).convert("RGBA")
    brand.putalpha(squircle_mask(384))
    # One file per density at 96 dp, not one nodpi file: a layer-list item uses the bitmap's
    # intrinsic size, so a single 384 px asset would draw the same physical size on every screen.
    scales = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}
    for name, factor in scales.items():
        side = int(96 * factor)
        piece = brand.resize((side, side), Image.LANCZOS)
        out = os.path.join(RES, "drawable-%s" % name, "ic_brand.png")
        os.makedirs(os.path.dirname(out), exist_ok=True)
        piece.save(out, optimize=True)
        print("wrote %s %dx%d" % (out, side, side))
    return 0


if __name__ == "__main__":
    sys.exit(main())
