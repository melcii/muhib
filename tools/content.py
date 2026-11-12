#!/usr/bin/env python3
"""
Moves Muhib's reminders between the app's content file and a spreadsheet.

  python3 tools/content.py export reminders.csv   # content.json -> CSV (to start or refresh the sheet)
  python3 tools/content.py import reminders.csv   # CSV (downloaded from the sheet) -> content.json

The sheet has one row per reminder. Shared columns: id, type, link, arabic. Then, for each
language, three columns named with its code: en_text, en_source, en_more, tr_text, ...
Add a language by adding its three columns (for example ar_text). Other columns, such as
notes, are ignored. English is
required; a missing translation falls back to English in the app.
"""
import csv
import json
import os
import re
import sys
from urllib.parse import urlparse

HERE = os.path.dirname(os.path.abspath(__file__))
CONTENT = os.path.join(HERE, "..", "app", "src", "main", "assets", "content.json")
SHARED = ["id", "type", "link", "arabic"]
FIELDS = ["text", "source", "more"]
TYPES = {"ayah", "hadith", "sunnah", "chapter", "dua"}
# Links to these are routine; any other site gets a note so you can double-check it.
KNOWN_HOSTS = {"quran.com", "sunnah.com", "www.quran.com", "www.sunnah.com"}


def export(path):
    data = json.load(open(CONTENT, encoding="utf-8"))
    langs = ["en"] + sorted({k for item in data["items"] for k, v in item.items() if isinstance(v, dict)})
    header = SHARED + [f"{lang}_{f}" for lang in langs for f in FIELDS]
    with open(path, "w", newline="", encoding="utf-8") as out:
        w = csv.writer(out)
        w.writerow(header)
        for item in data["items"]:
            row = {k: item.get(k, "") for k in SHARED}
            for f in FIELDS:
                row[f"en_{f}"] = item.get(f, "")
            for lang in langs[1:]:
                for f in FIELDS:
                    row[f"{lang}_{f}"] = (item.get(lang) or {}).get(f, "")
            w.writerow([row[h] for h in header])
    print(f"Wrote {len(data['items'])} reminders in {len(langs)} languages ({', '.join(langs)}) to {path}")


def import_(path):
    rows = list(csv.DictReader(open(path, newline="", encoding="utf-8-sig")))
    if not rows:
        sys.exit("The sheet has no rows.")
    cols = rows[0].keys()
    missing = [c for c in SHARED + ["en_text", "en_source"] if c not in cols]
    if missing:
        sys.exit(f"Missing columns: {', '.join(missing)}")
    langs = sorted({c.split("_", 1)[0] for c in cols if c.endswith("_text")} - {"en"})
    problems, warnings, items, seen = [], [], [], set()
    for n, r in enumerate(rows, start=2):  # row 1 is the header
        r = {k: (v or "").strip() for k, v in r.items()}
        if not any(r.values()):
            continue  # blank row
        where = f"Row {n} ({r['id'] or 'no id'})"
        if not r["id"]:
            problems.append(f"{where}: id is empty.")
        elif r["id"] in seen:
            problems.append(f"{where}: id is used twice.")
        seen.add(r["id"])
        if r["type"] not in TYPES:
            problems.append(f"{where}: type must be one of {', '.join(sorted(TYPES))}.")
        if r["link"] and not re.fullmatch(r"https://[A-Za-z0-9.-]+\.[A-Za-z]{2,}(/\S*)?", r["link"]):
            problems.append(f"{where}: link must be a full https:// address.")
        elif r["link"] and urlparse(r["link"]).hostname not in KNOWN_HOSTS:
            warnings.append(f"{where}: link goes to {urlparse(r['link']).hostname}; check it's meant to.")
        for need in ("en_text", "en_source"):
            if not r[need]:
                problems.append(f"{where}: {need} is empty.")
        item = {"id": r["id"], "type": r["type"]}
        for f in FIELDS:
            if r.get(f"en_{f}"):
                item[f] = r[f"en_{f}"]
        if r["arabic"]:
            item["arabic"] = r["arabic"]
        if r["link"]:
            item["link"] = r["link"]
        for lang in langs:
            local = {f: r[f"{lang}_{f}"] for f in FIELDS if r.get(f"{lang}_{f}")}
            if local:
                item[lang] = local
            if not r.get(f"{lang}_text"):
                warnings.append(f"{where}: no {lang} text yet; English will show.")
        items.append(item)
    for w in warnings:
        print("Note:", w)
    if problems:
        print("\n".join(problems))
        sys.exit(f"Not written: fix the {len(problems)} problem(s) above in the sheet and try again.")
    data = json.load(open(CONTENT, encoding="utf-8"))
    data["items"] = items
    with open(CONTENT, "w", encoding="utf-8") as out:
        json.dump(data, out, ensure_ascii=False, indent=2)
        out.write("\n")
    print(f"Wrote {len(items)} reminders ({', '.join(['en'] + langs)}) to app/src/main/assets/content.json")


if __name__ == "__main__":
    if len(sys.argv) != 3 or sys.argv[1] not in ("export", "import"):
        sys.exit(__doc__)
    (export if sys.argv[1] == "export" else import_)(sys.argv[2])
