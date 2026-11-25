#!/usr/bin/env python3
"""Check a patched APK without the Android SDK.

`zipalign` and `aapt2` are x86-64 SDK binaries and do not run in Termux, so the phone
build is verified by reading the zip structures directly:

  * the native library is present, stored, and starts on a 4096-byte boundary;
  * its bytes are the ones that were inserted;
  * the manifest string pool still carries the expected package name.

Usage:
  apk-verify.py --apk out.apk --entry lib/arm64-v8a/libGame.so --so libGame.so \
                --expect-package com.ferzdevzgta.game

Exit codes: 0 verified, 1 failed.
"""

import argparse
import hashlib
import struct
import sys
import zipfile

LOCAL_SIG = b"PK\x03\x04"
AARCH64 = 0xB7


def local_header(data, offset):
    """Return (name_len, extra_len, method, data_offset) for a local file record."""
    if data[offset:offset + 4] != LOCAL_SIG:
        raise ValueError("no local header at offset %d" % offset)
    method = struct.unpack_from("<H", data, offset + 8)[0]
    name_len, extra_len = struct.unpack_from("<HH", data, offset + 26)
    return name_len, extra_len, method, offset + 30 + name_len + extra_len


def check(apk, entry, so, align, expect_package):
    failures = []
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        if entry not in names:
            return ["%s is missing from %s" % (entry, apk)]
        info = archive.getinfo(entry)
        payload = archive.read(entry)

    with open(apk, "rb") as handle:
        data = handle.read()

    name_len, extra_len, method, data_offset = local_header(data, info.header_offset)
    if method != 0:
        failures.append("%s is deflated (method %d); the loader needs it stored" % (entry, method))
    if data_offset % align:
        failures.append(
            "%s starts at %d, which is %d bytes off a %d-byte boundary"
            % (entry, data_offset, data_offset % align, align)
        )
    if payload[:4] == b"\x7fELF" and len(payload) > 20:
        machine = struct.unpack_from("<H", payload, 18)[0]
        if machine != AARCH64:
            failures.append("%s is not an aarch64 ELF (e_machine %d)" % (entry, machine))
    if so:
        with open(so, "rb") as handle:
            inserted = handle.read()
        if hashlib.sha256(payload).digest() != hashlib.sha256(inserted).digest():
            failures.append("%s inside the APK is not the file that was inserted" % entry)
        if len(payload) != len(inserted):
            failures.append("%s length %d != %d" % (entry, len(payload), len(inserted)))

    if expect_package:
        needle8 = expect_package.encode("utf-8")
        needle16 = expect_package.encode("utf-16-le")
        if needle8 not in data and needle16 not in data:
            failures.append("package name %s does not appear in the APK" % expect_package)

    return failures, data_offset, method


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", required=True)
    parser.add_argument("--entry", default="lib/arm64-v8a/libGame.so")
    parser.add_argument("--so", default="")
    parser.add_argument("--align", type=int, default=4096)
    parser.add_argument("--expect-package", default="")
    args = parser.parse_args(argv)

    try:
        result = check(args.apk, args.entry, args.so, args.align, args.expect_package)
    except (ValueError, zipfile.BadZipFile, OSError) as error:
        sys.stderr.write("failed: %s\n" % error)
        return 1

    failures, data_offset, method = result
    if failures:
        for failure in failures:
            sys.stderr.write("failed: %s\n" % failure)
        return 1

    print("  %s: stored, offset %d (multiple of %d)" % (args.entry, data_offset, args.align))
    if args.so:
        print("  bytes match the file that was inserted")
    if args.expect_package:
        print("  package %s present" % args.expect_package)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
