#!/usr/bin/env python3
"""Verify that an APK bundles Vosk and cannot use Android networking or ASR."""
from __future__ import annotations

import os
import re
import shutil
import subprocess
import sys
from io import BytesIO
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
APK = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
EXPECTED = ("assets/speech/en.zip", "assets/speech/ru.zip")


def find_aapt():
    executable = shutil.which("aapt")
    if executable:
        return executable
    for variable in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        sdk = os.environ.get(variable)
        if sdk:
            candidates = sorted(Path(sdk).glob("build-tools/*/aapt*"), reverse=True)
            for path in candidates:
                if path.name in ("aapt", "aapt.exe") and path.is_file():
                    return str(path)
    raise FileNotFoundError("Android SDK build-tools aapt is required")


def main():
    apk_path = Path(sys.argv[1]) if len(sys.argv) > 1 else APK
    aapt = find_aapt()
    permissions = subprocess.check_output([aapt, "dump", "permissions", str(apk_path)], text=True)
    forbidden = {"INTERNET", "ACCESS_NETWORK_STATE", "CHANGE_NETWORK_STATE", "ACCESS_WIFI_STATE",
                 "CHANGE_WIFI_STATE", "NEARBY_WIFI_DEVICES"}
    declared = set(re.findall(r"android\.permission\.([A-Z_]+)", permissions))
    if forbidden & declared:
        raise AssertionError(f"Network permissions declared: {sorted(forbidden & declared)}")
    manifest = subprocess.check_output(
        [aapt, "dump", "xmltree", str(apk_path), "AndroidManifest.xml"], text=True
    )
    if "android.speech.RecognitionService" in manifest:
        raise AssertionError("System speech service declared")
    with ZipFile(apk_path) as apk:
        names = apk.namelist()
        for path in EXPECTED:
            if path not in names:
                raise AssertionError(f"Missing bundled speech asset {path}")
            size = apk.getinfo(path).file_size
            if size < 15 * 1024 * 1024:
                raise AssertionError(f"Speech model {path} too small to be a real acoustic model")
            with ZipFile(BytesIO(apk.read(path))) as model:
                damaged_entry = model.testzip()
                if damaged_entry:
                    raise AssertionError(f"Damaged model asset {path}: {damaged_entry}")
            print(f"Verified APK model asset {path}: {size:,} bytes")
        for abi in ("arm64-v8a", "x86_64"):
            required = {f"lib/{abi}/{name}" for name in ("libvosk.so", "libjnidispatch.so")}
            missing = required.difference(names)
            if missing:
                raise AssertionError(f"Missing native libraries: {sorted(missing)}")
            print(f"Verified Vosk and JNA for {abi}")
    print(f"Verified offline APK: {apk_path.stat().st_size:,} bytes")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"OFFLINE APK VERIFICATION FAILED: {exc}", file=sys.stderr)
        sys.exit(1)
