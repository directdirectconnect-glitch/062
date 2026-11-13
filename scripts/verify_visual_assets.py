#!/usr/bin/env python3
"""Verify the offline visual catalog against its source mapping and assets."""
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets"
MAPPING = ROOT / "build-requests/visual-164-drive-mapping.json"


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


def require(condition, message):
    if not condition:
        raise ValueError(message)


def main():
    words = {word["id"]: word for word in read_json(ASSETS / "words.json")}
    source = read_json(MAPPING)["images"]
    expected = {item["wordId"]: item["en"] for item in source}
    catalog = read_json(ASSETS / "visual_vocabulary.json")
    rows = catalog["images"]
    require(catalog["schemaVersion"] == 1, "Unexpected visual catalog schema")
    require(len(source) == len(expected) == len(rows) == 164, "Expected 164 mappings")
    require({row["wordId"] for row in rows} == expected.keys(), "Duplicate or incorrect word IDs")
    paths = {row["asset"] for row in rows}
    require(len(paths) == 164, "Duplicate asset paths")
    for row in rows:
        word_id = row["wordId"]
        relative = row["asset"]
        require(relative == f"images/word_{word_id:04}.webp", f"Invalid asset path: {relative}")
        require(word_id in words, f"Unknown dictionary ID: {word_id}")
        require(row["altEn"] == words[word_id]["en"] == expected[word_id], f"Wrong word: {word_id}")
        with Image.open(ASSETS / relative) as image:
            require(image.format == "WEBP", f"Not a WebP: {relative}")
            image.load()
    require({p.relative_to(ASSETS).as_posix() for p in (ASSETS / "images").iterdir()} == paths,
            "Asset folder differs from visual catalog")
    print(f"PASS: {len(words)} dictionary IDs and {len(rows)} decodable offline WebP assets")


if __name__ == "__main__":
    main()
