#!/usr/bin/env python3
"""Merge the 50 approved Nov 19 photo batch into the 217-WebP Android catalog.

Input assets are versioned under extra_photos; no external network is used.
Enforce the complete 50-file exact manifest and stable dictionary word IDs.
"""
from __future__ import annotations
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets"
EXTRAS = ASSETS / "extra_photos"
EXPECTED = set("""0210_father 0371_money 0255_night 0170_home 0104_work 0091_day 0078_people 0076_way 0121_man 0066_time 0153_well 0219_thought 0133_mean 0771_god 0269_stop 0115_thing 0126_help 0131_right 0524_love 0238_life 4473_dad 0185_house 0294_being 0174_kind 0224_left 0425_course 0189_mother 0106_place 0118_name 0489_fine 0360_morning 0223_saw 0523_mind 0560_job 0253_car 3915_friend 0599_son 0609_care 0142_show 0197_world 0555_brother 0227_while 0890_wife 0818_guess 0274_idea 0220_head 0613_baby 0762_bit 0312_room 0296_family""".split())
OUT = ROOT / "results/release-50-import.json"

def main():
    errors = []
    actual = {p.stem.removeprefix("word_") for p in EXTRAS.glob("*.webp")}
    if actual != EXPECTED:
        errors += ["MISSING: " + v for v in sorted(EXPECTED - actual)]
        errors += ["UNEXPECTED: " + v for v in sorted(actual - EXPECTED)]
    if len(actual) != 50:
        errors.append("expected 50 latest WebP photos, got " + str(len(actual)))
    unexpected_ext = [str(p.name) for p in EXTRAS.iterdir() if p.is_file() and p.suffix.lower() != ".webp"]
    if unexpected_ext:
        errors.append("non-WebP files: " + ", ".join(unexpected_ext))
    words = json.loads((ASSETS / "words.json").read_text(encoding="utf-8"))
    by_id = {int(w["id"]): w for w in words}
    mapping_path = ASSETS / "visual_vocabulary.json"
    doc = json.loads(mapping_path.read_text(encoding="utf-8"))
    mapping = {int(x["wordId"]): x for x in doc["images"]}
    original_count = len(mapping)
    replacements = []
    added = []
    for stem in sorted(EXPECTED):
        match = re.fullmatch(r"(\d{4})_([a-z0-9_]+)", stem)
        if not match:
            errors.append("invalid asset naming: " + stem)
            continue
        word_id, lemma = int(match.group(1)), match.group(2)
        word = by_id.get(word_id)
        if word is None:
            errors.append("unknown dictionary word ID " + str(word_id))
            continue
        actual_en = word["en"].lower().strip().replace(" ", "_").replace("-", "_")
        if actual_en != lemma:
            errors.append(f"lemma mismatch at ID {word_id}: expected {lemma}, got {actual_en}")
            continue
        asset = "extra_photos/word_" + stem + ".webp"
        if not (ASSETS / asset).is_file():
            errors.append("missing file " + asset)
            continue
        if word_id in mapping:
            replacements.append(word_id)
        else:
            added.append(word_id)
        mapping[word_id] = {
            "wordId": word_id,
            "asset": asset,
            "altEn": word["en"],
            "altRu": word["ru"],
        }
    if not errors:
        doc["images"] = list(mapping.values())
        mapping_path.write_text(json.dumps(doc, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    report = {
        "status": "PASS" if not errors else "BLOCKED",
        "expected_extra_photos": 50, "found_extra_photos": len(actual),
        "mapped_before": original_count, "mapped_after": len(mapping),
        "new_mappings": len(added), "replacements": replacements,
        "errors": errors,
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 1 if errors else 0

if __name__ == "__main__":
    sys.exit(main())
