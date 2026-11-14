"""Archive audit regression tests: no deletion, deterministic classification."""
import importlib.util
import tempfile
import unittest
from pathlib import Path
from PIL import Image

spec = importlib.util.spec_from_file_location("image_factory_audit", Path(__file__).with_name("audit.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class ArchiveAuditTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        im = Image.new("RGB", (800, 800))
        for y in range(800):
            for x in range(800):
                im.putpixel((x, y), ((x * 3) % 256, (y * 5) % 256, (x + y) % 256))
        self.image = im

    def test_exact_and_visual_duplicate_report(self):
        first = self.root / "a.png"
        second = self.root / "b.png"
        recoded = self.root / "c.png"
        self.image.save(first, compress_level=1)
        second.write_bytes(first.read_bytes())
        self.image.save(recoded, compress_level=9)
        before = {p.name: p.read_bytes() for p in self.root.iterdir()}
        result = module.audit(self.root, threshold=0)
        self.assertEqual(result["summary"]["scanned"], 3)
        self.assertEqual(result["summary"]["valid"], 3)
        self.assertEqual(result["summary"]["exact_duplicate_files"], 1)
        self.assertEqual(result["summary"]["distinct_sha256"], 2)
        self.assertEqual(result["summary"]["possible_duplicate_pairs"], 2)
        self.assertEqual({p.name: p.read_bytes() for p in self.root.iterdir()}, before)

    def test_corrupt_and_small_files(self):
        (self.root / "broken.png").write_bytes(b"not an image")
        Image.new("RGB", (32, 32), "red").save(self.root / "tiny.png")
        result = module.audit(self.root)
        self.assertEqual(result["summary"]["rejected"], 2)
        self.assertEqual(result["summary"]["valid"], 0)

    def test_empty_and_invalid_arguments(self):
        self.assertEqual(module.audit(self.root)["summary"]["scanned"], 0)
        with self.assertRaises(ValueError):
            module.audit(self.root, threshold=-1)
        with self.assertRaises(ValueError):
            module.audit(self.root, minimum=0)
        with self.assertRaises(ValueError):
            module.audit(self.root / "missing")

    def test_non_images_ignored_and_nested_files(self):
        folder = self.root / "nested"
        folder.mkdir()
        self.image.save(folder / "photo.png")
        (self.root / "notes.txt").write_text("ignore")
        result = module.audit(self.root)
        self.assertEqual(result["summary"]["scanned"], 1)
        self.assertEqual(result["valid_files"][0]["file"], "nested/photo.png")

if __name__ == "__main__":
    unittest.main()
