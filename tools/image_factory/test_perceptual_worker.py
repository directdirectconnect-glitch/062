"""End-to-end perceptual duplicate guard tests with real PNG files."""
import importlib.util
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
validate = load("validate").validate_image

def patterned_image():
    im = Image.new("RGB", (800, 800))
    for y in range(800):
        for x in range(800):
            im.putpixel((x, y), ((x * 3) % 256, (y * 5) % 256, (x + y) % 256))
    return im

class PerceptualWorkerTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.db = self.root / "jobs.sqlite"
        with queue.connect(self.db) as conn:
            conn.execute("INSERT INTO jobs(word_id,word,updated_at) VALUES('1','apple','now')")
            conn.execute("INSERT INTO jobs(word_id,word,updated_at) VALUES('2','bridge','now')")
            conn.commit()

    def test_reencoded_duplicate_rejected(self):
        image = patterned_image()
        calls = [0]
        def backend(_prompt, path):
            calls[0] += 1
            image.save(path, format="PNG", compress_level=0 if calls[0] == 1 else 9)
        result = worker.run_batch(self.db, self.root / "images", backend, validator=validate,
                                  max_attempts=1, perceptual_threshold=0)
        self.assertEqual(result, {"attempted": 2, "generated": 1, "failed": 1})
        with sqlite3.connect(self.db) as conn:
            statuses = conn.execute("SELECT status,error FROM jobs ORDER BY word_id").fetchall()
        self.assertEqual(statuses[0][0], "generated")
        self.assertEqual(statuses[1][0], "failed")
        self.assertIn("Perceptual duplicate image", statuses[1][1])

    def test_threshold_validation(self):
        with self.assertRaisesRegex(ValueError, "perceptual threshold"):
            worker.run_batch(self.db, self.root / "images", lambda p, q: None,
                             validator=validate, perceptual_threshold=65)

    def test_missing_existing_file_fails_closed(self):
        with queue.connect(self.db) as conn:
            conn.execute("UPDATE jobs SET status='existing',image_path=? WHERE word_id='1'",
                         (str(self.root / "missing.png"),))
            conn.commit()
        def backend(_prompt, path):
            patterned_image().save(path)
        result = worker.run_batch(self.db, self.root / "images", backend, validator=validate,
                                  max_attempts=1, perceptual_threshold=5)
        self.assertEqual(result["failed"], 1)
        with sqlite3.connect(self.db) as conn:
            error = conn.execute("SELECT error FROM jobs WHERE word_id='2'").fetchone()[0]
        self.assertIn("Cannot verify existing image", error)

if __name__ == "__main__":
    unittest.main()
