"""Regression tests for APK asset manifest verifier."""
import hashlib
import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("image_factory_verify_manifest", Path(__file__).with_name("verify_manifest.py"))
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

class ManifestVerificationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.assets = self.root / "assets"
        self.assets.mkdir()
        self.manifest = self.root / "manifest.json"

    def make_manifest(self, items):
        self.manifest.write_text(json.dumps({"count": len(items), "items": items}), encoding="utf-8")

    def item(self, filename, data=b"image", verified=True):
        return {"filename": filename, "sha256": hashlib.sha256(data).hexdigest(),
                "id_verified_against_app_dictionary": verified}

    def test_verified_binary_and_no_changes(self):
        (self.assets / "a.webp").write_bytes(b"image")
        self.make_manifest([self.item("a.webp")])
        before = (self.assets / "a.webp").read_bytes()
        result = mod.verify(self.manifest, self.assets)
        self.assertEqual(result["status"], "PASS")
        self.assertEqual(result["summary"]["verified_sha256"], 1)
        self.assertEqual((self.assets / "a.webp").read_bytes(), before)

    def test_missing_and_modified_files(self):
        (self.assets / "a.webp").write_bytes(b"tampered")
        self.make_manifest([self.item("a.webp"), self.item("missing.webp")])
        result = mod.verify(self.manifest, self.assets)
        self.assertEqual(result["status"], "BLOCKED")
        self.assertEqual(result["summary"]["hash_mismatches"], 1)
        self.assertEqual(result["summary"]["missing"], 1)

    def test_duplicate_contents_and_unverified_ids(self):
        for name in ("a.webp", "b.webp"):
            (self.assets / name).write_bytes(b"same")
        self.make_manifest([self.item("a.webp", b"same", False), self.item("b.webp", b"same", False)])
        result = mod.verify(self.manifest, self.assets)
        self.assertEqual(result["summary"]["duplicate_binary_files"], 1)
        self.assertEqual(result["summary"]["unverified_dictionary_ids"], 2)
        self.assertEqual(result["status"], "BLOCKED")

    def test_path_traversal_rejected(self):
        self.make_manifest([self.item("../secret.webp")])
        result = mod.verify(self.manifest, self.assets)
        self.assertEqual(result["summary"]["unsafe_or_duplicate_names"], 1)

    def test_invalid_count_rejected(self):
        self.manifest.write_text(json.dumps({"count": 2, "items": [self.item("a.webp")]}))
        with self.assertRaises(ValueError):
            mod.verify(self.manifest, self.assets)

    def test_cli_blocked_returns_nonzero_and_report(self):
        (self.assets / "a.webp").write_bytes(b"tampered")
        self.make_manifest([self.item("a.webp")])
        report = self.root / "blocked.json"
        completed = subprocess.run(
            [sys.executable, str(Path(__file__).with_name("verify_manifest.py")),
             str(self.manifest), str(self.assets), "--output", str(report)],
            capture_output=True, text=True, check=False)
        self.assertEqual(completed.returncode, 2)
        self.assertEqual(json.loads(report.read_text(encoding="utf-8"))["status"], "BLOCKED")

    def test_cli_pass_returns_zero(self):
        (self.assets / "a.webp").write_bytes(b"image")
        self.make_manifest([self.item("a.webp")])
        report = self.root / "passed.json"
        completed = subprocess.run(
            [sys.executable, str(Path(__file__).with_name("verify_manifest.py")),
             str(self.manifest), str(self.assets), "--output", str(report)],
            capture_output=True, text=True, check=False)
        self.assertEqual(completed.returncode, 0)
        self.assertEqual(json.loads(report.read_text(encoding="utf-8"))["status"], "PASS")

if __name__ == "__main__":
    unittest.main()
