#!/usr/bin/env python3
"""Fail the build unless exactly 267 WebP vocabulary assets reached the APK."""
from pathlib import Path
import hashlib
import json
import zipfile

ROOT = Path(__file__).resolve().parents[1]
apk_files = list((ROOT / "app/build/outputs/apk/debug").glob("*.apk"))
assert len(apk_files) == 1, f"Expected 1 APK; found {len(apk_files)}"
apk = apk_files[0]
with zipfile.ZipFile(apk) as archive:
    bad = archive.testzip()
    assert bad is None, f"Corrupt APK archive entry: {bad}"
    images = sorted(x for x in archive.namelist()
                    if x.startswith(("assets/images/", "assets/additional_photos/", "assets/extra_photos/"))
                    and x.lower().endswith((".webp", ".png", ".jpg", ".jpeg")))
    webps = [x for x in images if x.endswith(".webp")]
    assert len(images) == len(webps) == 267, f"Expected 267 WebP only; got {len(images)} images, {len(webps)} WebP"
    assert sum(x.startswith("assets/images/") for x in webps) == 164
    assert sum(x.startswith("assets/additional_photos/") for x in webps) == 53
    assert sum(x.startswith("assets/extra_photos/") for x in webps) == 50
    digests = {}
    for name in webps:
        payload = archive.read(name)
        assert payload[:4] == b"RIFF" and payload[8:12] == b"WEBP", f"Invalid WebP: {name}"
        d = hashlib.sha256(payload).hexdigest()
        assert d not in digests, f"Duplicate WebP: {name} = {digests.get(d)}"
        digests[d] = name
    mapped = json.loads(archive.read("assets/visual_vocabulary.json"))["images"]
    assert len(mapped) == 221, f"Expected 221 word-ID links, found {len(mapped)}"
    assert len({x["wordId"] for x in mapped}) == 221, "Repeated image word IDs"
    assert all("assets/" + x["asset"] in webps for x in mapped), "Missing mapped image"
    supplemental = json.loads(archive.read("assets/supplemental_photo_catalog.json"))["images"]
    assert len(supplemental) == 17, f"Expected 17 supplemental phrases, got {len(supplemental)}"
    assert all("assets/" + x["asset"] in webps for x in supplemental), "Missing supplemental photo"

results = ROOT / "results"
results.mkdir(exist_ok=True)
(results / "apk-image-inventory.txt").write_text("\n".join(webps) + "\n", encoding="utf-8")
report = {"status": "PASS", "apk_sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
          "dictionary_word_ids": 5126, "webp_count": len(webps), "word_image_links": len(mapped),
          "supplemental_phrases": len(supplemental), "png_jpeg": 0}
(results / "apk-image-proof.json").write_text(json.dumps(report, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")
print(json.dumps(report, ensure_ascii=False))
