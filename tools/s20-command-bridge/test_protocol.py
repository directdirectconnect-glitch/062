"""Offline safety checks for the data-only GitHub command channel."""
import json
import unittest
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).parent

def validate_job(job):
    allowed = {'schema_version', 'sequence', 'target', 'operation', 'expires_utc'}
    if not isinstance(job, dict) or set(job) != allowed:
        raise ValueError('Invalid fields')
    if type(job['schema_version']) is not int or job['schema_version'] != 1:
        raise ValueError('Invalid version')
    if type(job['sequence']) is not int or not 1 <= job['sequence'] <= 2**31 - 1:
        raise ValueError('Invalid sequence')
    if job['target'] != 's20fe':
        raise ValueError('Invalid target')
    if job['operation'] not in ('inventory', 'smoke', 'full_regression'):
        raise ValueError('Not allowed')
    expires = datetime.fromisoformat(job['expires_utc'].replace('Z', '+00:00'))
    if expires.tzinfo is None or expires <= datetime(2026, 11, 11, tzinfo=timezone.utc):
        raise ValueError('Not future')
    return True

class ProtocolTests(unittest.TestCase):
    def setUp(self):
        self.job = json.loads((ROOT / 'queue.json').read_text())

    def test_initial_job(self):
        self.assertTrue(validate_job(self.job))
        self.assertEqual(self.job['operation'], 'inventory')

    def test_reject_arbitrary_command(self):
        for name in ['powershell', 'shell', 'exec', 'script', 'install_apk', 'microphone']:
            with self.subTest(name=name), self.assertRaises(ValueError):
                validate_job({**self.job, 'operation': name})

    def test_reject_surprise_fields(self):
        for field in ('args', 'url', 'path', 'command', 'token'):
            with self.subTest(field=field), self.assertRaises(ValueError):
                validate_job({**self.job, field: 'echo hi'})

    def test_reject_wrong_device(self):
        with self.assertRaises(ValueError):
            validate_job({**self.job, 'target': 'fold'})

    def test_scripts_are_ascii_and_avoid_remote_execution(self):
        for f in ROOT.glob('*.ps1'):
            self.assertTrue(f.read_bytes().isascii(), f'PS5.1: {f.name}')
        script = (ROOT / 'Run-S20-CommandAgent.ps1').read_text()
        for forbidden in ('Invoke-Expression','DownloadString','iex ','Invoke-WebRequest','ScriptBlock::Create'):
            self.assertNotIn(forbidden, script)
        self.assertIn("'SM-G780G'", script)
        self.assertIn("'com.example.english1000.s20test'", script)

if __name__ == '__main__':
    unittest.main()
