"""Generate the texture for SUM's storm-shelter sign block.

Converts the real-world "SEVERE WEATHER SHELTER AREA" sign artwork
(severe_weather_shelter_area_sign.webp, 1500x2000, 3:4) into a square 256x256
PNG. Minecraft wants square texture files; the block model maps the full
texture onto a 12x16 face, which stretches it back out to the sign's true
3:4 proportions in-world.

Run from anywhere; paths are resolved relative to this file.
"""
from __future__ import annotations

from pathlib import Path
from PIL import Image

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]
OUT_DIR = REPO / "src/main/resources/assets/sum/textures/blocks"
OUT_DIR.mkdir(parents=True, exist_ok=True)

SOURCE = HERE / "severe_weather_shelter_area_sign.webp"
SIZE = 256


def make_texture() -> Image.Image:
    img = Image.open(SOURCE).convert("RGBA")
    # Non-uniform resize: squish 3:4 down to square. The block model's UV
    # mapping onto a 12x16 face undoes this in-world.
    return img.resize((SIZE, SIZE), Image.LANCZOS)


def main():
    make_texture().save(OUT_DIR / "storm_shelter_sign.png")
    print(f"wrote storm_shelter_sign.png to {OUT_DIR}")


if __name__ == "__main__":
    main()
