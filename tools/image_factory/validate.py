"""Strict raster validation for generated photos. Requires Pillow."""
from PIL import Image, ImageStat, UnidentifiedImageError

def validate_image(path, minimum=768):
    with Image.open(path) as im:
        im.verify()
    with Image.open(path) as im:
        if im.format not in ("PNG","JPEG","WEBP"):
            raise ValueError("Unsupported format")
        if min(im.size)<minimum:
            raise ValueError(f"Image too small: {im.size}")
        rgb=im.convert("RGB")
        stat=ImageStat.Stat(rgb.resize((128,128)))
        if max(stat.stddev)<8:
            raise ValueError("Nearly uniform image")
        return {"width":im.width,"height":im.height,"format":im.format}

# This validates file integrity and simple image statistics only.
# It does NOT establish semantic correctness, photorealism, or perceptual uniqueness.
