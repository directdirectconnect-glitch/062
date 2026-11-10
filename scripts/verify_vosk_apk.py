#!/usr/bin/env python3
"""Preflight: require complete, offline EN/RU Vosk model archives inside the APK.

Structural checks are not a substitute for a microphone test on a real phone.
"""
from __future__ import annotations

import sys
from io import BytesIO
from pathlib import Path
from zipfile import BadZipFile, ZipFile

ROOT = Path(__file__).resolve().parents[1]
APK = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
MODELS = {
    "en": "vosk-model-small-en-us-0.15",
    "ru": "vosk-model-small-ru-0.22",
}
REQUIRED = ("am/final.mdl", "conf/model.conf", "graph/HCLG.fst", "graph/words.txt")
MAX_EXPANDED_BYTES = 420 * 1024 * 1024
MAX_FILES = 10_000


def verify_model_archive(archive: bytes, language: str, root: str) -> None:
    with ZipFile(BytesIO(archive)) as model:
        files = [item for item in model.infolist() if not item.is_dir()]
        if not files or len(files) > MAX_FILES:
            raise AssertionError(f"{language}: invalid model file count {len(files)}")
        expanded = sum(item.file_size for item in files)
        if expanded > MAX_EXPANDED_BYTES:
            raise AssertionError(f"{language}: expanded model exceeds 420 MiB")
        paths = {item.filename for item in files}
        prefix = root + "/"
        for name in paths:
            if not name.startswith(prefix) or ".." in name.split("/") or "\\" in name:
                raise AssertionError(f"{language}: unexpected/unsafe entry {name!r}")
        for required in REQUIRED:
            entry = prefix + required
            if entry not in paths or model.getinfo(entry).file_size == 0:
                raise AssertionError(f"{language}: required model file missing/empty: {entry}")
        invalid = model.testzip()
        if invalid is not None:
            raise BadZipFile(f"{language}: corrupt ZIP entry {invalid}")
        print(f"Verified {language} Vosk model structure: {len(files)} files, "
              f"{expanded:,} expanded bytes")


def main() -> None:
    if not APK.exists():
        raise FileNotFoundError(APK)
    with ZipFile(APK) as apk:
        names = set(apk.namelist())
        for language, root in MODELS.items():
            path = f"assets/speech/{language}.zip"
            if path not in names:
                raise AssertionError(f"Missing bundled speech asset {path}")
            size = apk.getinfo(path).file_size
            if size < 15 * 1024 * 1024:
                raise AssertionError(f"Speech model {path} too small to be real")
            verify_model_archive(apk.read(path), language, root)
            print(f"Verified APK asset {path}: {size:,} bytes")
        for abi in ("arm64-v8a", "x86_64"):
            libraries = [name for name in names
                         if name.startswith(f"lib/{abi}/") and name.endswith(".so")]
            if not libraries:
                raise AssertionError(f"Missing native libraries for {abi}")
            print(f"Verified {abi}: {len(libraries)} native libraries")
    print(f"Offline APK preflight PASSED: {APK.stat().st_size:,} bytes")
    print("NOTE: Galaxy Fold microphone and recognition accuracy remain untested.")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"OFFLINE APK VERIFICATION FAILED: {exc}", file=sys.stderr)
        sys.exit(1)
