#!/usr/bin/env python3
"""Fetch and stage official Sherpa-ONNX Piper voices into Android APK assets.

The two archives are fetched at build time and embedded into app/src/main/assets/tts.
This script fails closed: no model => no release APK. Model archive provenance is
recorded in each manifest. No placeholder audio or Android TTS is accepted.
"""
import hashlib
import json
import os
from pathlib import Path
import shutil
import tarfile
import tempfile
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / "app/src/main/assets/tts"
MODELS = {
    "en": ("vits-piper-en_US-lessac-medium", "en_US-lessac-medium.onnx", "en-US"),
    "ru": ("vits-piper-ru_RU-irina-medium", "ru_RU-irina-medium.onnx", "ru-RU"),
}
BASE = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/"


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def install(lang, tmp):
    name, model_filename, locale = MODELS[lang]
    url = BASE + name + ".tar.bz2"
    archive = tmp / (name + ".tar.bz2")
    print(f"Fetching {lang} offline model: {url}", flush=True)
    urllib.request.urlretrieve(url, archive)
    if archive.stat().st_size < 1024 * 1024:
        raise RuntimeError(f"{lang}: suspiciously small model archive")
    unpack = tmp / (lang + "-unpacked")
    unpack.mkdir()
    with tarfile.open(archive, "r:bz2") as tar:
        for member in tar.getmembers():
            target = (unpack / member.name).resolve()
            if not target.is_relative_to(unpack.resolve()):
                raise RuntimeError(f"{lang}: unsafe archive path")
            if member.issym() or member.islnk() or member.isdev():
                raise RuntimeError(f"{lang}: unsafe archive member")
        tar.extractall(unpack, filter="data")
    source = unpack / name
    model = source / model_filename
    tokens = source / "tokens.txt"
    espeak = source / "espeak-ng-data"
    if not (model.is_file() and tokens.is_file() and espeak.is_dir()):
        raise RuntimeError(f"{lang}: required model, tokens or espeak-ng-data missing")
    target = DEST / lang
    if target.exists():
        shutil.rmtree(target)
    target.mkdir(parents=True)
    shutil.copy2(model, target / "model.onnx")
    shutil.copy2(tokens, target / "tokens.txt")
    shutil.copytree(espeak, target / "espeak-ng-data")
    files = {}
    for file in sorted(target.rglob("*")):
        if file.is_file():
            files[file.relative_to(target).as_posix()] = sha256(file)
    (target / "manifest.json").write_text(json.dumps({
        "model_id": name, "locale": locale, "source": url,
        "archive_sha256": sha256(archive), "files": files,
    }, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"{lang}: staged {len(files)} files; model size={model.stat().st_size}", flush=True)


def main():
    DEST.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="englishwords-tts-") as directory:
        temp = Path(directory)
        for language in MODELS:
            install(language, temp)
    print("Both offline TTS models staged for APK packaging.")


if __name__ == "__main__":
    main()
