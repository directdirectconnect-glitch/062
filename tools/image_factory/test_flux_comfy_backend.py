"""Network-free integration contract tests for FLUX/ComfyUI backend."""
import importlib.util
import io
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from PIL import Image

spec = importlib.util.spec_from_file_location("flux_comfy_backend", Path(__file__).with_name("flux_comfy_backend.py"))
backend = importlib.util.module_from_spec(spec)
spec.loader.exec_module(backend)

class FakeResponse:
    def __init__(self, data): self.data = data
    def __enter__(self): return self
    def __exit__(self, *args): pass
    def read(self, *args): return self.data

class FluxComfyTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.workflow = self.root / "workflow.json"
        self.workflow.write_text(json.dumps({"6": {"class_type": "CLIPTextEncode", "inputs": {"text": "old"}}}), encoding="utf-8")

    def test_comfy_workflow_produces_real_png(self):
        png = io.BytesIO()
        Image.new("RGB", (800, 800), (10, 100, 200)).save(png, format="PNG")
        calls = []
        def urlopen(req, timeout=30):
            url = req.full_url if hasattr(req, "full_url") else req
            calls.append(url)
            if url.endswith("/prompt"):
                body = json.loads(req.data)
                self.assertEqual(body["prompt"]["6"]["inputs"]["text"], "Photograph of a fox")
                return FakeResponse(b'{"prompt_id":"job-1"}')
            if "/history/" in url:
                return FakeResponse(json.dumps({"job-1": {"status": {"completed": True}, "outputs": {"9": {"images": [{"filename": "fox.png", "subfolder": "", "type": "output"}]}}}}).encode())
            if "/view?" in url:
                return FakeResponse(png.getvalue())
            raise AssertionError(url)
        with patch.object(backend.request, "urlopen", side_effect=urlopen):
            output = self.root / "fox.png"
            backend.generate("Photograph of a fox", output, workflow_path=self.workflow, prompt_node_id="6", poll_interval=0)
        self.assertEqual(output.read_bytes(), png.getvalue())
        self.assertEqual(len(calls), 3)

    def test_remote_endpoint_rejected(self):
        with self.assertRaisesRegex(ValueError, "loopback"):
            backend.generate("fox", self.root / "fox.png", workflow_path=self.workflow,
                             prompt_node_id="6", server="https://example.com")

    def test_invalid_workflow_rejected(self):
        with self.assertRaisesRegex(ValueError, "Missing prompt node"):
            backend.generate("fox", self.root / "fox.png", workflow_path=self.workflow, prompt_node_id="missing")

    def test_comfy_error_is_not_pass(self):
        def urlopen(req, timeout=30):
            url = req.full_url if hasattr(req, "full_url") else req
            if url.endswith("/prompt"):
                return FakeResponse(b'{"prompt_id":"job-1"}')
            return FakeResponse(b'{"job-1":{"status":{"status_str":"error"}}}')
        with patch.object(backend.request, "urlopen", side_effect=urlopen):
            with self.assertRaisesRegex(RuntimeError, "execution failed"):
                backend.generate("fox", self.root / "fox.png", workflow_path=self.workflow,
                                 prompt_node_id="6", poll_interval=0)
        self.assertFalse((self.root / "fox.png").exists())

if __name__ == "__main__":
    unittest.main()
