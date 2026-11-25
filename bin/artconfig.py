#!/usr/bin/env python3
"""Read a SA-MP 0.3.DL `models/artconfig.txt` and prove it is sane.

A 0.3.DL server points its client at model files through artconfig.txt, and a broken entry does not
fail at startup: the server accepts it and the model silently never appears for players. This reads
the file the way the server does and refuses the mistakes that cost people an evening:

  * `AddCharModel` ids outside 20000..30000, `AddSimpleModel` ids outside -30000..-1000
  * a base id that is not a stock skin / object
  * the same new id registered twice (the second registration loses)
  * a name that is missing from the models folder, wrong extension, or points outside it
  * a line without the trailing semicolon the parser needs

Usage:
  bin/artconfig.py models/artconfig.txt --models models --check
  bin/artconfig.py models/artconfig.txt --models models --manifest models.json

Exit codes: 0 clean, 1 at least one entry is wrong, 2 unusable input.
"""

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

CHAR_ID_MIN, CHAR_ID_MAX = 20000, 30000
OBJ_ID_MIN, OBJ_ID_MAX = -30000, -1000
SKIN_BASE_MIN, SKIN_BASE_MAX = 0, 311

CALL = re.compile(r"^(AddCharModel|AddSimpleModel)\s*\((?P<args>.*)\)\s*;\s*$")
QUOTED = re.compile(r'"([^"]*)"')


class Problem(Exception):
    pass


def numbers(text):
    return [int(match) for match in re.findall(r"-?\d+", text)]


def parse_line(line, lineno):
    match = CALL.match(line)
    if not match:
        raise Problem("line %d: not a `AddCharModel(...);` or `AddSimpleModel(...);` call" % lineno)
    call = match.group(1)
    args = match.group("args")
    names = QUOTED.findall(args)
    if len(names) != 2:
        raise Problem("line %d: expected a quoted .dff name and a quoted .txd name" % lineno)

    # Remove the quoted names before reading numbers, so a name containing a comma cannot be
    # mistaken for an argument.
    bare = QUOTED.sub("", args)
    nums = numbers(bare)
    # AddCharModel(baseid, newid, dff, txd) has two numbers, AddSimpleModel(vworld, baseid, newid,
    # dff, txd) three. The names are already removed above.
    expected = 2 if call == "AddCharModel" else 3
    if len(nums) != expected:
        raise Problem("line %d: %s takes %d numbers, found %d" % (lineno, call, expected, len(nums)))

    entry = {"type": call, "dff": names[0], "txd": names[1], "line": lineno}
    if call == "AddCharModel":
        entry["baseid"], entry["newid"] = nums[0], nums[1]
        entry["virtual_world"] = -1
    else:
        entry["virtual_world"], entry["baseid"], entry["newid"] = nums[0], nums[1], nums[2]
    return entry


def validate(entry, models_dir):
    kind = entry["type"]
    newid = entry["newid"]
    if kind == "AddCharModel":
        if not CHAR_ID_MIN <= newid <= CHAR_ID_MAX:
            raise Problem("line %d: skin id %d is outside %d..%d"
                          % (entry["line"], newid, CHAR_ID_MIN, CHAR_ID_MAX))
        if not SKIN_BASE_MIN <= entry["baseid"] <= SKIN_BASE_MAX:
            raise Problem("line %d: skin base %d is outside %d..%d (only stock skins inherit)"
                          % (entry["line"], entry["baseid"], SKIN_BASE_MIN, SKIN_BASE_MAX))
    else:
        if not OBJ_ID_MIN <= newid <= OBJ_ID_MAX:
            raise Problem("line %d: object id %d is outside %d..%d"
                          % (entry["line"], newid, OBJ_ID_MIN, OBJ_ID_MAX))
        if entry["baseid"] < 0:
            raise Problem("line %d: object base %d is negative" % (entry["line"], entry["baseid"]))
        if entry["virtual_world"] < -1:
            raise Problem("line %d: virtual world %d is below -1"
                          % (entry["line"], entry["virtual_world"]))

    for key, suffix in (("dff", ".dff"), ("txd", ".txd")):
        name = entry[key]
        if not name.lower().endswith(suffix):
            raise Problem("line %d: %s is not a %s file" % (entry["line"], name, suffix))
        if name.startswith("/") or "\\" in name or ".." in name:
            raise Problem("line %d: %s points outside the models folder" % (entry["line"], name))
        if models_dir is not None:
            path = models_dir / name
            if not path.is_file():
                raise Problem("line %d: %s is listed but not in %s" % (entry["line"], name, models_dir))
            entry[key + "_bytes"] = path.stat().st_size
            entry[key + "_sha256"] = sha256(path)


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def read(path, models_dir):
    entries = []
    seen = {}
    problems = []
    for lineno, raw in enumerate(path.read_text(encoding="utf-8", errors="replace").splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#") or line.startswith("//"):
            continue
        try:
            entry = parse_line(line, lineno)
            validate(entry, models_dir)
        except Problem as problem:
            problems.append(str(problem))
            continue
        except ValueError as error:
            problems.append("line %d: %s" % (lineno, error))
            continue
        if entry["newid"] in seen:
            problems.append("line %d: id %d is already used on line %d"
                            % (lineno, entry["newid"], seen[entry["newid"]]))
            continue
        seen[entry["newid"]] = lineno
        entries.append(entry)
    return entries, problems


def main(argv):
    parser = argparse.ArgumentParser(add_help=True)
    parser.add_argument("config")
    parser.add_argument("--models", default="", help="folder holding the .dff/.txd files")
    parser.add_argument("--check", action="store_true", help="validate only")
    parser.add_argument("--manifest", default="", help="write a JSON inventory here")
    args = parser.parse_args(argv[1:])

    config = Path(args.config)
    if not config.is_file():
        print("FAIL: no artconfig at %s" % config, file=sys.stderr)
        return 2
    models_dir = Path(args.models) if args.models else None
    if models_dir is not None and not models_dir.is_dir():
        print("FAIL: --models %s is not a directory" % models_dir, file=sys.stderr)
        return 2

    entries, problems = read(config, models_dir)
    skins = [e for e in entries if e["type"] == "AddCharModel"]
    objects = [e for e in entries if e["type"] == "AddSimpleModel"]
    print("artconfig: %d entries (%d skins, %d objects)" % (len(entries), len(skins), len(objects)))
    for problem in problems:
        print("FAIL " + problem)

    if args.manifest and not problems:
        # Two entries can name the same texture, so the total is per file, not per entry.
        unique = {}
        for entry in entries:
            for key in ("dff", "txd"):
                if key + "_bytes" in entry:
                    unique[entry[key]] = entry[key + "_bytes"]
        total = sum(unique.values())
        payload = {
            "source": str(config),
            "count": len(entries),
            "unique_files": len(unique),
            "total_bytes": total,
            "models": entries,
        }
        Path(args.manifest).write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n",
                                       encoding="utf-8")
        print("manifest: %s (%d bytes)" % (args.manifest, total))

    if problems:
        return 1
    if args.check and models_dir is None:
        print("note: files were not checked, pass --models to check them")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
