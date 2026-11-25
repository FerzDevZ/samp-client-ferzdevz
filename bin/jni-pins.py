#!/usr/bin/env python3
"""Match the natives declared in Java against the symbols a native library exports.

A `native` method with no matching `Java_...` symbol compiles, installs, and then throws
UnsatisfiedLinkError the first time the engine touches it, which is exactly the bug you
cannot see until a phone reaches that code path. This compares both directions:

  * declared but not exported  -> FAIL (it will crash when called)
  * exported but not declared  -> INFO (dead code in the library, not a bug)

Usage:
  jni-pins.py --library libGame.so --source client/app/src/main/java/com/rockstargames/oswrapper

Exit codes: 0 no missing implementation, 1 a declared native has no symbol, 2 bad usage.
"""

import argparse
import os
import re
import subprocess
import sys

NATIVE = re.compile(r"\bnative\b[^;()]*?\b(\w+)\s*\(([^)]*)\)\s*;")
PACKAGE = re.compile(r"^\s*package\s+([\w.]+)\s*;", re.M)


def exported_symbols(library):
    """Base symbols only: an overload adds __<signature>, the base name is what we match."""
    try:
        output = subprocess.run(
            ["nm", "-D", "--defined-only", library],
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
        ).stdout.decode("utf-8", "replace")
    except (OSError, subprocess.CalledProcessError) as error:
        sys.stderr.write("failed to read symbols from %s: %s\n" % (library, error))
        sys.exit(2)
    names = set()
    for line in output.splitlines():
        fields = line.split()
        if not fields:
            continue
        symbol = fields[-1]
        if symbol.startswith("Java_"):
            names.add(symbol.split("__")[0])
    return names


def declared_natives(root):
    """Yield (file, java name, expected symbol) for every native declaration found."""
    found = []
    for directory, _, files in os.walk(root):
        for name in files:
            if not name.endswith(".java"):
                continue
            path = os.path.join(directory, name)
            with open(path, encoding="utf-8", errors="replace") as handle:
                text = handle.read()
            if " native " not in text:
                continue
            package = PACKAGE.search(text)
            if not package:
                continue
            simple = name[:-5]
            prefix = "Java_" + package.group(1).replace(".", "_") + "_" + simple + "_"
            for match in NATIVE.finditer(text):
                found.append((path, match.group(1), prefix + match.group(1)))
    return found


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--library", required=True)
    parser.add_argument("--source", required=True)
    args = parser.parse_args(argv)

    if not os.path.isfile(args.library):
        sys.stderr.write("no such library: %s\n" % args.library)
        return 2
    if not os.path.isdir(args.source):
        sys.stderr.write("no such source directory: %s\n" % args.source)
        return 2

    exported = exported_symbols(args.library)
    claimed = set()
    missing = []
    for path, java_name, symbol in declared_natives(args.source):
        claimed.add(symbol)
        if symbol not in exported:
            missing.append((os.path.relpath(path), java_name, symbol))

    unused = sorted(symbol for symbol in exported if symbol not in claimed)

    if unused:
        print("  %d exported symbol(s) with no Java declaration (dead code, not fatal):" % len(unused))
        for symbol in unused:
            print("    %s" % symbol.split("Java_", 1)[1])
    if missing:
        print("  %d declared native(s) with no implementation:" % len(missing))
        for path, java_name, symbol in missing:
            print("    %s: %s calls %s" % (path, java_name, symbol))
        return 1

    print("  %d declared natives all resolve, %d exported symbols unclaimed"
          % (len(claimed), len(unused)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
