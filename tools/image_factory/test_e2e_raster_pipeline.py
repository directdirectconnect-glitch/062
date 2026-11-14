"""End-to-end Image Factory test with actual raster PNGs, without a GPU/model.

The backend deliberately creates deterministic synthetic photographs-like raster
patterns via Pillow; this proves file production and pipeline integration, NOT AI
image generation, realism or subject correctness.
"""
import hashlib
import importlib.util
import random
import sqlite3
import tempfile
import unittest
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent

def load(name):
    spec = importlib.util.spec_from_file_location("image_factory_e2e_" + name, ROOT / (name + ".py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module

queue = load("queue")
worker = load("worker")
validate = load("validate")
audit = load("audit")

class RasterPipelineEndToEndTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.db = self.root / "queue.sqlite"
        self.out = self.root / "images"
        with queue.connect(self.db) as conn:
            for wid, word in [("1", "apple"), ("2", "bridge")]:
                conn.execute("INSERT INTO jobs(word_id,word,updated_at) VALUES(?,?,?)", (wid, word, "now"))
            conn.commit()

    @staticmethod
    def raster_backend(prompt, path):
        # Actual valid 800x800 RGB PNG, reproducible across runs.
        seed = int.from_bytes(hashlib.sha256(prompt.encode()).digest()[:8], "big")
        rng = random.Random(seed)
        pixels = rng.randbytes(800 * 800 * 3)
        Image.frombytes("RGB", (800, 800), pixels).save(path, format="PNG")

    def test_two_real_pngs_saved_and_audited(self):
        result = worker.run_batch(self.db, self.out, self.raster_backend,
                                  limit=2, validator=validate.validate_image,
                                  perceptual_threshold=0)
        self.assertEqual(result, {"attempted": 2, "generated": 2, "failed": 0})
        files = sorted(self.out.glob("*.png"))
        self.assertEqual(len(files), 2)
        for path in files:
            self.assertEqual(validate.validate_image(path)["format"], "PNG")
        report = audit.audit(self.out, minimum=768, threshold=0)
        self.assertEqual(report["summary"]["valid"], 2)
        self.assertEqual(report["summary"]["rejected"], 0)
        self.assertEqual(report["summary"]["exact_duplicate_files"], 0)
        with sqlite3.connect(self.db) as conn:
            self.assertEqual(conn.execute("SELECT count(*) FROM jobs WHERE status='generated'").fetchone()[0], 2)
        self.assertEqual(worker.run_batch(self.db, self.out, self.raster_backend,
                                          validator=validate.validate_image)["attempted"], 0)

    def test_invalid_real_png_rejected(self):
        def too_small(prompt, path):
            Image.new("RGB", (32, 32), (255, 0, 0)).save(path, format="PNG")
        result = worker.run_batch(self.db, self.out, too_small, limit=1,
                                  validator=validate.validate_image)
        self.assertEqual(result["generated"], 0)
        self.assertEqual(result["failed"], 1)
        self.assertEqual(list(self.out.glob("*.png")), [])
        self.assertEqual(list(self.out.glob("*.partial.png")), [])

    def test_identical_real_png_rejected(self):
        def same_png(prompt, path):
            self.raster_backend("fixed seed", path)
        result = worker.run_batch(self.db, self.out, same_png, limit=2,
                                  validator=validate.validate_image)
        self.assertEqual(result["generated"], 1)
        self.assertEqual(result["failed"], 1)
        self.assertEqual(len(list(self.out.glob("*.png"))), 1)

if __name__ == "__main__":
    unittest.main()
