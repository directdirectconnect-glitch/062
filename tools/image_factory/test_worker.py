"""Image Factory worker tests with a deterministic fake backend."""
import importlib.util
import sqlite3
import tempfile
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parent
def load(name):
    spec=importlib.util.spec_from_file_location("image_factory_"+name,ROOT/(name+".py"))
    module=importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module
queue=load("queue")
worker=load("worker")

class WorkerTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name)
        self.db=self.root/"jobs.sqlite"
        with queue.connect(self.db) as conn:
            conn.execute("INSERT INTO jobs(word_id,word,updated_at) VALUES('1','apple','now')")
            conn.commit()

    def test_success_and_no_duplicate_execution(self):
        def backend(prompt,path):
            self.assertIn("apple",prompt)
            path.write_bytes(b"image bytes")
        def validator(path):
            self.assertTrue(path.exists())
        result=worker.run_batch(self.db,self.root/"images",backend,validator=validator)
        self.assertEqual(result,{"attempted":1,"generated":1,"failed":0})
        self.assertEqual(worker.run_batch(self.db,self.root/"images",backend,validator=validator)["attempted"],0)

    def test_failure_retries_then_terminal(self):
        def backend(prompt,path): raise RuntimeError("temporary error")
        for i in range(3):
            result=worker.run_batch(self.db,self.root/"images",backend,validator=lambda p:None,max_attempts=3)
            self.assertEqual(result["failed"],1)
        with sqlite3.connect(self.db) as db:
            self.assertEqual(db.execute("SELECT status,attempts FROM jobs").fetchone(),("failed",3))

    def test_recover_interrupted(self):
        with sqlite3.connect(self.db) as db:
            db.execute("UPDATE jobs SET status='running'")
            db.commit()
        self.assertEqual(worker.recover_interrupted(self.db),1)

if __name__=="__main__":
    unittest.main()
