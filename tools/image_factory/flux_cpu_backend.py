"""CPU-only FLUX.1-schnell adapter using stable-diffusion.cpp sd-cli.exe.

Requires a separately installed CPU build and authorized local model weights.
No shell execution, no network, no GPU requirement. Inject into worker.run_batch.
"""
import os
import subprocess
from pathlib import Path

def generate(prompt, output_path, *, executable, diffusion_model, vae, clip_l, t5xxl,
             width=768, height=768, steps=4, timeout=3600, threads=4):
    if not isinstance(prompt, str) or not prompt.strip():
        raise ValueError("Prompt must be nonempty")
    if width < 256 or height < 256 or width % 64 or height % 64:
        raise ValueError("Image dimensions must be >=256 and divisible by 64")
    if not 1 <= steps <= 8 or not 1 <= threads <= 64:
        raise ValueError("Invalid FLUX CPU settings")
    paths = [Path(p) for p in (executable, diffusion_model, vae, clip_l, t5xxl)]
    for path in paths:
        if not path.is_file():
            raise FileNotFoundError(str(path))
    target = Path(output_path)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.unlink(missing_ok=True)
    command = [str(paths[0]), "--diffusion-model", str(paths[1]),
               "--vae", str(paths[2]), "--clip_l", str(paths[3]),
               "--t5xxl", str(paths[4]), "-p", prompt,
               "--cfg-scale", "1.0", "--sampling-method", "euler",
               "--steps", str(steps), "--clip-on-cpu",
               "-W", str(width), "-H", str(height),
               "-t", str(threads), "-o", str(target)]
    env = os.environ.copy()
    env["CUDA_VISIBLE_DEVICES"] = ""
    try:
        subprocess.run(command, check=True, timeout=timeout, env=env,
                       capture_output=True, text=True)
        if not target.is_file() or target.stat().st_size < 8:
            raise RuntimeError("sd-cli produced no PNG")
        with target.open("rb") as f:
            if f.read(8) != b"\x89PNG\r\n\x1a\n":
                raise RuntimeError("sd-cli output is not PNG")
    except Exception:
        target.unlink(missing_ok=True)
        raise
    return target
