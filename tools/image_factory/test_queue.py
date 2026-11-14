import csv
import tempfile
import unittest
from pathlib import Path
from queue import connect, ingest, register

class QueueTests(unittest.TestCase):
    def test_import_idempotent_and_register(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d)
            words=root/"words.csv"
            words.write_text("word_id,word,translation\n1,apple,яблоко\n2,bridge,мост\n",encoding="utf-8")
            image=root/"apple.png"; image.write_bytes(b"test-image-bytes")
            manifest=root/"existing.csv"
            manifest.write_text(f"word_id,image_path\n1,{image}\n",encoding="utf-8")
            with connect(root/"test.sqlite") as db:
                self.assertEqual(ingest(db,words),2)
                self.assertEqual(ingest(db,words),0)
                self.assertEqual(register(db,manifest),1)
                self.assertEqual(register(db,manifest),0)
                self.assertEqual(dict(db.execute("SELECT status,COUNT(*) FROM jobs GROUP BY status")),
                                 {"existing":1,"pending":1})

if __name__=="__main__":
    unittest.main()
