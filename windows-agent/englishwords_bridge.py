"""English Words Windows GitHub bridge. Run locally under a dedicated Windows account.
Requires: Python 3.10+, gh CLI authenticated with repo issue read/write.
Never executes arbitrary text, shell commands, or remote scripts.
"""
import datetime as dt
import json
import os
from pathlib import Path
import sqlite3
import subprocess
import sys
import time

REPO = "directdirectconnect-glitch/062"
ISSUE = 19
ROOT = Path(os.environ.get("ENGLISHWORDS_LAB", r"C:\EnglishWords-Windows-Lab"))
DB = ROOT / "bridge" / "state.sqlite3"
LOG = ROOT / "bridge" / "bridge.log"
# Explicit command identifiers only; no arbitrary command execution.
PREFIX = "EW_AGENT_JOB_V1 "
AUTHORIZED_AUTHOR = "directdirectconnect-glitch"
ALLOWLIST = {"dictionary_review_1100": ROOT / "bridge" / "jobs" / "dictionary_review_1100.py"}

def now():
    return dt.datetime.now(dt.timezone.utc).isoformat()

def gh(*args):
    p = subprocess.run(["gh", *args], capture_output=True, text=True,
                       encoding="utf-8", timeout=45, check=True)
    return p.stdout

def post(message):
    gh("api", f"repos/{REPO}/issues/{ISSUE}/comments",
       "-f", f"body={message}")

def log(message):
    LOG.parent.mkdir(parents=True, exist_ok=True)
    with LOG.open("a", encoding="utf-8") as f:
        f.write(f"{now()} {message}\n")

def setup():
    DB.parent.mkdir(parents=True, exist_ok=True)
    with sqlite3.connect(DB) as db:
        db.execute("CREATE TABLE IF NOT EXISTS processed (id INTEGER PRIMARY KEY, status TEXT NOT NULL, updated TEXT NOT NULL)")
        db.execute("CREATE TABLE IF NOT EXISTS heartbeat (id INTEGER PRIMARY KEY CHECK(id=1), utc TEXT NOT NULL)")
        db.commit()

def mark(comment_id, status):
    with sqlite3.connect(DB) as db:
        db.execute("INSERT OR REPLACE INTO processed VALUES (?,?,?)", (comment_id,status,now()))
        db.commit()

def seen(comment_id):
    with sqlite3.connect(DB) as db:
        return db.execute("SELECT 1 FROM processed WHERE id=?", (comment_id,)).fetchone() is not None

def poll():
    # Read the latest comments only. Never treat free-form issue text as executable.
    raw = gh("api", f"repos/{REPO}/issues/{ISSUE}/comments?per_page=100")
    comments = json.loads(raw)
    for c in comments:
        cid = int(c["id"])
        body = c.get("body", "").strip()
        if c.get("user", {}).get("login") != AUTHORIZED_AUTHOR or not body.startswith(PREFIX) or seen(cid):
            continue
        mark(cid, "received")
        try:
            payload = json.loads(body[len(PREFIX):])
            if set(payload) != {"job", "request_id"}:
                raise ValueError("Unexpected fields")
            job = payload["job"]
            request_id = payload["request_id"]
            if not isinstance(request_id,str) or not (1 <= len(request_id) <= 64) or not request_id.replace("-","").replace("_","").isalnum():
                raise ValueError("Invalid request_id")
            script = ALLOWLIST.get(job)
            if script is None or not script.is_file():
                raise ValueError("Job not allowlisted or local script not installed")
            # Persist before launch: prevents duplicate execution after restart.
            mark(cid, "started")
            proc = subprocess.Popen([sys.executable, "-X", "utf8", str(script)],
                                    cwd=str(ROOT), stdout=subprocess.DEVNULL,
                                    stderr=subprocess.DEVNULL, creationflags=getattr(subprocess,"CREATE_NO_WINDOW",0))
            post(f"EW_AGENT_STARTED request_id={request_id} job={job} pid={proc.pid} utc={now()} comment_id={cid}")
            log(f"STARTED {cid} {job} pid={proc.pid}")
        except Exception as exc:
            mark(cid, "blocked")
            post(f"EW_AGENT_BLOCKED comment_id={cid} utc={now()} reason={type(exc).__name__}: {str(exc)[:250]}")
            log(f"BLOCKED {cid} {exc}")

def main():
    setup()
    log("AGENT_BOOT")
    while True:
        try:
            with sqlite3.connect(DB) as db:
                db.execute("INSERT OR REPLACE INTO heartbeat VALUES (1,?)", (now(),))
                db.commit()
            poll()
        except Exception as exc:
            log(f"POLL_ERROR {type(exc).__name__}: {exc}")
        time.sleep(60)

if __name__ == "__main__":
    main()
