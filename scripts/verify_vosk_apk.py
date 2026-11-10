#!/usr/bin/env python3
"""Fail a 0.9.7 APK build unless RU/EN Vosk models and native ABIs are packaged."""
from __future__ import annotations

import sys
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
APK = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
EXPECTED = ("assets/speech/en.zip", "assets/speech/ru.zip")


def main():
    if not APK.exists():
        raise FileNotFoundError(APK)
    with ZipFile(APK) as apk:
        names = apk.namelist()
        for path in EXPECTED:
            if path not in names:
                raise AssertionError(f"Missing bundled speech asset {path}")
            size = apk.getinfo(path).file_size
            if size < 15 * 1024 * 1024:
                raise AssertionError(f"Speech model {path} too small to be a real acoustic model")
            print(f"Verified APK model asset {path}: {size:,} bytes")
        for abi in ("arm64-v8a", "x86_64"):
            libraries = [n for n in names if n.startswith(f"lib/{abi}/") and n.endswith(".so")]
            if not libraries:
                raise AssertionError(f"Missing Vosk / JNA native libraries for {abi}")
            print(f"Verified {abi}: {len(libraries)} native library entries")
    print(f"Verified fully bundled APK: {APK.stat().st_size:,} bytes")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"OFFLINE APK VERIFICATION FAILED: {exc}", file=sys.stderr)
        sys.exit(1)
