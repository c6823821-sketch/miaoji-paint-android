from __future__ import annotations

import json
import time
import urllib.request
from pathlib import Path

from PIL import Image

from generate_style_sheet import API_BASE, API_KEY


ASSET_ROOT = Path(r"E:\Codex\work\miaoji-paint\android\app\src\main\assets")
WORK_ROOT = Path(r"E:\Codex\work\miaoji-paint")
SHEET_OUTPUT = WORK_ROOT / "skill-pack-v2-sheet.jpg"
RECEIPT = WORK_ROOT / "skill-pack-v2-sheet.receipt.json"
MAX_CHARGED_CENTS = 500

TILE_IDS = [
    "minimal-zine-poster",
    "photo-revival",
    "scene-distillation",
    "pixel-style-poster",
    "kimi-tech-editorial",
    "mondo-cinema-poster",
    "github-trending-poster",
    "skill-index-board",
    "travel-photo-abstraction",
    "crt-terminal-poster",
    "dreamcore-collage",
    "collage-design",
]

PROMPT = """
Create one landscape 4-column by 3-row contact sheet with exactly twelve finished
poster artwork previews, reading left to right, top to bottom. Each tile must be
visually distinct, fill its complete rectangular cell edge to edge, and contain a
finished composition with no mockup, no phone frame, no outer border, no panel
number and no watermark. Keep all in-image typography short, restrained and
spelled correctly. The twelve tiles must be clearly different media and spatial
ideas, not twelve color filters of the same picture.

1 Minimal emotional zine: mostly warm ivory paper with about 85 percent quiet
space, one small torn old-photo fragment of a lone tree by the sea, one saturated
cobalt cut-paper shape, tiny type "LESS, BUT BETTER" and a tiny date.

2 Photo revival: a tiny hand-painted travel scene of an old bicycle beside a
sunlit stone alley, occupying only 14 percent of a clean white page; vivid local
watercolor and pencil color, huge empty paper field, tiny handwritten note.

3 Scene distillation zine: no photographic material; one small traveler silhouette
facing a huge sculptural mountain mass, one saturated vermilion shape as emotional
event, broken ink contours, mostly empty warm paper, short authorial typography.

4 Pixel style poster: a recognizable mountain and lake profile rebuilt from very
fine varied-density halftone dots on black, one restrained cyan and coral reverse
halftone glow, precise technical grid and the short word "SIGNAL".

5 Computational technology editorial: one polished chrome torus floating in a
large pale silver space, subtle iridescent reflection, immaculate product lighting,
tiny technical labels, model number K-1.6 and balanced editorial grid.

6 Mondo cinema poster: three-ink collectible screenprint on warm ivory with black
and one tomato red, a small figure before a giant red moon and a narrow tower,
strong graphic negative space, subtle print misregistration, title "AFTER LIGHT".

7 Open-source trend daily: a dark graphite data-poster with five clean ranking
rows, tiny legible project names and numbers, one fluorescent green signal color,
a small contribution heatmap, rigorous alignment and generous breathing room.

8 Creative skill index board: warm grey art paper with a precise 3 by 3 library of
nine tiny but genuinely different artworks: pencil still life, cobalt print,
landscape photo, torn-paper collage, chrome form, geometric type, botanical
specimen, abstract 3D and tiny architecture; short category labels around the grid.

9 Travel photo abstraction diptych: top half a truthful cinematic lakeside travel
photo with one small traveler, bottom half uniform ivory with only three tiny
source-derived abstract marks, huge quiet space, microtype number and phrase.

10 CRT terminal archive: a slightly curved black monitor filled with phosphor-green
minimal system interface, precise menu lines and a tiny pixel landscape, visible
scanlines and glass reflection, readable short terminal labels only.

11 Dreamcore surreal collage: an ordinary open door in a quiet room opens onto a
blue evening cloud field, one impossible distant mountain reduced to a miniature,
vintage magazine paper texture, muted cream blue and one warm orange accent.

12 Photo collage reconstruction: a lakeside tree photograph cut into five to eight
irregular sharp paper fragments and recomposed into a new floating spatial collage,
real photographic grain preserved inside pieces, warm ivory background, one restrained
yellow block and the short word "ORBIT".

Use sophisticated contemporary art direction, clear thumbnail readability,
varied negative space, realistic paper or screen materials, and visible fine detail
at close range. Avoid identical layouts, cheap neon cyberpunk, crowded scrapbooking,
generic AI plastic faces, illegible text walls, logos, watermarks and duplicate panels.
""".strip()


def request_json(url: str, payload: dict | None = None) -> dict:
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=data,
        method="GET" if payload is None else "POST",
        headers={
            "Authorization": f"Bearer {API_KEY}",
            "Accept": "application/json",
            "Content-Type": "application/json; charset=utf-8",
        },
    )
    with urllib.request.urlopen(request, timeout=90) as response:
        return json.loads(response.read().decode("utf-8"))


def crop_tiles(sheet: Image.Image) -> None:
    width, height = sheet.size
    tile_width = width // 4
    tile_height = height // 3
    for index, style_id in enumerate(TILE_IDS):
        column = index % 4
        row = index // 4
        box = (
            column * tile_width,
            row * tile_height,
            (column + 1) * tile_width,
            (row + 1) * tile_height,
        )
        sheet.crop(box).convert("RGB").save(
            ASSET_ROOT / f"style-{style_id}.jpg",
            quality=93,
            optimize=True,
        )


def main() -> None:
    submitted = request_json(
        f"{API_BASE}/images/generations",
        {
            "model": "gpt-image-2.5-flare",
            "prompt": PROMPT,
            "size": "4:3",
            "resolution": "1k",
            "quality": "low",
            "n": 1,
        },
    )
    data = submitted.get("data") or {}
    task_id = data.get("task_id")
    if not task_id:
        raise RuntimeError(f"No task id: {submitted}")
    print(
        json.dumps(
            {
                "task_id": task_id,
                "estimated_cents": data.get("estimated_cents"),
                "max_charged_cents": MAX_CHARGED_CENTS,
            },
            ensure_ascii=False,
        ),
        flush=True,
    )

    deadline = time.time() + 10 * 60
    while time.time() < deadline:
        time.sleep(4)
        task = request_json(f"{API_BASE}/images/tasks/{task_id}").get("data") or {}
        print(
            f"status={task.get('status')} progress={task.get('progress')}",
            flush=True,
        )
        status = task.get("status")
        if status == "succeeded":
            charged = int(task.get("charged_cents") or 0)
            receipt = {
                "task_id": task_id,
                "charged_cents": charged,
                "max_charged_cents": MAX_CHARGED_CENTS,
                "model": "gpt-image-2.5-flare",
                "resolution": "1k",
                "quality": "low",
                "size": "4:3",
            }
            if charged > MAX_CHARGED_CENTS:
                receipt["stopped"] = True
                RECEIPT.write_text(
                    json.dumps(receipt, ensure_ascii=False, indent=2),
                    encoding="utf-8",
                )
                raise RuntimeError(
                    f"charged_cents={charged} exceeds {MAX_CHARGED_CENTS}; stop"
                )
            images = task.get("images") or []
            if not images:
                raise RuntimeError("No image URL")
            with urllib.request.urlopen(images[0]["url"], timeout=120) as response:
                image_bytes = response.read()
            SHEET_OUTPUT.write_bytes(image_bytes)
            with Image.open(SHEET_OUTPUT) as sheet:
                crop_tiles(sheet)
            receipt.update(
                {
                    "output": str(SHEET_OUTPUT),
                    "bytes": len(image_bytes),
                    "tiles": TILE_IDS,
                }
            )
            RECEIPT.write_text(
                json.dumps(receipt, ensure_ascii=False, indent=2),
                encoding="utf-8",
            )
            print(json.dumps(receipt, ensure_ascii=False), flush=True)
            return
        if status in {"failed", "cancelled"}:
            raise RuntimeError(json.dumps(task, ensure_ascii=False))
    raise TimeoutError("image task timed out")


if __name__ == "__main__":
    main()
