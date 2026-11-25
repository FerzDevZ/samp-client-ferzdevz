#!/usr/bin/env bash
# Serve the newest built APK over HTTP so a phone on the same Tailscale tailnet can install it.
#
#   bin/serve-apk.sh                 # newest APK, port 8000
#   bin/serve-apk.sh --port 8080 --apk path/to/app.apk
#   bin/serve-apk.sh --dir-only      # stage the files and print the URLs, do not listen
#
# What it does NOT do: it does not build, sign or patch anything. Point it at an APK you
# already have, and remember that an APK containing libGame.so is for your own devices only.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORT=8000
APK=""
DIR_ONLY=0

while [ $# -gt 0 ]; do
    case "$1" in
        --port) PORT="${2:-}"; shift 2 ;;
        --apk)  APK="${2:-}"; shift 2 ;;
        --dir-only) DIR_ONLY=1; shift ;;
        -h|--help)
            sed -n '2,12p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
            exit 0 ;;
        *) echo "unknown argument: $1" >&2; exit 2 ;;
    esac
done

case "$PORT" in
    ''|*[!0-9]*) echo "FAIL: --port needs a number" >&2; exit 2 ;;
esac
if [ "$PORT" -lt 1 ] || [ "$PORT" -gt 65535 ]; then
    echo "FAIL: --port out of range: $PORT" >&2
    exit 2
fi

# Newest APK across the build output and dist/, unless one was named.
if [ -z "$APK" ]; then
    APK="$(find "$ROOT/client/app/build/outputs/apk" "$ROOT/dist" -name '*.apk' -type f -printf '%T@ %p\n' 2>/dev/null \
        | sort -rn | head -1 | cut -d' ' -f2-)"
fi
if [ -z "$APK" ] || [ ! -f "$APK" ]; then
    echo "FAIL: no APK found. Build one first: bin/android-build.sh debug" >&2
    exit 1
fi

STAGE="$HOME/samp-download"
mkdir -p "$STAGE"
NAME="$(basename "$APK")"
cp -f "$APK" "$STAGE/$NAME"
(cd "$STAGE" && sha256sum "$NAME" > "$NAME.sha256")

SIZE_BYTES="$(stat -c %s "$STAGE/$NAME")"
SIZE_HUMAN="$(awk -v b="$SIZE_BYTES" 'BEGIN { printf "%.1f MB", b / 1048576 }')"
SHA="$(cut -d' ' -f1 "$STAGE/$NAME.sha256")"

# Every address the phone could reach, Tailscale first because it works away from the house.
ADDRESSES=()
while read -r ip; do
    [ -n "$ip" ] && ADDRESSES+=("$ip")
done < <(tailscale ip 2>/dev/null)
if command -v hostname >/dev/null; then
    while read -r ip; do
        case "$ip" in
            ''|127.*|100.*) continue ;;
        esac
        ADDRESSES+=("$ip")
    done < <(hostname -I 2>/dev/null | tr ' ' '\n')
fi

printf 'staged  %s\n' "$STAGE/$NAME"
printf 'size    %s bytes (%s)\n' "$SIZE_BYTES" "$SIZE_HUMAN"
printf 'sha256  %s\n\n' "$SHA"
printf 'download from the phone:\n'
for ip in "${ADDRESSES[@]}"; do
    case "$ip" in
        *:*) printf '  http://[%s]:%s/%s\n' "$ip" "$PORT" "$NAME" ;;
        *)   printf '  http://%s:%s/%s\n' "$ip" "$PORT" "$NAME" ;;
    esac
done
printf '\nverify after download (Termux):\n'
printf '  sha256sum ~/storage/downloads/%s\n  # must print %s\n' "$NAME" "$SHA"

if [ "$DIR_ONLY" = "1" ]; then
    printf '\n--dir-only: nothing is listening. Files are in %s\n' "$STAGE"
    exit 0
fi

printf '\nserving %s on port %s, Ctrl+C to stop\n' "$STAGE" "$PORT"
exec python3 -m http.server "$PORT" --bind 0.0.0.0 --directory "$STAGE"
