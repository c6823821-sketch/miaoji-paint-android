from __future__ import annotations

import json
import time
import urllib.request
from pathlib import Path

from generate_style_sheet import API_BASE, API_KEY


OUTPUT = Path(
    r"E:\Codex\work\miaoji-paint\android\app\src\main\assets"
    r"\style-geo-editorial.jpg"
)
RECEIPT = Path(r"E:\Codex\work\miaoji-paint\geo-editorial-preview.receipt.json")
MAX_CHARGED_CENTS = 6

PROMPT = """
Create a finished horizontal 4:3 travel-editorial artwork, suitable as a
thumbnail preview for an image-generation style called Travel Geography
Editorial. Use one truthful-looking photograph as the dominant base: an adult
Chinese traveler seen from behind at a scenic stone overlook above a misty
river valley near Chongqing at blue hour. Preserve believable photography,
natural skin, clothing, architecture, mountains, sky and original light; do not
turn the whole photograph into an illustration or painting.

Integrate one translucent, low-contrast cartographic layer directly into the
sky and other negative space. The map should feel like thin pale contour lines
printed on transparent tracing paper, with a river path, mountain contours and
two or three precise place labels in small editorial type. Mark the viewpoint
with one tiny vermilion-red dot. The map occupies about 20 percent of the image,
never covers the traveler's face or the main subject, and follows the photo's
actual geography rather than looking like a rectangular sticker.

Art direction: independent travel magazine and documentary photo book, calm and
natural, restrained ivory map lines, subtle paper-print grain, generous breathing
room, a very short elegant handwritten English caption in one corner. Preserve
the complete photographic frame and landscape composition. No giant headline,
poster frame, passport stamps, pins, scrapbook tape, sticker clutter, extra
people, logos or watermark. Sophisticated, specific, tactile and believable.
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
        )
    )

    deadline = time.time() + 8 * 60
    while time.time() < deadline:
        time.sleep(4)
        task = request_json(f"{API_BASE}/images/tasks/{task_id}").get("data") or {}
        print(f"status={task.get('status')} progress={task.get('progress')}")
        if task.get("status") == "succeeded":
            charged = int(task.get("charged_cents") or 0)
            receipt = {
                "task_id": task_id,
                "charged_cents": charged,
                "max_charged_cents": MAX_CHARGED_CENTS,
            }
            if charged > MAX_CHARGED_CENTS:
                receipt["stopped"] = True
                RECEIPT.write_text(
                    json.dumps(receipt, ensure_ascii=False, indent=2),
                    encoding="utf-8",
                )
                print(json.dumps(receipt, ensure_ascii=False))
                raise RuntimeError(
                    f"charged_cents={charged} exceeds {MAX_CHARGED_CENTS}; stop"
                )
            images = task.get("images") or []
            if not images:
                raise RuntimeError("No image URL")
            with urllib.request.urlopen(images[0]["url"], timeout=90) as response:
                image_bytes = response.read()
            OUTPUT.write_bytes(image_bytes)
            receipt["output"] = str(OUTPUT)
            receipt["bytes"] = len(image_bytes)
            RECEIPT.write_text(
                json.dumps(receipt, ensure_ascii=False, indent=2),
                encoding="utf-8",
            )
            print(json.dumps(receipt, ensure_ascii=False))
            return
        if task.get("status") in {"failed", "cancelled"}:
            raise RuntimeError(json.dumps(task, ensure_ascii=False))
    raise TimeoutError("image task timed out")


if __name__ == "__main__":
    main()
