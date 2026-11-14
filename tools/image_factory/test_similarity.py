"""Regression tests for near-duplicate image detection."""
import importlib.util
import tempfile
import unittest
from pathlib import Path
from PIL import Image, ImageEnhance

spec = importlib.util.spec_from_file_location("image_factory_similarity", Path(__file__).with_name("similarity.py"))
similarity = importlib.util.module_from_spec(spec)
spec.loader.exec_module(similarity)

class SimilarityTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        im = Image.new("RGB", (144, 128))
        for y in range(im.height):
            for x in range(im.width):
                im.putpixel((x, y), (x * 2 % 256, y * 2 % 256, (x + y) % 256))
        self.original = self.root / "original.png"
        im.save(self.original)

    def test_same_image_different_encoding(self):
        with Image.open(self.original) as im:
            other = self.root / "reencoded.bmp"
            im.save(other)
        self.assertEqual(similarity.distance(similarity.dhash(self.original), similarity.dhash(other)), 0)
        self.assertEqual(similarity.find_similar(self.original, [other], threshold=0), [(str(other), 0)])

    def test_resized_image(self):
        with Image.open(self.original) as im:
            resized = self.root / "resized.png"
            im.resize((288, 256), Image.Resampling.NEAREST).save(resized)
        self.assertLessEqual(similarity.distance(similarity.dhash(self.original), similarity.dhash(resized)), 5)

    def test_brightness_variant(self):
        with Image.open(self.original) as im:
            variant = self.root / "bright.png"
            ImageEnhance.Brightness(im).enhance(1.1).save(variant)
        self.assertLessEqual(similarity.distance(similarity.dhash(self.original), similarity.dhash(variant)), 5)

    def test_distinct_pattern_not_identical(self):
        other = self.root / "reverse.png"
        with Image.open(self.original) as im:
            im.transpose(Image.Transpose.FLIP_LEFT_RIGHT).save(other)
        self.assertGreater(similarity.distance(similarity.dhash(self.original), similarity.dhash(other)), 5)

    def test_corrupt_file_rejected(self):
        broken = self.root / "broken.png"
        broken.write_bytes(b"invalid")
        with self.assertRaises(Exception):
            similarity.dhash(broken)

    def test_distance_and_threshold_validation(self):
        self.assertEqual(similarity.distance(0, 2**64 - 1), 64)
        with self.assertRaises(ValueError):
            similarity.distance(-1, 0)
        with self.assertRaises(ValueError):
            similarity.find_similar(self.original, [], threshold=65)

if __name__ == "__main__":
    unittest.main()
