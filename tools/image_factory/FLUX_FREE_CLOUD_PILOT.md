# FLUX.1 schnell: free cloud GPU pilot (no Windows GPU upgrade)

Status: PILOT PLAN ONLY. No cloud GPU allocation or FLUX inference has been performed.

## Goal
Generate 3 real photorealistic images for apple, bridge, elephant using FLUX.1 schnell on a permitted free GPU session. Inspect semantic correctness and image quality; only then integrate production automation.

## Available platforms and limits (verify again at execution)
- Hugging Face ZeroGPU: existing public Gradio Spaces can be used within a limited daily quota. It is not a dedicated always-on server; a free personal account may host a limited number of ZeroGPU Spaces if eligible. A Space's UI/API is NOT the same as the ComfyUI /prompt endpoint.
- Google Colab free: interactive notebook experiments, GPU availability not guaranteed; do not use it as an unattended distributed worker, remote-control tunnel, or web UI proxy. Follow Colab usage policies.

## Execution sequence
1. Obtain an authorized interactive free GPU session and confirm actual GPU, VRAM, quota, and access terms.
2. Run actual FLUX.1 schnell inference with official/open-licensed model weights in that environment; do not substitute synthetic PNG or ChatGPT image generation.
3. Prompts: photorealistic documentary photograph of one red apple on a neutral tabletop; photorealistic documentary photograph of a stone bridge over a river; photorealistic wildlife photograph of an elephant in natural savanna. No text, cartoon, illustration, collage, watermark.
4. Save three generated PNGs and a JSON report with engine/model, GPU, prompt, dimensions, generation time, SHA-256, validation result and manual semantic review. Do not claim PASS without the files.
5. Return files to English Words' local Windows control computer by a user-authorized secure transfer; validate and archive. Existing local `worker.py` supports injected backends, but `flux_comfy_backend.py` specifically requires loopback ComfyUI. A cloud Gradio endpoint requires a separate compatible adapter, not an unsafe open ComfyUI port.
6. Check issue #22 Windows bridge and Task Scheduler execution with real logs before asserting autonomous control. Windows PC GT 730 2GB is for orchestration only, not FLUX inference.

## Acceptance
PASS only when 3 real FLUX-generated images are stored and inspected, hashes unique, dimensions >=768px, no image corruption, correct subject and photographic appearance. Otherwise report BLOCKED or FAIL with evidence.

## Production decision
Free cloud GPU quotas are insufficient for guaranteed unattended 24/7 generation. Use free cloud solely for an initial quality/compatibility pilot. Later choose dedicated GPU hardware or a paid GPU service if 24/7 production is required.
