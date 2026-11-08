#!/usr/bin/env python3
"""Turn a JSON list of row objects into a CSV file in the workspace."""
import csv
import json
import sys


def main(argv):
    if not argv or argv[0] in ("-h", "--help"):
        sys.stderr.write("usage: rows_to_csv.py <rows.json> [out.csv]\n")
        return 0 if argv and argv[0] in ("-h", "--help") else 2
    src = argv[0]
    dest = argv[1] if len(argv) > 1 else src.rsplit(".", 1)[0] + ".csv"
    with open(src, encoding="utf-8") as f:
        rows = json.load(f)
    if not isinstance(rows, list) or not rows:
        sys.stderr.write("no rows\n")
        return 3
    if not all(isinstance(r, dict) for r in rows):
        sys.stderr.write("rows must be objects\n")
        return 3
    fields = []
    for r in rows:
        for k in r:
            if k not in fields:
                fields.append(str(k))
    with open(dest, "w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=fields, extrasaction="ignore")
        w.writeheader()
        for r in rows:
            w.writerow({k: r.get(k, "") for k in fields})
    sys.stdout.write(dest + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
