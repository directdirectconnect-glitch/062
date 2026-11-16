"""Contract tests for CPU FLUX adapter: no actual model inference is claimed."""
import importlib.util
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from subprocess import CalledProcessError

spec=importlib.util.spec_from_file_location("flux_cpu_backend",Path(__file__).with_name("flux_cpu_backend.py"))
module=importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class FluxCpuTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name)
        self.files=[self.root / x for x in ("sd-cli.exe","flux.gguf","ae.safetensors","clip.safetensors","t5.gguf")]
        for p in self.files: p.write_bytes(b"test")
        self.target=self.root / "apple.png"
    def call(self,**kw):
        return module.generate("photograph of an apple",self.target,
            executable=self.files[0],diffusion_model=self.files[1],
            vae=self.files[2],clip_l=self.files[3],t5xxl=self.files[4],**kw)
    def test_command_contains_cpu_flux_parameters(self):
        def fake_run(command,**kwargs):
            self.assertIn("--clip-on-cpu",command)
            self.assertEqual(command[command.index("--steps")+1],"4")
            self.assertEqual(command[command.index("-p")+1],"photograph of an apple")
            self.target.write_bytes(b"\x89PNG\r\n\x1a\n"+b"abc")
        with patch.object(module.subprocess,"run",side_effect=fake_run):
            self.call()
        self.assertTrue(self.target.exists())
    def test_missing_model_blocks_execution(self):
        self.files[1].unlink()
        with patch.object(module.subprocess,"run") as run:
            with self.assertRaises(FileNotFoundError): self.call()
            run.assert_not_called()
    def test_failed_inference_cleans_partial(self):
        def fail(*args,**kwargs):
            self.target.write_bytes(b"partial")
            raise CalledProcessError(1,"sd-cli")
        with patch.object(module.subprocess,"run",side_effect=fail):
            with self.assertRaises(CalledProcessError): self.call()
        self.assertFalse(self.target.exists())
    def test_fake_png_rejected(self):
        with patch.object(module.subprocess,"run",side_effect=lambda *a,**k:self.target.write_bytes(b"not an image")):
            with self.assertRaisesRegex(RuntimeError,"not PNG"): self.call()
        self.assertFalse(self.target.exists())

if __name__=="__main__": unittest.main()
