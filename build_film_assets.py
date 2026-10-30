from __future__ import annotations

import argparse
import base64
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageOps

from preview_film_luts import apply_lut, read_cube


LUT_ROOT = Path(r"E:\Codex\work\spektrafilm\miaoji-luts")
ASSET_ROOT = Path(r"E:\Codex\work\miaoji-paint\android\app\src\main\assets")
DEFAULT_SOURCE = Path(
    r"E:\Codex\work\miaoji-paint\effect-previews-generated\source-base-cn.png"
)

STOCKS = [
    {
        "id": "portra",
        "name": "日光人像",
        "stock": "Portra 400 · Endura",
        "desc": "柔和肤色 · 奶油高光 · 日常人像",
        "bundle": "Portra 400 Endura",
    },
    {
        "id": "gold",
        "name": "金色黄昏",
        "stock": "Gold 200 · Ultra Endura",
        "desc": "暖金日光 · 胶片颗粒感 · 街头",
        "bundle": "Gold 200 Ultra",
    },
    {
        "id": "ektar",
        "name": "高彩正片",
        "stock": "Ektar 100 · Supra Endura",
        "desc": "浓郁红蓝 · 干净层次 · 风景",
        "bundle": "Ektar 100 Supra",
    },
    {
        "id": "pro400h",
        "name": "清透青绿",
        "stock": "Pro 400H · Crystal Archive",
        "desc": "薄荷青 · 柔粉高光 · 日系空气感",
        "bundle": "Pro 400H Crystal",
    },
    {
        "id": "vision",
        "name": "夜色电影",
        "stock": "Vision3 500T · Kodak 2383",
        "desc": "冷调夜景 · 电影高光 · 青橙分离",
        "bundle": "Vision3 500T 2383",
    },
    {
        "id": "ultramax",
        "name": "街头快照",
        "stock": "UltraMax 400 · Supra Endura",
        "desc": "鲜活色块 · 粗粝颗粒 · 日常街拍",
        "bundle": "UltraMax 400 Supra",
    },
    {
        "id": "c200",
        "name": "旧日胶卷",
        "stock": "C200 · Crystal Archive",
        "desc": "低饱和青绿 · 轻褪色 · 生活记录",
        "bundle": "C200 Crystal",
    },
    {
        "id": "portra160",
        "name": "柔光肤调",
        "stock": "Portra 160 · Endura Premier",
        "desc": "细腻低反差 · 清透肤色 · 棚拍人像",
        "bundle": "Portra 160 Premier",
    },
]


def cube_path(bundle: str) -> Path:
    matches = sorted((LUT_ROOT / bundle).glob("*.cube"))
    if len(matches) != 1:
        raise ValueError(f"Expected one cube in {LUT_ROOT / bundle}, found {len(matches)}")
    return matches[0]


def build_lut_js() -> dict[str, bytes]:
    luts: dict[str, str] = {}
    size = 0
    for stock in STOCKS:
        stock_size, lut = read_cube(cube_path(stock["bundle"]))
        if size and stock_size != size:
            raise ValueError("All film LUTs must use the same grid size")
        size = stock_size
        # Store in the Adobe .cube order (red index fastest) for a direct
        # ((b * size + g) * size + r) * 3 lookup in the WebView.
        raw = np.clip(lut.transpose(2, 1, 0, 3) * 255.0 + 0.5, 0, 255).astype(np.uint8)
        luts[stock["id"]] = base64.b64encode(raw.tobytes()).decode("ascii")

    payload = {
        "size": size,
        "stocks": luts,
        "attribution": (
            "Derived from spektrafilm by Andrea Volpato; "
            "https://github.com/andreavolpato/spektrafilm; CC BY-SA 4.0."
        ),
    }
    script = "window.MIAOJI_FILM_LUTS=" + json.dumps(payload, separators=(",", ":")) + ";\n"
    return {"film-luts.js": script.encode("utf-8")}


def sample_previews(source_path: Path) -> dict[str, bytes]:
    source = Image.open(source_path).convert("RGB")
    source = ImageOps.fit(
        source,
        (720, 720),
        method=Image.Resampling.LANCZOS,
        centering=(0.5, 0.43),
    )
    outputs: dict[str, bytes] = {}
    for stock in STOCKS:
        _, lut = read_cube(cube_path(stock["bundle"]))
        rendered = apply_lut(source, lut)
        from io import BytesIO

        buffer = BytesIO()
        rendered.save(buffer, "JPEG", quality=88, optimize=True, progressive=True)
        outputs[f"film-{stock['id']}.jpg"] = buffer.getvalue()
    return outputs


def contact_sheet(source_path: Path, output_path: Path) -> None:
    source = Image.open(source_path).convert("RGB")
    width, height = 360, 420
    gap = 12
    top = 54
    columns = 4
    rows = 2
    sheet = Image.new(
        "RGB",
        (columns * width + (columns + 1) * gap, top + rows * height + (rows + 1) * gap),
        "#f2efe7",
    )
    draw = ImageDraw.Draw(sheet)
    font_path = Path(r"C:\Windows\Fonts\msyh.ttc")
    font = ImageFont.truetype(str(font_path), 18)
    small = ImageFont.truetype(str(font_path), 14)
    draw.text((18, 18), "妙极画图 1.8.0 · 胶片冲印 / 真实 LUT 样片", fill="#17201e", font=font)

    fitted = ImageOps.fit(
        source, (width, width), method=Image.Resampling.LANCZOS, centering=(0.5, 0.43)
    )
    for index, stock in enumerate(STOCKS):
        column = index % columns
        row = index // columns
        x = gap + column * (width + gap)
        y = top + gap + row * (height + gap)
        _, lut = read_cube(cube_path(stock["bundle"]))
        rendered = apply_lut(fitted, lut)
        sheet.paste(rendered, (x, y))
        draw.text((x + 2, y + width + 9), stock["name"], fill="#17201e", font=font)
        draw.text((x + 2, y + width + 34), stock["stock"], fill="#68736e", font=small)

    output_path.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(output_path, quality=91, optimize=True, progressive=True)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--write-assets", action="store_true")
    parser.add_argument("--source", type=Path, default=DEFAULT_SOURCE)
    parser.add_argument(
        "--contact-out",
        type=Path,
        default=Path(r"E:\Codex\outputs\妙极手机画图\胶片冲印-v1.8.0-preview.jpg"),
    )
    args = parser.parse_args()

    if args.write_assets:
        ASSET_ROOT.mkdir(parents=True, exist_ok=True)
        for name, content in {**build_lut_js(), **sample_previews(args.source)}.items():
            (ASSET_ROOT / name).write_bytes(content)
            print(f"wrote {ASSET_ROOT / name}")

    contact_sheet(args.source, args.contact_out)
    print(f"wrote {args.contact_out}")


if __name__ == "__main__":
    main()
