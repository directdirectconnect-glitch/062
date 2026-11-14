"""Offline contract tests for the real OpenAI image API adapter."""
import base64
import importlib.util
import io
import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from PIL import Image

spec = importlib.util.spec_from_file_location("image_factory_openai_backend", Path(__file__).with_name("openai_backend.py"))
backend = importlib.util.module_from_spec(spec)
spec.loader.exec_module(backend)

class OpenAIBackendTests(unittest.TestCase):
    def test_missing_credentials_fail_before_network(self):
        with tempfile.TemporaryDirectory() as td, patch.dict(os.environ, {}, clear=True):
            with self.assertRaisesRegex(RuntimeError, "OPENAI_API_KEY"):
                backend.generate("photograph of an apple", Path(td) / "apple.png")

    def test_api_response_writes_actual_png(self):
        buffer = io.BytesIO()
        Image.new("RGB", (8, 8), (25, 100, 170)).save(buffer, format="PNG")
        payload = {"data": [{"b64_json": base64.b64encode(buffer.getvalue()).decode("ascii")}]}
        class FakeResponse:
            def __enter__(self): return self
            def __exit__(self, *args): pass
            def read(self, *args): return json.dumps(payload).encode()
        with tempfile.TemporaryDirectory() as td, patch.dict(os.environ, {"OPENAI_API_KEY": "test-only"}):
            with patch.object(backend.request, "urlopen", return_value=FakeResponse()) as network:
                out = Path(td) / "apple.png"
                backend.generate("photograph of an apple", out)
                self.assertEqual(out.read_bytes(), buffer.getvalue())
                request = network.call_args.args[0]
                self.assertEqual(json.loads(request.data)["model"], "gpt-image-1")
                self.assertIn("Bearer test-only", request.headers["Authorization"])

    def test_invalid_image_response_rejected(self):
        payload = {"data": [{"b64_json": base64.b64encode(b"not-png").decode("ascii")}]}
        class FakeResponse:
            def __enter__(self): return self
            def __exit__(self, *args): pass
            def read(self, *args): return json.dumps(payload).encode()
        with tempfile.TemporaryDirectory() as td, patch.dict(os.environ, {"OPENAI_API_KEY": "test-only"}):
            with patch.object(backend.request, "urlopen", return_value=FakeResponse()):
                out = Path(td) / "bad.png"
                with self.assertRaisesRegex(ValueError, "not PNG"):
                    backend.generate("test", out)
                self.assertFalse(out.exists())

if __name__ == "__main__":
    unittest.main()
