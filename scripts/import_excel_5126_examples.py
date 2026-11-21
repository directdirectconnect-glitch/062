#!/usr/bin/env python3
"""Restore approved Excel content: 5126 entries, 15378 translated examples.

Source is 26 versioned parts exported from EnglishWords-5126-priority.xlsx.
No network calls, silent translation, or fabricated dictionary IDs.
"""
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets"
SOURCE = ROOT / "release-media/excel-5126-examples"
REPORT = ROOT / "results/excel-5126-content-audit.json"


def run():
    files = sorted(SOURCE.glob("part-*.json"))
    if len(files) != 26:
        raise ValueError(f"Expected 26 source parts, got {len(files)}")
    dictionary = json.loads((ASSETS / "words.json").read_text(encoding="utf-8"))
    by_id = {row["id"]: row["en"] for row in dictionary}
    if len(dictionary) != 5126 or len(by_id) != 5126:
        raise ValueError("Dictionary must have 5126 unique word IDs")
    groups = {}
    lexical = {}
    seen = set()
    total_examples = total_collocations = total_phrasal = 0
    for part, path in enumerate(files):
        data = json.loads(path.read_text(encoding="utf-8"))
        if data.get("part") != part:
            raise ValueError(f"Wrong source part identifier in {path.name}")
        for row in data["records"]:
            wid = row["id"]
            if wid in seen or wid not in by_id:
                raise ValueError(f"Duplicate or unknown word ID {wid}")
            seen.add(wid)
            canonical = by_id[wid]
            excel_name = row["en"]
            if canonical.casefold().replace("-", "") != excel_name.casefold().replace("-", ""):
                raise ValueError(f"Excel/dictionary mismatch at ID {wid}: {excel_name} vs {canonical}")
            examples = row["examples"]
            if len(examples) != 3 or any(
                not isinstance(ex.get("en"), str) or not ex["en"].strip()
                or not isinstance(ex.get("ru"), str) or not ex["ru"].strip()
                for ex in examples
            ):
                raise ValueError(f"Missing EN/RU example at word ID {wid}")
            if canonical in groups:
                raise ValueError(f"Repeated lemma: {canonical}")
            groups[canonical] = examples
            total_examples += len(examples)
            extensions = {}
            for field, count_name in (("collocations", "collocations"),
                                      ("phrasal", "phrasal")):
                lines = [x.strip() for x in row.get(field, "").splitlines() if x.strip()]
                pairs = []
                for line in lines:
                    if " — " not in line:
                        raise ValueError(f"Missing translated phrase separator at {wid}: {line}")
                    en, ru = line.split(" — ", 1)
                    if not en.strip() or not ru.strip():
                        raise ValueError(f"Blank phrase translation at ID {wid}")
                    pairs.append({"en": en.strip(), "ru": ru.strip()})
                extensions[field] = pairs
                if count_name == "collocations":
                    total_collocations += len(pairs)
                else:
                    total_phrasal += len(pairs)
            if extensions["collocations"] or extensions["phrasal"]:
                lexical[str(wid)] = extensions
    if len(seen) != 5126 or total_examples != 15378:
        raise ValueError(f"Incomplete Excel import: {len(seen)} IDs / {total_examples} translated examples")
    if total_collocations != 17998 or total_phrasal != 1706:
        raise ValueError(f"Unexpected lexical totals: {total_collocations} collocations / {total_phrasal} phrasal")
    (ASSETS / "examples.json").write_text(
        json.dumps({"examples": groups}, ensure_ascii=False, separators=(",", ":")) + "\n",
        encoding="utf-8"
    )
    (ASSETS / "lexical_extensions.json").write_text(
        json.dumps({"schemaVersion": 1, "byWordId": lexical}, ensure_ascii=False, separators=(",", ":")) + "\n",
        encoding="utf-8"
    )
    report = {"status": "PASS", "word_ids": len(seen), "translated_examples": total_examples,
              "translated_collocations": total_collocations,
              "translated_phrasal_verbs": total_phrasal,
              "excel_spelling_alias": {"word_id": 1286, "excel": "check-in", "app": "checkin"}}
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    try:
        sys.exit(run())
    except Exception as exc:
        print("Excel import FAIL: " + str(exc), file=sys.stderr)
        sys.exit(1)
