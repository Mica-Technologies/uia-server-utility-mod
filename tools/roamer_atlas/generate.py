"""Generate the roamer texture atlas.

The mod's RenderRoamer reads a single atlas at
``assets/sum/textures/entity/roamer_atlas.png``. The atlas stacks N 64x64
modern-format Minecraft skins vertically (so its size is 64 wide x N*64 tall).
Variant N occupies V-rows [N*64, (N+1)*64); the matching ``ModelRoamer`` shifts
each part's V offset by ``N*64`` to sample its slice.

This script:

1. Loads the six base skins from ``templates/``.
2. For each persona, recolors specific palette regions (skin, hair, shirt,
   pants) by exact-color pixel replacement, using the base template's detected
   palette as the source colors and a chosen palette entry as the target.
3. Pastes all 16 variants into one tall PNG and writes it to the assets dir.

Adding a persona: append to ``PERSONAS``. Update ``RenderRoamer.VARIANT_COUNT``
in Java to match. (The atlas height implies N, so a mismatch is detectable.)

Run from anywhere; paths are resolved relative to this file.
"""
from __future__ import annotations

from pathlib import Path
from PIL import Image

REPO = Path(__file__).resolve().parents[2]
TEMPLATE_DIR = Path(__file__).resolve().parent / "templates"
OUT_PATH = REPO / "src/main/resources/assets/sum/textures/entity/roamer_atlas.png"

# Modern 64x64 player skin sample points used to detect each template's
# palette. The roamer source skins use flat fills in each region, so a single
# pixel sample is enough to identify the source color for an exact-match swap.
SAMPLE_POINTS = {
    "skin":  (12, 12),  # face center
    "hair":  (12, 4),   # top of head
    "shirt": (24, 22),  # body front, center
    "pants": (5, 26),   # right leg front, center
}

# City-themed palette. Hand-picked to read distinctly at the small skin
# resolution; avoid colors that are too close to common skin/hair tones.
HAIR = {
    "black":      (0x1a, 0x1a, 0x1a),
    "dark_brown": (0x3b, 0x2f, 0x2f),
    "brown":      (0x66, 0x44, 0x22),
    "blonde":     (0xc4, 0x8e, 0x3b),
    "gray":       (0x9a, 0x9a, 0x9a),
    "auburn":     (0x8b, 0x44, 0x22),
}

SHIRT = {
    "navy":      (0x1a, 0x2e, 0x55),
    "charcoal":  (0x33, 0x33, 0x3a),
    "denim":     (0x4a, 0x6e, 0x9e),
    "olive":     (0x66, 0x7a, 0x44),
    "burgundy":  (0x6e, 0x22, 0x33),
    "teal":      (0x2e, 0x77, 0x88),
    "hi_vis":    (0xee, 0x88, 0x1a),
    "white":     (0xee, 0xee, 0xee),
    "black":     (0x22, 0x22, 0x22),
    "tan":       (0xc4, 0xa8, 0x88),
    "rust":      (0xb4, 0x55, 0x33),
    "lavender":  (0x88, 0x7a, 0xa8),
    "mustard":   (0xd4, 0xa8, 0x33),
}

PANTS = {
    "denim":       (0x3a, 0x4a, 0x66),
    "black_jean":  (0x22, 0x22, 0x2a),
    "khaki":       (0xa0, 0x88, 0x55),
    "gray_slack":  (0x55, 0x55, 0x60),
    "brown":       (0x55, 0x44, 0x33),
    "olive":       (0x44, 0x55, 0x33),
    "burgundy":    (0x55, 0x22, 0x2a),
}

# Each persona: (base_template_idx 0-5, hair_key|None, shirt_key|None, pants_key|None, label)
# A None means "keep the base template's color for that region". The first six
# entries reproduce the original skins unchanged, so the existing UUID-to-variant
# distribution stays continuous (entities that previously hashed to N=0..5 still
# render the same template).
PERSONAS = [
    (0, None,         None,        None,          "original_1"),
    (1, None,         None,        None,          "original_2"),
    (2, None,         None,        None,          "original_3"),
    (3, None,         None,        None,          "original_4"),
    (4, None,         None,        None,          "original_5"),
    (5, None,         None,        None,          "original_6"),
    (0, None,         "navy",      "gray_slack",  "office"),
    (1, None,         "charcoal",  "denim",       "casual"),
    (2, None,         "hi_vis",    "khaki",       "construction"),
    (3, None,         "black",     "black_jean",  "nightlife"),
    (4, "blonde",     "white",     "denim",       "tourist"),
    (5, None,         "olive",     "khaki",       "park"),
    (0, "blonde",     "burgundy",  "khaki",       "academic"),
    (1, "gray",       "tan",       "brown",       "elder"),
    (2, None,         "teal",      "gray_slack",  "tech"),
    (3, "auburn",     "rust",      "olive",       "artist"),
]


def detect_palette(img):
    return {region: img.getpixel(xy)[:3] for region, xy in SAMPLE_POINTS.items()}


def recolor(img, color_map):
    if not color_map:
        return img.copy()
    out = img.copy()
    px = out.load()
    w, h = out.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            key = (r, g, b)
            if key in color_map:
                nr, ng, nb = color_map[key]
                px[x, y] = (nr, ng, nb, a)
    return out


def build_variant(persona, base_imgs, base_palettes):
    base_idx, hair_key, shirt_key, pants_key, _label = persona
    base = base_imgs[base_idx]
    palette = base_palettes[base_idx]
    swaps = {}
    if hair_key is not None:
        swaps[palette["hair"]] = HAIR[hair_key]
    if shirt_key is not None:
        swaps[palette["shirt"]] = SHIRT[shirt_key]
    if pants_key is not None:
        swaps[palette["pants"]] = PANTS[pants_key]
    return recolor(base, swaps)


def main():
    base_imgs = [
        Image.open(TEMPLATE_DIR / f"roamer_{i + 1}.png").convert("RGBA")
        for i in range(6)
    ]
    base_palettes = [detect_palette(img) for img in base_imgs]

    n = len(PERSONAS)
    atlas = Image.new("RGBA", (64, 64 * n), (0, 0, 0, 0))
    for i, persona in enumerate(PERSONAS):
        variant = build_variant(persona, base_imgs, base_palettes)
        atlas.paste(variant, (0, i * 64))

    OUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(OUT_PATH, optimize=True)
    print(f"wrote {OUT_PATH} ({atlas.size[0]}x{atlas.size[1]}, {n} variants)")
    for i, (_, _, _, _, label) in enumerate(PERSONAS):
        print(f"  [{i:2d}] {label}")


if __name__ == "__main__":
    main()
