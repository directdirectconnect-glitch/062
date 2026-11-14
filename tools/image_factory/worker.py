"""GPU-independent worker. Backend is a callable(prompt, output_path)."""
import hashlib
import sqlite3
from datetime import datetime, timezone
from pathlib import Path

def prompt(word):
    if not word.strip(): raise ValueError("Empty word")
    return (f"Professional documentary photograph of {word!r}; one clear subject, "
            "realistic lighting, natural colors, accurate proportions, sharp detail. "
            "No cartoons, illustrations, CGI, text, collage or watermarks.")

def run_batch(db_path, output_dir, backend, limit=10, max_attempts=3, validator=None):
    """Run bounded jobs; validator(path) must reject damaged/low-quality images."""
    if limit < 1 or max_attempts < 1: raise ValueError("Invalid limits")
    if validator is None: raise ValueError("A real image validator is required")
    out=Path(output_dir);out.mkdir(parents=True,exist_ok=True)
    summary={"attempted":0,"generated":0,"failed":0}
    with sqlite3.connect(db_path) as db:
        jobs=db.execute("SELECT word_id,word FROM jobs WHERE status='pending' AND attempts<? ORDER BY word_id LIMIT ?",(max_attempts,limit)).fetchall()
        for wid,word in jobs:
            now=lambda:datetime.now(timezone.utc).isoformat()
            db.execute("UPDATE jobs SET status='running',attempts=attempts+1,updated_at=? WHERE word_id=?",(now(),wid))
            db.commit();summary["attempted"]+=1
            stem=hashlib.sha256(str(wid).encode()).hexdigest()[:20]
            tmp=out/(stem+".partial.png");dst=out/(stem+".png")
            try:
                tmp.unlink(missing_ok=True)
                backend(prompt(word),tmp)
                if not tmp.is_file(): raise ValueError("Backend produced no file")
                validator(tmp)
                digest=hashlib.sha256(tmp.read_bytes()).hexdigest()
                if db.execute("SELECT 1 FROM jobs WHERE image_sha256=? AND word_id<>?",(digest,wid)).fetchone():
                    raise ValueError("Exact duplicate image")
                tmp.replace(dst)
                db.execute("UPDATE jobs SET status='generated',image_path=?,image_sha256=?,error=NULL,updated_at=? WHERE word_id=?",(str(dst.resolve()),digest,now(),wid))
                summary["generated"]+=1
            except Exception as exc:
                tmp.unlink(missing_ok=True)
                attempts=db.execute("SELECT attempts FROM jobs WHERE word_id=?",(wid,)).fetchone()[0]
                status="failed" if attempts>=max_attempts else "pending"
                db.execute("UPDATE jobs SET status=?,error=?,updated_at=? WHERE word_id=?",(status,str(exc)[:500],now(),wid))
                summary["failed"]+=1
            db.commit()
    return summary

def recover_interrupted(db_path):
    with sqlite3.connect(db_path) as db:
        cur=db.execute("UPDATE jobs SET status='pending' WHERE status='running'")
        db.commit();return cur.rowcount
