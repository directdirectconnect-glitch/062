"""Perceptual image similarity using a 64-bit difference hash (dHash)."""
from pathlib import Path
from PIL import Image, ImageOps

def dhash(path):
    """Return a 64-bit integer. EXIF orientation is normalized before hashing."""
    with Image.open(path) as im:
        im.load()
        gray = ImageOps.exif_transpose(im).convert("L").resize((9, 8), Image.Resampling.LANCZOS)
        pixels = list(gray.getdata())
    value = 0
    for y in range(8):
        for x in range(8):
            value = (value << 1) | int(pixels[y * 9 + x] > pixels[y * 9 + x + 1])
    return value

def distance(left, right):
    """Hamming distance between two 64-bit perceptual hashes."""
    if not (0 <= left < 2**64 and 0 <= right < 2**64):
        raise ValueError("Expected 64-bit unsigned hashes")
    return (left ^ right).bit_count()

def find_similar(path, candidates, threshold=5):
    """Find near matches; threshold is a heuristic, not proof of duplication."""
    if not 0 <= threshold <= 64:
        raise ValueError("threshold must be between 0 and 64")
    source = dhash(path)
    results = []
    for candidate in candidates:
        candidate = Path(candidate)
        if candidate.resolve() == Path(path).resolve():
            continue
        score = distance(source, dhash(candidate))
        if score <= threshold:
            results.append((str(candidate), score))
    return sorted(results, key=lambda item: (item[1], item[0]))
