"""Real OpenAI Images API backend for Image Factory worker.

Requires OPENAI_API_KEY in the environment. No secrets are stored in source.
This module is an adapter, not a locally installed GPU model.
"""
import base64
import json
import os
from pathlib import Path
from urllib import error, request

API_URL = "https://api.openai.com/v1/images/generations"

def generate(prompt, output_path, *, model="gpt-image-1", size="1536x1024", timeout=180):
    key = os.environ.get("OPENAI_API_KEY")
    if not key:
        raise RuntimeError("OPENAI_API_KEY not configured; real image generation not attempted")
    if not isinstance(prompt, str) or not prompt.strip():
        raise ValueError("Prompt must be nonempty")
    payload = json.dumps({
        "model": model, "prompt": prompt, "size": size,
        "n": 1, "output_format": "png",
    }).encode("utf-8")
    req = request.Request(API_URL, data=payload, headers={
        "Authorization": "Bearer " + key,
        "Content-Type": "application/json",
    }, method="POST")
    try:
        with request.urlopen(req, timeout=timeout) as response:
            body = json.load(response)
    except error.HTTPError as exc:
        raise RuntimeError("Image API HTTP " + str(exc.code)) from exc
    encoded = body.get("data", [{}])[0].get("b64_json")
    if not encoded:
        raise RuntimeError("Image API did not return b64_json")
    image_bytes = base64.b64decode(encoded, validate=True)
    if not image_bytes.startswith(b"\x89PNG\r\n\x1a\n"):
        raise ValueError("Image API response is not PNG")
    path = Path(output_path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(image_bytes)
    return path
