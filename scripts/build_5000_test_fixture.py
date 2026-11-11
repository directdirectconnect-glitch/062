#!/usr/bin/env python3
"""Generate an isolated, exactly 5,000-entry load-test vocabulary.

Preserves existing dictionary entries and IDs. Missing words receive explicit
TEST-ONLY placeholders. Never use this fixture as real learning content.
"""
from __future__ import annotations
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "app/src/main/assets/words.json"
FREQUENCY = ROOT / "testdata/lexicore-5000.txt"
OUTPUT = ROOT / "build/test-fixtures/words_5000_LOAD_TEST_ONLY.json"
TARGET = 5000
PLACEHOLDER = "[ТЕСТ: перевод не проверен]"

def build(base_path: Path = BASE, frequency_path: Path = FREQUENCY):
    original = json.loads(base_path.read_text(encoding="utf-8"))
    if not isinstance(original, list):
        raise ValueError("Base dictionary must be a JSON array")
    if len(original) > TARGET:
        raise ValueError("Base dictionary already exceeds 5,000 entries")
    seen = set()
    merged = []
    for expected_id, entry in enumerate(original, 1):
        if entry.get("id") != expected_id:
            raise ValueError(f"Existing ID changed at index {expected_id}")
        word = entry.get("en", "").strip().casefold()
        if not word or word in seen:
            raise ValueError(f"Duplicate or empty English word: {word!r}")
        for field in ("pron", "ru"):
            if not isinstance(entry.get(field), str) or not entry[field].strip():
                raise ValueError(f"Missing {field} in existing entry {word}")
        seen.add(word)
        merged.append(dict(entry))
    candidate_words = [
        line.strip() for line in frequency_path.read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]
    if len(candidate_words) != 5000 or len(set(w.casefold() for w in candidate_words)) != 5000:
        raise ValueError("Frequency list must contain 5,000 distinct words")
    for word in candidate_words:
        if len(merged) == TARGET:
            break
        normalized = word.casefold()
        if normalized in seen:
            continue
        seen.add(normalized)
        merged.append({
            "id": len(merged) + 1,
            "en": word,
            "pron": "[TEST ONLY: no pronunciation]",
            "ru": PLACEHOLDER
        })
    if len(merged) < TARGET:
        raise ValueError(f"Only {len(merged)} entries after frequency list; need more candidates")
    if len({entry["en"].strip().casefold() for entry in merged}) != TARGET:
        raise ValueError("Duplicate English words in fixture")
    return merged, len(original)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", type=Path, default=BASE)
    parser.add_argument("--frequency", type=Path, default=FREQUENCY)
    parser.add_argument("--output", type=Path, default=OUTPUT)
    args = parser.parse_args()
    data, original_count = build(args.base, args.frequency)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Load-test fixture: {len(data)} entries, {original_count} preserved, "
          f"{len(data) - original_count} TEST-ONLY placeholders")
    print(f"Output: {args.output}")
    print("Never distribute this as a learner-ready dictionary.")

if __name__ == "__main__":
    main()
