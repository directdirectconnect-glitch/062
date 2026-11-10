#!/usr/bin/env python3
"""Bundle pinned Vosk RU/EN mobile acoustic models at Android BUILD time.

This script runs only on the build machine. It never runs on the user's phone.
The installed APK has no INTERNET permission and needs no ASR model download.
"""
from __future__ import annotations

import hashlib
import os
from pathlib import Path
import sys
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "app/src/main/assets/speech"
MODELS = {
    "en": ("vosk-model-small-en-us-0.15", "30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498"),
    "ru": ("vosk-model-small-ru-0.22", "961d5ff98a17f4aa6de69864d0aa71fa5bac682301d2b5d17a3f24c5c99a46d4"),
}
MAX_ARCHIVE_SIZE = 80 * 1024 * 1024  # reject accidental giant model downloads


def download_one(language: str, name: str, sha256: str):
    destination = OUTPUT / f"{language}.zip"
    if destination.exists():
        digest = hashlib.sha256(destination.read_bytes()).hexdigest()
        if digest == sha256:
            print(f"Using verified {language} model from local cache", flush=True)
            return
        raise ValueError(f"Incorrect local {language} model checksum: {digest}")
    url = f"https://alphacephei.com/vosk/models/{name}.zip"
    tmp = destination.with_suffix(".part")
    digest = hashlib.sha256()
    count = 0
    try:
        print(f"Downloading official {language} model for APK packaging", flush=True)
        req = urllib.request.Request(url, headers={"User-Agent": "EnglishWords-OfflineModelPacker/1.0"})
        with urllib.request.urlopen(req, timeout=90) as response, tmp.open("wb") as out:
            while True:
                chunk = response.read(1024 * 1024)
                if not chunk:
                    break
                count += len(chunk)
                if count > MAX_ARCHIVE_SIZE:
                    raise ValueError(f"{name}: over size limit")
                digest.update(chunk)
                out.write(chunk)
        actual = digest.hexdigest()
        if actual != sha256:
            raise ValueError(f"{name}: SHA-256 mismatch. Expected {sha256}, received {actual}")
        with zipfile.ZipFile(tmp) as archive:
            names = archive.namelist()
            if not names or any(not n.startswith(name + "/") for n in names):
                raise ValueError(f"{name}: unexpected model directory structure")
            required = ("am/final.mdl", "conf/model.conf")
            if not all(name + "/" + p in names for p in required):
                raise ValueError(f"{name}: missing required model files")
            bad = archive.testzip()
            if bad:
                raise ValueError(f"{name}: damaged ZIP entry {bad}")
        os.replace(tmp, destination)
        print(f"Bundled {language}: {count:,} bytes, SHA-256 {actual}", flush=True)
    finally:
        tmp.unlink(missing_ok=True)


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for language, (name, sha256) in MODELS.items():
        download_one(language, name, sha256)
    print("Both mobile Vosk models are ready for offline APK packaging.", flush=True)


if __name__ == "__main__":
    main()
