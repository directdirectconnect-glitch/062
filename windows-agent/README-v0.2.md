# Windows bridge v0.2 deployment notes

The agent is **not installed or running** merely because these files are committed.

1. On the Windows PC, install Python 3.10+ and GitHub CLI; authenticate gh with read/write access to repo issues.
2. Copy bridge.py to C:\EnglishWords-Windows-Lab\bridge\englishwords_bridge.py and job_runner.py to C:\EnglishWords-Windows-Lab\bridge\job_runner.py.
3. Install and review an explicitly allowlisted job at C:\EnglishWords-Windows-Lab\bridge\jobs\dictionary_review_1100.py. Do not use untrusted issue text as a shell command.
4. Run the bridge in a dedicated low-privilege Windows account; use Task Scheduler to start at boot and restart on failure. Avoid running multiple instances (a single-instance guard still needs implementation).
5. Test a harmless allowlisted diagnostic job before the dictionary job; validate STARTED, COMPLETED, logs, duplicate suppression and restart recovery.

Command syntax (only from repository owner):
EW_AGENT_JOB_V1 {"job":"dictionary_review_1100","request_id":"review-1100-001"}

Status:
- 60-second polling, SQLite durable request claims, completion status reporting and retry implemented.
- Not yet installed on Windows or tested end-to-end.
- No automatic provisioning, no local dictionary-review job shipped.
- Security hardening still required: verify local script integrity, enforce a single instance, handle stuck/aborted runners, limit GitHub API polling, avoid untrusted log output in comments.
