#!/usr/bin/env python3
"""Audit the bundled vocabulary without external packages or network access.

This is a structural gate, NOT an automated claim that translations,
pronunciation or frequency ranking are editorially verified.
"""
import argparse
import json
import re
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT = ROOT / "app/src/main/assets/words.json"

def audit(path: Path) -> dict:
    words = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(words, list):
        raise ValueError("Vocabulary must be a JSON list")
    issues = []
    ids, english = [], []
    for n, entry in enumerate(words, 1):
        if not isinstance(entry, dict):
            issues.append(f"row {n}: not an object")
            continue
        for field in ("id", "en", "ru", "pron"):
            if field not in entry:
                issues.append(f"row {n}: missing {field}")
        identifier = entry.get("id")
        if not isinstance(identifier, int) or isinstance(identifier, bool) or identifier <= 0:
            issues.append(f"row {n}: invalid ID {identifier!r}")
        else:
            ids.append(identifier)
        for field in ("en", "ru", "pron"):
            v = entry.get(field)
            if not isinstance(v, str) or not v.strip():
                issues.append(f"row {n}: blank {field}")
        token = entry.get("en")
        if isinstance(token, str) and token.strip():
            english.append(token.casefold().strip())
    for name, seq in (("id", ids), ("en", english)):
        repeats = [item for item, count in Counter(seq).items() if count > 1]
        if repeats:
            issues.append(f"duplicate {name}: {repeats[:20]}")
    if ids and ids != list(range(1, len(words) + 1)):
        issues.append("IDs are not consecutive and ordered from 1")
    return {
        "total_rows": len(words),
        "unique_english_spellings": len(set(english)),
        "unique_single_token_spellings": len({w for w in english if not re.search(r"\s", w)}),
        "multiword_records": sum(bool(re.search(r"\s", w)) for w in english),
        "bracketed_transcriptions": sum(isinstance(x, dict) and str(x.get("pron", "")).strip().startswith("[") for x in words),
        "target": 5000,
        "remaining": max(0, 5000 - len(words)),
        "issues": issues,
        "note": "This check cannot validate lexical frequency, lemmas, translations or pronunciation."
    }

def main():
    p = argparse.ArgumentParser()
    p.add_argument("--vocabulary", type=Path, default=DEFAULT)
    p.add_argument("--output", type=Path)
    p.add_argument("--require-5000-single-words", action="store_true", help="Fail unless there are at least 5000 distinct single-token English spellings")
    a = p.parse_args()
    result = audit(a.vocabulary)
    if a.require_5000_single_words and result["unique_single_token_spellings"] < 5000:
        result["issues"].append("Fewer than 5,000 unique single-token English spellings")
    report = json.dumps(result, ensure_ascii=False, indent=2)
    print(report)
    if a.output:
        a.output.parent.mkdir(parents=True, exist_ok=True)
        a.output.write_text(report + "\n", encoding="utf-8")
    if result["issues"]:
        raise SystemExit(1)

if __name__ == "__main__":
    main()
