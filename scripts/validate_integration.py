#!/usr/bin/env python3
"""Static integration checks: bundled offline content and Android activity wiring.

No Android device or network dependency. Run from any working directory:
    python3 scripts/validate_integration.py
"""
from __future__ import annotations

import json
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app" / "src" / "main" / "assets"


def load(name: str):
    with (ASSETS / name).open(encoding="utf-8") as source:
        return json.load(source)


def require(condition: bool, reason: str):
    if not condition:
        raise AssertionError(reason)


def main() -> None:
    words = load("words.json")
    scene_bank = load("scenes.json")
    phrase_bank = load("examples.json")
    family_bank = load("word_families.json")
    require(isinstance(words, list) and words, "Dictionary must not be empty")
    ids: dict[int, dict] = {}
    english: set[str] = set()
    for word in words:
        word_id = word.get("id")
        require(isinstance(word_id, int) and word_id > 0, f"Invalid word ID: {word_id}")
        require(word_id not in ids, f"Duplicate word ID: {word_id}")
        for field in ("en", "ru", "pron"):
            require(isinstance(word.get(field), str), f"{word_id}: missing {field}")
        require(word["en"].strip() and word["ru"].strip(), f"{word_id}: blank word/translation")
        ids[word_id] = word
        english.add(word["en"].lower())

    scenes = scene_bank["scenes"]
    require(len({scene["id"] for scene in scenes}) == len(scenes), "Duplicate scene IDs")
    for scene in scenes:
        used: set[int] = set()
        for item in scene["words"]:
            word_id = item["wordId"]
            require(word_id in ids, f"Scene {scene['id']} refers to unknown ID {word_id}")
            require(word_id not in used, f"Scene {scene['id']}: duplicate ID {word_id}")
            used.add(word_id)
            require(item["word"].lower() == ids[word_id]["en"].lower(),
                    f"Scene {scene['id']}: wrong spelling for ID {word_id}")
            examples = item["examples"]
            require(1 <= len(examples) <= 2, f"Scene {scene['id']}: expected 1-2 examples")
            for ex in examples:
                require(ex["en"].strip() and ex["ru"].strip(), "Empty scene example")

    for word, examples in phrase_bank["examples"].items():
        require(word.lower() in english, f"Example bank has unknown word '{word}'")
        require(1 <= len(examples) <= 6, f"{word}: expected 1-6 curated examples")
        for ex in examples:
            require(ex["en"].strip() and ex["ru"].strip(), f"{word}: blank example")

    for family in family_bank["families"]:
        require(family["base"].strip() and family["baseRu"].strip(), "Empty family headword")
        for form in family["members"] + family["forms"]:
            for field in ("word", "ru", "pos", "build"):
                require(form[field].strip(), f"Family {family['base']}: missing {field}")

    manifest = ET.parse(ROOT / "app" / "src" / "main" / "AndroidManifest.xml")
    android = "{http://schemas.android.com/apk/res/android}"
    activities = {
        item.attrib.get(android + "name"): item
        for item in manifest.findall("./application/activity")
    }
    require(".MainActivity" in activities, "Missing main lesson activity")
    require(".AskWordActivity" in activities, "Missing Ask Word activity")
    require(activities[".AskWordActivity"].attrib.get(android + "exported") == "false",
            "Ask Word activity must not be externally exported")
    permissions = {
        item.attrib.get(android + "name")
        for item in manifest.findall("./uses-permission")
    }
    require("android.permission.INTERNET" not in permissions,
            "Offline-first beta unexpectedly requests INTERNET permission")

    print(
        f"Integration validation passed: {len(words)} words, "
        f"{len(scenes)} scenes, {len(phrase_bank['examples'])} example groups, "
        f"{len(family_bank['families'])} word families, 2 activities."
    )


if __name__ == "__main__":
    main()
