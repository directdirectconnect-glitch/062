"""ComfyUI FLUX.1-schnell adapter for Image Factory.

Uses a user-exported ComfyUI API-format workflow JSON (not UI workflow).
The workflow must already have the FLUX checkpoints, text-encoder and VAE
configured in ComfyUI. No model weights are downloaded by this module.
Only loopback ComfyUI is allowed to prevent remote job/data exfiltration.
"""
import copy
import json
import time
import uuid
from pathlib import Path
from urllib import parse, request

def generate(prompt, output_path, *, workflow_path, prompt_node_id,
             server="http://127.0.0.1:8188", timeout=300, poll_interval=2):
    parsed = parse.urlsplit(server)
    if parsed.scheme != "http" or parsed.hostname not in ("127.0.0.1", "localhost", "::1") or parsed.username or parsed.password or parsed.query or parsed.fragment:
        raise ValueError("ComfyUI endpoint must be loopback HTTP")
    if not isinstance(prompt, str) or not prompt.strip():
        raise ValueError("Prompt must be nonempty")
    workflow = json.loads(Path(workflow_path).read_text(encoding="utf-8"))
    if not isinstance(workflow, dict) or str(prompt_node_id) not in workflow:
        raise ValueError("Missing prompt node in API workflow")
    workflow = copy.deepcopy(workflow)
    inputs = workflow[str(prompt_node_id)].get("inputs")
    if not isinstance(inputs, dict) or not isinstance(inputs.get("text"), str):
        raise ValueError("Prompt node must have a text input")
    inputs["text"] = prompt
    client_id = str(uuid.uuid4())
    root = server.rstrip("/")
    payload = json.dumps({"prompt": workflow, "client_id": client_id}).encode()
    req = request.Request(root + "/prompt", data=payload,
                          headers={"Content-Type": "application/json"}, method="POST")
    with request.urlopen(req, timeout=30) as response:
        submitted = json.load(response)
    job_id = submitted.get("prompt_id")
    if not isinstance(job_id, str) or not job_id:
        raise RuntimeError("ComfyUI did not return prompt_id")
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        with request.urlopen(root + "/history/" + parse.quote(job_id, safe=""), timeout=30) as response:
            history = json.load(response)
        job = history.get(job_id)
        if job:
            if job.get("status", {}).get("status_str") == "error":
                raise RuntimeError("ComfyUI execution failed")
            images = [img for node in job.get("outputs", {}).values()
                      for img in node.get("images", []) if img.get("type") == "output"]
            if images:
                selected = images[0]
                query = parse.urlencode({key: selected.get(key, "") for key in ("filename", "subfolder", "type")})
                with request.urlopen(root + "/view?" + query, timeout=60) as response:
                    data = response.read()
                if not data.startswith(b"\x89PNG\r\n\x1a\n"):
                    raise ValueError("ComfyUI did not return PNG")
                output = Path(output_path)
                output.parent.mkdir(parents=True, exist_ok=True)
                output.write_bytes(data)
                return output
            if job.get("status", {}).get("completed"):
                raise RuntimeError("ComfyUI completed without output image")
        time.sleep(poll_interval)
    raise TimeoutError("ComfyUI generation timed out")
