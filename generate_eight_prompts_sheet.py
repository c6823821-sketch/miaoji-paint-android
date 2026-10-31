from __future__ import annotations

import json
import time
import urllib.request
from pathlib import Path

from PIL import Image

from generate_style_sheet import API_BASE, API_KEY


ASSET_ROOT = Path(r"E:\Codex\work\miaoji-paint\android\app\src\main\assets")
WORK_ROOT = Path(r"E:\Codex\work\miaoji-paint")
SHEET_OUTPUT = WORK_ROOT / "eight-prompts-sheet-v1.jpg"
RECEIPT = WORK_ROOT / "eight-prompts-sheet-v1.receipt.json"
MAX_CHARGED_CENTS = 500

TILE_OUTPUTS = [
    "style-y2k-doodle-poster.jpg",
    "style-impasto-diorama-v2.jpg",
    "style-watercolor-ink-reconstruct.jpg",
    "style-silly-marker-cartoon.jpg",
    "style-geometric-facet-collage.jpg",
    "style-fashion-fragment-collage.jpg",
    "style-embroidered-patchwork.jpg",
    "style-black-crayon-sketch.jpg",
]

PROMPT = """
Create one landscape 4-column by 2-row contact sheet with exactly eight finished
3:4-style portrait artworks. Each tile fills its cell edge to edge, has a fully
resolved composition and is separated by a crisp clean boundary. No panel number,
UI, phone mockup, outer frame, logo or watermark. The eight images must use
genuinely different materials and visual systems, not filters of the same image.

1 Y2K doodle selfie poster: a polished young adult woman portrait with glossy
skin and flash photography, surrounded by white cartoon cat ears, black marker
contours, stars, arrows, scribbles and a tiny green cartoon dinosaur breathing a
small flame. Add bold rough lettering "RAWR!" and "NOPE, STILL NOT NORMAL",
balanced around the subject. Bright playful Y2K colors, controlled chaos, sharp
eyes and high-end editorial finish.

2 3D impasto miniature landscape: reconstruct a recognizable landscape atop a
warm ivory textured paper field. Main mountain, water, path and tiny traveler are
raised oil-paint forms with clear knife ridges, peaks, curled edges and real
shadows. Generous negative space, fresh luminous greens and sky blues, tactile
paint relief, poetic and clean rather than a paint-filter screenshot.

3 Minimal ink-watercolor reconstruction: reduce a quiet old-town scene to soft
flat shapes on warm matte art paper. Muted washes, irregular dry ink edges,
loose contours and large quiet negative space. Subject sits slightly above center.
At the bottom, place a small elegant serif English title and one fine sans-serif
subtitle. Scholarly, airy, contemporary and restrained.

4 Silly marker cartoon: turn an ordinary adult portrait into an intentionally
awkward, funny felt-tip drawing. Wobbly uneven linework, playful distorted
perspective, oversized expression, flat careless color blocks that occasionally
cross the lines, small random doodles and comic background. Charming internet
meme energy, childlike but sophisticated, intentionally not polished realism.

5 Geometric facet collage portrait: rebuild a face and upper body from a limited
set of large rectangles, trapezoids, circles and irregular polygons. Broad flat
color blocks, stepped beard/hair shapes, off-register planes, warm ivory paper
background, huge calm margin. Seven parts abstraction, three parts recognizable
likeness, matte print grain, bold and reductive.

6 Fashion photo-fragment collage: a high-fashion portrait on pale blue-grey paper
cut into 12 to 20 rectangular fragments, slightly displaced with crisp white gaps,
soft curled corners and restrained shadows. Keep the face readable. Add five to
eight cream sticky notes, a little narrow tape and tiny handwritten English.
Large serif title, thin rules and small editorial captions. Minimal cool-toned
magazine cover energy, never a chaotic scrapbook.

7 Embroidered patchwork portrait: rebuild a person and a few necessary environment
elements entirely from cotton, linen, felt patches, visible running stitches,
chain stitches, loose thread ends, raw fabric edges, seams and soft relief on a
warm ivory linen field. Preserve pose, clothing direction and recognizable story.
Quiet space, muted source-derived colors, handmade warmth and editorial restraint.

8 Expressive black crayon sketch: an adult portrait drawn with coarse black wax
crayon, charcoal and dry brush on warm white paper. Bold messy overlapping
strokes, strong black masses in hair and clothing, selective white space on the
face, wax crumbs, smudges, broken lines and rough fibers. Exaggerate expression
and posture while keeping the person recognizable. Large empty paper field,
independent art-book energy, intentionally unfinished.

Tiles 1 to 8 should read clearly at thumbnail size and remain rewarding when
viewed larger. Use sophisticated art direction and realistic material detail.
Avoid duplicate layouts, glossy AI faces everywhere, generic neon, dense
scrapbooking, illegible text walls, logos and watermarks.
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
    tile_height = height // 2
    for index, filename in enumerate(TILE_OUTPUTS):
        column = index % 4
        row = index // 4
        box = (
            column * tile_width,
            row * tile_height,
            (column + 1) * tile_width,
            (row + 1) * tile_height,
        )
        sheet.crop(box).convert("RGB").save(
            ASSET_ROOT / filename,
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
                    "tiles": TILE_OUTPUTS,
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
