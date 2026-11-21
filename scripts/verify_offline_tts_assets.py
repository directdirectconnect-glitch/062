#!/usr/bin/env python3
"""Release gate: ensure both embedded offline TTS voices are packaged.

Expected model manifests:
  app/src/main/assets/tts/en/manifest.json
  app/src/main/assets/tts/ru/manifest.json
Each manifest declares model_id, locale, files (relative paths) and sha256 checksums.
This verifier never substitutes Android system voices for bundled assets.
"""
import hashlib
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets/tts"


def verify(locale):
    directory = ASSETS / locale
    manifest_file = directory / "manifest.json"
    if not manifest_file.is_file():
        return [f"{locale}: missing {manifest_file.relative_to(ROOT)}"]
    try:
        manifest = json.loads(manifest_file.read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        return [f"{locale}: invalid manifest: {exc}"]
    errors = []
    if manifest.get("locale", "").split("-")[0] != locale:
        errors.append(f"{locale}: incorrect locale")
    if not manifest.get("model_id"):
        errors.append(f"{locale}: missing model_id")
    files = manifest.get("files")
    if not isinstance(files, dict) or not files:
        errors.append(f"{locale}: no model files and SHA-256 checksums")
        return errors
    for name, expected in files.items():
        target = (directory / name).resolve()
        if not target.is_relative_to(directory.resolve()):
            errors.append(f"{locale}: unsafe path {name}")
            continue
        if not target.is_file():
            errors.append(f"{locale}: missing model file {name}")
            continue
        actual = hashlib.file_digest(target.open("rb"), "sha256").hexdigest()
        if actual.lower() != str(expected).lower():
            errors.append(f"{locale}: SHA-256 mismatch for {name}")
    return errors


def main():
    errors = verify("en") + verify("ru")
    if errors:
        print("OFFLINE_TTS_ASSETS: FAIL")
        for error in errors:
            print(" -", error)
        return 1
    print("OFFLINE_TTS_ASSETS: PASS — EN and RU manifests and file checksums")
    return 0


if __name__ == "__main__":
    sys.exit(main())
