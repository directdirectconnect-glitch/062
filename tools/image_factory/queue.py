"""English Words Image Factory: durable local image production queue (stdlib only)."""
import argparse
import csv
import hashlib
import json
import sqlite3
from pathlib import Path
from datetime import datetime, timezone

SCHEMA = """
CREATE TABLE IF NOT EXISTS jobs (
 word_id TEXT PRIMARY KEY, word TEXT NOT NULL, translation TEXT,
 status TEXT NOT NULL DEFAULT 'pending',
 image_path TEXT, image_sha256 TEXT, attempts INTEGER NOT NULL DEFAULT 0,
 error TEXT, updated_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_jobs_status ON jobs(status);
"""
def now():
    return datetime.now(timezone.utc).isoformat()
def connect(db):
    conn=sqlite3.connect(db)
    conn.executescript(SCHEMA)
    return conn
def ingest(conn, csv_path):
    count=0
    with open(csv_path, encoding="utf-8-sig", newline="") as f:
        for row in csv.DictReader(f):
            word=(row.get("word") or row.get("english") or "").strip()
            if not word: continue
            wid=(row.get("word_id") or row.get("id") or word.casefold()).strip()
            translation=(row.get("translation") or row.get("russian") or "").strip()
            cur=conn.execute("INSERT OR IGNORE INTO jobs(word_id,word,translation,updated_at) VALUES(?,?,?,?)",
                             (wid,word,translation,now()))
            count+=cur.rowcount
    conn.commit()
    return count
def register(conn, manifest):
    count=0
    with open(manifest,encoding="utf-8-sig",newline="") as f:
        for row in csv.DictReader(f):
            wid=(row.get("word_id") or row.get("id") or "").strip()
            path=Path((row.get("image_path") or "").strip())
            if not wid or not path.is_file(): continue
            digest=hashlib.sha256(path.read_bytes()).hexdigest()
            cur=conn.execute("UPDATE jobs SET status='existing',image_path=?,image_sha256=?,updated_at=? WHERE word_id=? AND status IN ('pending','failed')",
                            (str(path.resolve()),digest,now(),wid))
            count+=cur.rowcount
    conn.commit()
    return count
def main():
    p=argparse.ArgumentParser()
    p.add_argument("--db",default="image_factory.sqlite")
    sub=p.add_subparsers(dest="command",required=True)
    sub.add_parser("status")
    a=sub.add_parser("import-nouns");a.add_argument("csv")
    a=sub.add_parser("register-existing");a.add_argument("manifest")
    a=sub.add_parser("export-pending");a.add_argument("output")
    args=p.parse_args()
    with connect(args.db) as conn:
        if args.command=="import-nouns":
            print("Imported:",ingest(conn,args.csv))
        elif args.command=="register-existing":
            print("Registered:",register(conn,args.manifest))
        elif args.command=="status":
            print(json.dumps(dict(conn.execute("SELECT status,COUNT(*) FROM jobs GROUP BY status")),ensure_ascii=False))
        elif args.command=="export-pending":
            rows=conn.execute("SELECT word_id,word,translation FROM jobs WHERE status='pending' ORDER BY word_id").fetchall()
            with open(args.output,"w",encoding="utf-8",newline="") as f:
                w=csv.writer(f);w.writerow(["word_id","word","translation"]);w.writerows(rows)
            print("Exported:",len(rows))
if __name__=="__main__":main()
