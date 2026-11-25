#!/usr/bin/env bash
# Build the game cache that an SA-MP Android client reads from
#   /storage/emulated/0/GTA/
#
# The cache is Rockstar's GTA San Andreas data. It is NEVER published by this
# repo: you point this script at a copy you already own (Play Store APK + OBB,
# or an APK you dumped from your own device) and it produces the two layouts the
# community calls "full" and "lite":
#
#   full  — every asset: radio, cutscene movies, textures, audio
#   lite  — audio/ and movies/ removed: smaller download, no radio, no cutscenes
#
# Usage
#   bin/build-cache.sh --apk GTA.apk --obb main.8.com.rockstargames.gtasa.obb --out cache
#   bin/build-cache.sh --dir /sdcard/Android/obb/com.rockstargames.gtasa --out cache
#   bin/build-cache.sh --out cache --verify
#
# Output
#   cache/full/                 full asset tree
#   cache/lite/                 audio + movies stripped
#   cache/manifest-full.json    per-file sha256 + sizes
#   cache/manifest-lite.json
#   cache/PROVENANCE.txt        what this cache was built from
set -euo pipefail

APK=""
OBB=""
SRC=""
OUT=""
VERIFY=0

die() { echo "FAIL: $*" >&2; exit 1; }
log() { echo "  $*"; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --apk)    APK="${2:-}"; shift 2 ;;
    --obb)    OBB="${2:-}"; shift 2 ;;
    --dir)    SRC="${2:-}"; shift 2 ;;
    --out)    OUT="${2:-}"; shift 2 ;;
    --verify) VERIFY=1; shift ;;
    -h|--help) sed -n '2,24p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done

[[ -n "$OUT" ]] || die "--out is required"
command -v unzip >/dev/null || die "unzip not found"

# --- verify mode: prove a cache matches its manifest, no rebuild --------------
if [[ $VERIFY -eq 1 ]]; then
  for variant in full lite; do
    man="$OUT/manifest-$variant.json"
    dir="$OUT/$variant"
    [[ -f "$man" ]] || die "missing $man"
    [[ -d "$dir" ]] || die "missing $dir"
    echo "== verifying $variant =="
    mismatches=0
    while IFS=$'\t' read -r path size sha; do
      f="$dir/$path"
      if [[ ! -f "$f" ]]; then echo "  MISSING  $path"; mismatches=$((mismatches+1)); continue; fi
      actual_size=$(stat -c%s "$f")
      actual_sha=$(sha256sum "$f" | cut -d' ' -f1)
      if [[ "$actual_size" != "$size" || "$actual_sha" != "$sha" ]]; then
        echo "  CORRUPT  $path"; mismatches=$((mismatches+1))
      fi
    done < <(python3 - "$man" <<'PY'
import json,sys
m=json.load(open(sys.argv[1]))
for f in m["files"]:
    print(f"{f['path']}\t{f['size']}\t{f['sha256']}")
PY
)
    [[ $mismatches -eq 0 ]] || die "$variant: $mismatches file(s) do not match the manifest"
    echo "  OK: $(python3 -c "import json,sys;m=json.load(open('$man'));print(len(m['files']),'files,',m['total_bytes'],'bytes')")"
  done
  echo "PASS: cache matches its manifests"
  exit 0
fi

# --- stage: get the asset tree onto disk, read-only ---------------------------
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT
ASSETS="$STAGE/assets"
mkdir -p "$ASSETS"

if [[ -n "$SRC" ]]; then
  [[ -d "$SRC" ]] || die "not a directory: $SRC"
  echo "== staging from directory: $SRC =="
  cp -a "$SRC/." "$ASSETS/"
elif [[ -n "$APK" ]]; then
  [[ -f "$APK" ]] || die "no such APK: $APK"
  echo "== staging assets/ from APK: $(basename "$APK") =="
  unzip -q "$APK" 'assets/*' -d "$STAGE/apk" || die "APK has no assets/ (wrong file?)"
  cp -a "$STAGE/apk/assets/." "$ASSETS/"
  if [[ -n "$OBB" ]]; then
    [[ -f "$OBB" ]] || die "no such OBB: $OBB"
    echo "== staging OBB: $(basename "$OBB") (this is the slow part) =="
    mkdir -p "$STAGE/obb"
    unzip -q "$OBB" -d "$STAGE/obb" || die "cannot extract OBB (try the .obb from the same install as the APK)"
    cp -a "$STAGE/obb/." "$ASSETS/"
  else
    echo "  NOTE: no --obb given; cache will be incomplete (no audio/models)"
  fi
else
  die "give one of --apk/--obb or --dir (see --help)"
fi

file_count=$(find "$ASSETS" -type f | wc -l)
total=$(du -sb "$ASSETS" | cut -f1)
[[ "$file_count" -gt 0 ]] || die "staged tree is empty — wrong input?"
echo "  staged: $file_count files, $total bytes"

# --- full --------------------------------------------------------------------
echo "== building full =="
rm -rf "$OUT/full"
mkdir -p "$OUT/full"
cp -a "$ASSETS/." "$OUT/full/"

# --- lite: drop the two directories that dominate the size -------------------
echo "== building lite (no audio/, no movies/) =="
rm -rf "$OUT/lite"
mkdir -p "$OUT/lite"
cp -a "$ASSETS/." "$OUT/lite/"
removed=0
for heavy in audio movies; do
  # match at any depth: mobile layouts nest them differently per version
  while IFS= read -r d; do
    [[ -d "$d" ]] || continue
    log "removing ${d#"$OUT/lite/"}"
    rm -rf "$d"
    removed=$((removed+1))
  done < <(find "$OUT/lite" -type d -name "$heavy")
done
[[ $removed -gt 0 ]] || echo "  WARNING: no audio/ or movies/ found — lite equals full"

# --- manifests --------------------------------------------------------------
emit_manifest() {
  local variant="$1" root="$OUT/$1" man="$OUT/manifest-$1.json"
  python3 - "$root" "$man" "$variant" "$APK" "$OBB" <<'PY'
import hashlib, json, os, sys, time
root, man, variant, apk, obb = sys.argv[1:6]
files = []
total = 0
for dirpath, dirnames, filenames in os.walk(root):
    dirnames.sort()
    for name in sorted(filenames):
        p = os.path.join(dirpath, name)
        rel = os.path.relpath(p, root)
        h = hashlib.sha256()
        size = 0
        with open(p, "rb") as fh:
            for chunk in iter(lambda: fh.read(1 << 20), b""):
                h.update(chunk)
                size += len(chunk)
        files.append({"path": rel, "size": size, "sha256": h.hexdigest()})
        total += size
doc = {
    "variant": variant,
    "generated_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
    "target_path": "/storage/emulated/0/GTA/",
    "source": {"apk": os.path.basename(apk) if apk else None,
               "obb": os.path.basename(obb) if obb else None},
    "file_count": len(files),
    "total_bytes": total,
    "files": files,
}
with open(man, "w") as fh:
    json.dump(doc, fh, indent=1, sort_keys=True)
print(f"  {variant}: {len(files)} files, {total} bytes, {total/1048576:.1f} MiB -> {man}")
PY
}
echo "== writing manifests =="
emit_manifest full
emit_manifest lite

cat > "$OUT/PROVENANCE.txt" <<EOF
SA-MP Android game cache — built $(date -u +%Y-%m-%dT%H:%M:%SZ)

Source of the assets (you must own these):
  apk: ${APK:-<none>}
  obb: ${OBB:-<none>}
  dir: ${SRC:-<none>}

These files are Rockstar Games' copyrighted GTA San Andreas data. They were
NOT downloaded by this repo and must NOT be uploaded to GitHub, to a public
CDN, or shipped inside an APK/Release. Keep this cache on the device it was
built for, or distribute your own client and let each player build theirs.

Target path on device: /storage/emulated/0/GTA/
Verify later with:     bin/build-cache.sh --out $(basename "$OUT") --verify
EOF

echo
echo "PASS"
du -sh "$OUT/full" "$OUT/lite" | sed 's/^/  /'
echo "  full : $OUT/full  ($OUT/manifest-full.json)"
echo "  lite : $OUT/lite  ($OUT/manifest-lite.json)"
echo "  push to device: adb push $OUT/full/. /storage/emulated/0/GTA/"
