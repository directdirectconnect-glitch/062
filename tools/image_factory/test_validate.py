"""Raster integrity and duplicate detection regression tests."""
import importlib.util
import io
import sqlite3
import tempfile
import unittest
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent

def load(name):
    spec = importlib.util.spec_from_file_location("image_factory_" + name, ROOT / (name + ".py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module

queue = load("queue")
worker = load("worker")
validate_image = load("validate").validate_image

def photo_bytes(color=(20, 90, 160)):
    im = Image.new("RGB", (800, 800))
    pixels = im.load()
    for y in range(800):
        for x in range(800):
            pixels[x, y] = ((color[0] + x // 4) % 256,
                            (color[1] + y // 4) % 256,
                            (color[2] + (x + y) // 8) % 256)
    data = io.BytesIO()
    im.save(data, format="PNG")
    return data.getvalue()

class ImageValidationTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.valid = photo_bytes()

    def test_valid_raster(self):
        path = self.root / "valid.png"
        path.write_bytes(self.valid)
        result = validate_image(path)
        self.assertEqual((result["width"], result["height"], result["format"]), (800, 800, "PNG"))

    def test_random_bytes_rejected(self):
        path = self.root / "broken.png"
        path.write_bytes(b"not an image")
        with self.assertRaises(Exception):
            validate_image(path)

    def test_truncated_png_rejected(self):
        path = self.root / "truncated.png"
        path.write_bytes(self.valid[:len(self.valid)//2])
        with self.assertRaises(Exception):
            validate_image(path)

    def test_small_image_rejected(self):
        path = self.root / "small.png"
        Image.new("RGB", (100, 100), "red").save(path)
        with self.assertRaisesRegex(ValueError, "too small"):
            validate_image(path)

    def test_uniform_image_rejected(self):
        path = self.root / "uniform.png"
        Image.new("RGB", (800, 800), "gray").save(path)
        with self.assertRaisesRegex(ValueError, "uniform"):
            validate_image(path)

    def test_duplicate_image_rejected_by_worker(self):
        db = self.root / "jobs.sqlite"
        with queue.connect(db) as conn:
            for wid, word in (("1", "apple"), ("2", "bridge")):
                conn.execute("INSERT INTO jobs(word_id,word,updated_at) VALUES(?,?,?)", (wid, word, "now"))
            conn.commit()

        def backend(_prompt, output):
            output.write_bytes(self.valid)

        result = worker.run_batch(db, self.root / "output", backend, limit=2,
                                  max_attempts=1, validator=validate_image)
        self.assertEqual(result, {"attempted": 2, "generated": 1, "failed": 1})
        with sqlite3.connect(db) as conn:
            rows = conn.execute("SELECT status,error FROM jobs ORDER BY word_id").fetchall()
        self.assertEqual(rows[0][0], "generated")
        self.assertEqual(rows[1][0], "failed")
        self.assertIn("Exact duplicate image", rows[1][1])
        self.assertEqual(len(list((self.root / "output").glob("*.png"))), 1)

    def test_corrupt_backend_output_not_accepted(self):
        db = self.root / "jobs.sqlite"
        with queue.connect(db) as conn:
            conn.execute("INSERT INTO jobs(word_id,word,updated_at) VALUES('1','apple','now')")
            conn.commit()

        def backend(_prompt, output):
            output.write_bytes(b"broken image")

        result = worker.run_batch(db, self.root / "output", backend, max_attempts=1,
                                  validator=validate_image)
        self.assertEqual(result, {"attempted": 1, "generated": 0, "failed": 1})
        self.assertEqual(list((self.root / "output").glob("*.png")), [])

if __name__ == "__main__":
    unittest.main()
