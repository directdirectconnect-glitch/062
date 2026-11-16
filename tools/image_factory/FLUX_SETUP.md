# English Words: FLUX.1 schnell + ComfyUI backend

Status: adapter implemented; no physical FLUX generation or GPU test confirmed.

## Prerequisites

1. Windows or Linux host with ComfyUI installed and listening only on 127.0.0.1:8188.
2. FLUX.1 schnell model weights and required text encoders/VAE downloaded from an authorized source and installed according to the selected ComfyUI FLUX workflow.
3. A functioning ComfyUI **API-format** workflow exported from ComfyUI, including a SaveImage node (not just the UI-format graph).
4. Record the node ID of the positive text prompt node with a string `inputs.text`.
5. Python with Pillow installed for `validate_image`.

Model weights, GPU drivers, ComfyUI, and the workflow are NOT installed by this repository adapter.

## Call from the existing worker

```python
from functools import partial
from tools.image_factory.flux_comfy_backend import generate
from tools.image_factory.validate import validate_image
from tools.image_factory.worker import run_batch

backend = partial(
    generate,
    workflow_path=r"C:\EnglishWords-Windows-Lab\flux_schnell_api.json",
    prompt_node_id="6",  # Replace with actual positive prompt node ID
    server="http://127.0.0.1:8188",
    timeout=600,
)
result = run_batch(
    db_path=r"C:\EnglishWords-Windows-Lab\image_factory.sqlite",
    output_dir=r"C:\EnglishWords-Windows-Lab\generated",
    backend=backend,
    validator=validate_image,
    limit=3,
    perceptual_threshold=5,
)
print(result)
```

The prompt node ID is an EXAMPLE only; inspect the exported workflow. No successful
FLUX generation is claimed until the actual model and API-format workflow are tested.

## Validation and acceptance

Run `python -m unittest discover -s tools/image_factory -p 'test_*.py' -v`.
For an actual hardware pilot, generate `apple`, `bridge`, `elephant`,
record GPU model/VRAM, runtime per image, dimensions, SHA-256, and manual
semantic/photorealism evaluation. A green mocked API test is not proof of FLUX inference.

## Safety

The adapter accepts only loopback HTTP endpoints. Keep ComfyUI API private,
do not expose port 8188 to the internet. If using a separate remote host,
use a secure authenticated tunnel to local loopback rather than disabling
the endpoint restriction. No automatic deletion of generated assets.
