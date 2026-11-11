#!/usr/bin/env python3
"""Mass QA triage for real app vocabulary; flags are NOT verified mistakes.

Use this report to prioritize editorial verification. Exit 1 only on
unambiguous structural corruption, not on legitimate lexical variants.
"""
import argparse
import json
import re
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT = ROOT / "app/src/main/assets/words.json"

def audit(path: Path) -> dict:
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, list):
        raise ValueError("Expected list of words")

    buckets = {
        "unbalanced_parentheses_in_translation": [],
        "non_cyrillic_pronunciation": [],
        "mixed_script_pronunciation": [],
        "multiword_expressions": [],
        "potential_inflected_headwords": [],
        "suspiciously_short_translation": [],
        "unusual_translation_characters": [],
    }
    for w in data:
        ident = w.get("id")
        en, ru, pron = (str(w.get(k, "")) for k in ("en", "ru", "pron"))
        if ru.count("(") != ru.count(")"):
            buckets["unbalanced_parentheses_in_translation"].append(ident)
        if not re.search(r"[а-яё]", pron, re.I):
            buckets["non_cyrillic_pronunciation"].append(ident)
        if re.search(r"[a-z]", pron, re.I) and re.search(r"[а-яё]", pron, re.I):
            buckets["mixed_script_pronunciation"].append(ident)
        if re.search(r"\s", en.strip()):
            buckets["multiword_expressions"].append(ident)
        if re.search(r"(?:ing|ed|ies|s)$", en, re.I) and len(en) > 5:
            buckets["potential_inflected_headwords"].append(ident)
        if len(ru.strip()) < 2:
            buckets["suspiciously_short_translation"].append(ident)
        if re.search(r"[A-Za-z\ufffd]", ru):
            buckets["unusual_translation_characters"].append(ident)
    total = len(data)
    singles = total - len(buckets["multiword_expressions"])
    checks = {
        "row_count": total,
        "single_token_spellings": singles,
        "unique_headwords": len(set(w["en"].strip().casefold() for w in data)),
        "sequential_ids": all(w.get("id") == i for i, w in enumerate(data, 1)),
        "all_mandatory_fields_nonempty": all(
            isinstance(w.get(k), str) and bool(w[k].strip()) for w in data for k in ("en", "ru", "pron")
        ),
    }
    return {
        "source": str(path),
        "scope": "Automated flagging for subsequent bilingual editorial review",
        "checks": checks,
        "flag_counts": {key: len(vals) for key, vals in buckets.items()},
        "flagged_entry_ids": buckets,
        "blocking_issues": [
            *([] if checks["sequential_ids"] else ["Nonsequential IDs"]),
            *([] if checks["all_mandatory_fields_nonempty"] else ["Blank mandatory fields"]),
            *([] if checks["unique_headwords"] == total else ["Duplicate English spellings"]),
            *([] if singles >= 5000 else ["Fewer than 5,000 standalone English spellings"]),
            *([] if not buckets["unbalanced_parentheses_in_translation"] else ["Unbalanced parentheses in translations"]),
        ],
        "limitations": (
            "Flags are candidates for manual review, not proof of lexical errors. "
            "Pronunciation format differs between original batches; translated meanings, "
            "IPA correctness, licensing and lemma frequency require further human review."
        ),
    }

def main():
    p = argparse.ArgumentParser()
    p.add_argument("--vocabulary", type=Path, default=DEFAULT)
    p.add_argument("--output", type=Path, default=ROOT / "build/vocabulary-quality-triage.json")
    args = p.parse_args()
    result = audit(args.vocabulary)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"checks": result["checks"], "flag_counts": result["flag_counts"],
                      "blocking_issues": result["blocking_issues"]}, ensure_ascii=False, indent=2))
    if result["blocking_issues"]:
        raise SystemExit(1)

if __name__ == "__main__":
    main()
