"""Read raster room labels without changing geometry or inventing missing names.

Tesseract emits words and boxes in the decoded raster's coordinates. Sparse-text mode
keeps separate room captions apart; low-confidence lines are left for manual review.
Embedded PDF/SVG text remains the preferred source and never goes through OCR twice.
"""
from __future__ import annotations

import csv
import os
import re
import shutil
import subprocess
from dataclasses import dataclass, field
from io import BytesIO, StringIO

from PIL import Image

from ..contracts import RasterInput, TextLabel

TIMEOUT_SECONDS = 6
MIN_CONFIDENCE = 70.0
MAX_LABELS = 500
MAX_LABEL_LENGTH = 120
_MEASUREMENT = re.compile(r"^[\d\s.,x×:/+-]+\s*(?:m|cm|mm|m2|m²|ft|sq\.?\s*ft)$", re.IGNORECASE)


@dataclass(slots=True)
class OcrResult:
    status: str
    labels: list[TextLabel] = field(default_factory=list)


def available() -> bool:
    return os.getenv("MAPPER_OCR_DISABLED", "").lower() not in ("1", "true", "yes") and shutil.which("tesseract") is not None


def extract_labels(source: RasterInput) -> OcrResult:
    if source.labels:
        return OcrResult("embedded-text")
    if os.getenv("MAPPER_OCR_DISABLED", "").lower() in ("1", "true", "yes"):
        return OcrResult("disabled")
    executable = shutil.which("tesseract")
    if executable is None:
        return OcrResult("unavailable")
    image = BytesIO()
    Image.fromarray(source.image).save(image, format="PNG")
    try:
        result = subprocess.run(
            [executable, "stdin", "stdout", "-l", "spa+eng", "--psm", "11", "tsv"],
            input=image.getvalue(), capture_output=True, timeout=TIMEOUT_SECONDS, check=False,
            env={**os.environ, "OMP_THREAD_LIMIT": "1"},
        )
    except subprocess.TimeoutExpired:
        return OcrResult("timeout")
    except OSError:
        return OcrResult("unavailable")
    if result.returncode != 0:
        return OcrResult("failed")
    labels = parse_tsv(result.stdout.decode("utf-8", errors="replace"), source.width, source.height)
    return OcrResult("extracted" if labels else "no-text", labels)


def parse_tsv(tsv: str, width: int, height: int) -> list[TextLabel]:
    lines: dict[tuple, list[dict]] = {}
    for row in csv.DictReader(StringIO(tsv), delimiter="\t", quoting=csv.QUOTE_NONE):
        if row.get("level") != "5" or not row.get("text", "").strip():
            continue
        try:
            word = dict(text=row["text"].strip(), confidence=float(row["conf"]),
                        x=int(row["left"]), y=int(row["top"]), w=int(row["width"]), h=int(row["height"]))
        except (ValueError, KeyError):
            continue
        if word["w"] <= 0 or word["h"] <= 0 or word["x"] < 0 or word["y"] < 0:
            continue
        if word["x"] + word["w"] > width or word["y"] + word["h"] > height:
            continue
        key = tuple(row.get(k) for k in ("page_num", "block_num", "par_num", "line_num"))
        lines.setdefault(key, []).append(word)

    labels = []
    for words in lines.values():
        # Do not concatenate captions in distant rooms even if the engine calls them one line.
        groups: list[list[dict]] = []
        for word in sorted(words, key=lambda w: w["x"]):
            if not groups or word["x"] - (groups[-1][-1]["x"] + groups[-1][-1]["w"]) > 3 * max(word["h"], groups[-1][-1]["h"]):
                groups.append([])
            groups[-1].append(word)
        for group in groups:
            text = " ".join(w["text"] for w in group)
            letters = sum(c.isalpha() for c in text)
            if (len(text) > MAX_LABEL_LENGTH or _MEASUREMENT.fullmatch(text)
                    or (letters < 3 and text.upper() not in ("WC", "RX"))
                    or any(not (MIN_CONFIDENCE <= w["confidence"] <= 100) for w in group)):
                continue
            x0, y0 = min(w["x"] for w in group), min(w["y"] for w in group)
            x1, y1 = max(w["x"] + w["w"] for w in group), max(w["y"] + w["h"] for w in group)
            labels.append(TextLabel(text, ((x0 + x1) / 2, (y0 + y1) / 2)))
            if len(labels) == MAX_LABELS:
                return labels
    return labels
