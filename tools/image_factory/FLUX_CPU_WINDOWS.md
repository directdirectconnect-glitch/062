# Free offline FLUX.1-schnell CPU pilot on Windows 10

**Status:** CPU adapter and contract tests committed; no model downloaded or run on the user's PC. Do not call this an installation.

## Why this approach
User hardware: Ryzen 5 3600, 16 GB RAM, NVIDIA GT 730 2 GB, Windows 10 Pro x64, Python 3.12, ample D: space. GT 730 must not be used for FLUX inference. The stable-diffusion.cpp project documents FLUX.1-schnell GGUF and CPU execution; FastSD CPU documents a ~12 GB RAM FLUX GGUF configuration. 16 GB is borderline once Windows and other apps are included; memory exhaustion and slow generation are realistic risks.

Upstream: https://github.com/leejet/stable-diffusion.cpp/blob/master/docs/flux.md
FastSD CPU: https://github.com/rupeshs/fastsdcpu

## Isolated folder structure (no destructive changes)

D:\EnglishWords-FluxCPU\bin\sd-cli.exe
D:\EnglishWords-FluxCPU\models\flux1-schnell-q4_0.gguf
D:\EnglishWords-FluxCPU\models\ae.safetensors
D:\EnglishWords-FluxCPU\models\clip_l.safetensors
D:\EnglishWords-FluxCPU\models\t5xxl.gguf
D:\EnglishWords-FluxCPU\output\
D:\EnglishWords-FluxCPU\logs\

Exact names may differ; configure actual paths. Obtain authorized, compatible GGUF model, CLIP-L, T5-XXL and VAE from upstream instructions; check licenses and SHA256. Download an official/prebuilt **CPU** Windows sd-cli release matching these formats or compile the CPU build. Do not download arbitrary EXEs from unverified mirrors.

## Non-destructive PowerShell preflight

```powershell
$root = 'D:\EnglishWords-FluxCPU'
$paths = @(
  "$root\bin\sd-cli.exe",
  "$root\models\flux1-schnell-q4_0.gguf",
  "$root\models\ae.safetensors",
  "$root\models\clip_l.safetensors",
  "$root\models\t5xxl.gguf"
)
$paths | ForEach-Object { [pscustomobject]@{Path=$_;Exists=Test-Path -LiteralPath $_} }
Get-CimInstance Win32_OperatingSystem | Select-Object FreePhysicalMemory,TotalVisibleMemorySize
Get-PSDrive D | Select-Object Name,Free
```

## One-word actual inference (ONLY after files are installed)

```powershell
$root='D:\EnglishWords-FluxCPU'
New-Item -ItemType Directory -Force -Path "$root\output" | Out-Null
& "$root\bin\sd-cli.exe" --diffusion-model "$root\models\flux1-schnell-q4_0.gguf" --vae "$root\models\ae.safetensors" --clip_l "$root\models\clip_l.safetensors" --t5xxl "$root\models\t5xxl.gguf" -p 'Professional photorealistic photograph of one red apple on a wooden table, natural daylight, no text, no illustration' --cfg-scale 1 --sampling-method euler --steps 4 --clip-on-cpu -W 768 -H 768 -t 4 -o "$root\output\apple.png"
```

Check that installed sd-cli version accepts these flags (`--help`); CLI evolves. Measure elapsed time, peak RAM and inspect actual image before trying bridge and elephant. Do not schedule unattended production until memory and output quality pass.

## Python worker integration
Use `functools.partial(flux_cpu_backend.generate, executable=..., diffusion_model=..., vae=..., clip_l=..., t5xxl=...)` as the injected backend for `worker.run_batch`. The existing validator handles image integrity; semantic review is separate. Use one job at a time on 16 GB RAM.

## Completion gates
1. Windows confirms binary/model files and their hashes.
2. Real FLUX inference produces apple.png, valid dimensions and photographic subject.
3. Time and peak RAM recorded; no crash or swapping storm.
4. 3-word run and duplicate checks pass.
5. Only then enable automation with logs and restart recovery.

No paid API, no cloud GPU and no new hardware required for the attempt; practical throughput is unverified.
