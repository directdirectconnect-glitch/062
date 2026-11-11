#!/usr/bin/env python3
"""Validate photo-planning manifest against the ORIGINAL app dictionary.

This validator checks references and planning state, not image quality.
"""
import json
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VOCAB = ROOT / "app/src/main/assets/words.json"
MANIFEST = ROOT / "visual/visual_classification_draft.json"
TYPES = {"grammar_card", "manual_review", "action_scene", "concept_scene",
         "place_photo", "object_photo", "diagram", "person_photo"}


def main():
    words = json.loads(VOCAB.read_text(encoding="utf-8"))
    data = json.loads(MANIFEST.read_text(encoding="utf-8"))
    records = data["items"]
    errors = []
    if len(records) != len(words):
        errors.append("Count mismatch")
    if data["sourceWordCount"] != len(words):
        errors.append("Stale sourceWordCount")
    ids = [x["wordId"] for x in records]
    if ids != [w["id"] for w in words]:
        errors.append("Missing or reordered IDs")
    for index, (record, word) in enumerate(zip(records, words), 1):
        if record["en"] != word["en"] or record["ru"] != word["ru"]:
            errors.append(f"Entry {index}: stale English/Russian content")
        if record["suggestedVisualType"] not in TYPES:
            errors.append(f"Entry {index}: unknown visual type")
    status = Counter(x["reviewStatus"] for x in records)
    types = Counter(x["suggestedVisualType"] for x in records)
    attached = sum(bool(x.get("imagePath")) for x in records)
    print(json.dumps({"entries": len(records), "types": types, "statuses": status,
                      "attached_images_in_plan": attached, "errors": errors},
                     ensure_ascii=False, indent=2))
    if errors:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
