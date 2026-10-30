from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont


def read_cube(path: Path) -> tuple[int, np.ndarray]:
    size = 0
    values = []
    with path.open("r", encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            if line.upper().startswith("LUT_3D_SIZE"):
                size = int(line.split()[-1])
                continue
            parts = line.split()
            if len(parts) == 3:
                values.append(tuple(float(part) for part in parts))
    if not size or len(values) != size**3:
        raise ValueError(f"Invalid 3D LUT: {path}")
    # .cube stores red fastest, then green, then blue. Transpose to [r, g, b].
    lut = np.asarray(values, dtype=np.float32).reshape(size, size, size, 3)
    return size, lut.transpose(2, 1, 0, 3)


def apply_lut(image: Image.Image, lut: np.ndarray) -> Image.Image:
    rgb = np.asarray(image.convert("RGB"), dtype=np.float32) / 255.0
    size = lut.shape[0]
    scale = size - 1
    q = rgb * scale
    low = np.floor(q).astype(np.int32)
    high = np.minimum(low + 1, size - 1)
    frac = q - low

    out = np.zeros_like(rgb)
    for r in (0, 1):
        wr = frac[..., 0] if r else 1.0 - frac[..., 0]
        ri = high[..., 0] if r else low[..., 0]
        for g in (0, 1):
            wg = frac[..., 1] if g else 1.0 - frac[..., 1]
            gi = high[..., 1] if g else low[..., 1]
            for b in (0, 1):
                wb = frac[..., 2] if b else 1.0 - frac[..., 2]
                bi = high[..., 2] if b else low[..., 2]
                out += (wr * wg * wb)[..., None] * lut[ri, gi, bi]
    return Image.fromarray(
        np.clip(out * 255.0 + 0.5, 0, 255).astype(np.uint8), "RGB"
    )


def label(image: Image.Image, text: str) -> Image.Image:
    framed = Image.new("RGB", (image.width, image.height + 42), "#f5f3ed")
    framed.paste(image.convert("RGB"), (0, 42))
    draw = ImageDraw.Draw(framed)
    font = ImageFont.load_default(size=17)
    draw.text((12, 12), text, fill="#17201e", font=font)
    return framed


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--lut17", type=Path, required=True)
    parser.add_argument("--lut33", type=Path, required=True)
    parser.add_argument("--label17", default="spektrafilm 17^3")
    parser.add_argument("--label33", default="spektrafilm 33^3")
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()

    source = Image.open(args.input).convert("RGB")
    source.thumbnail((720, 540), Image.Resampling.LANCZOS)
    results = [label(source, "原图 / SOURCE")]
    for path, name in ((args.lut17, args.label17), (args.lut33, args.label33)):
        _, lut = read_cube(path)
        results.append(label(apply_lut(source, lut), name))

    sheet = Image.new(
        "RGB",
        (sum(item.width for item in results) + 24, results[0].height),
        "#f5f3ed",
    )
    x = 0
    for item in results:
        sheet.paste(item, (x, 0))
        x += item.width + 12
    args.out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(args.out, quality=92, optimize=True)
    print(args.out)


if __name__ == "__main__":
    main()
