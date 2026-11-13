#!/usr/bin/env python3
"""Report exactly which local vocabulary and visual records a preview APK build uses."""
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1]
assets = root / "app/src/main/assets"
words = json.loads((assets / "words.json").read_text(encoding="utf-8"))
visual = json.loads((assets / "visual_vocabulary.json").read_text(encoding="utf-8"))
images = visual.get("images", [])
if not isinstance(words, list) or not isinstance(images, list):
    raise SystemExit("Invalid bundled content manifest structure")
report = (
    "English Words Android debug APK - bundled content\n"
    f"Dictionary entries: {len(words)} / target 5200\n"
    f"Visual mappings: {len(images)} / target 164\n"
    f"Content-ready: {'YES' if len(words) >= 5200 and len(images) >= 164 else 'NO - TECHNICAL PREVIEW ONLY'}\n"
)
output = root / "build" / "apk-content-report.txt"
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(report, encoding="utf-8")
print(report, end="")
