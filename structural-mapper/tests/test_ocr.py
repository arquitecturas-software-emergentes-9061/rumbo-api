from io import BytesIO
import subprocess
import unicodedata

import numpy as np
from PIL import Image, ImageDraw, ImageFont
import pymupdf
import pytest

from mapper.contracts import RasterInput, TextLabel
from mapper.ingest import ocr
from mapper.pipeline import MapperOptions, Pipeline, scene_to_payload


def caption_plan() -> Image.Image:
    image = Image.new("RGB", (1000, 600), "white")
    draw = ImageDraw.Draw(image)
    draw.rectangle((25, 25, 975, 575), outline="black", width=12)
    draw.line((500, 25, 500, 575), fill="black", width=12)
    font = ImageFont.load_default(size=28)
    draw.text((140, 265), "Consultorio 101", font=font, fill="black")
    draw.text((650, 265), "Ascensor", font=font, fill="black")
    return image


def plan_bytes(kind: str) -> tuple[bytes, str]:
    image = caption_plan()
    stream = BytesIO()
    image.save(stream, "JPEG" if kind == "jpeg" else "PNG")
    if kind == "pdf":
        document = pymupdf.open()
        page = document.new_page(width=image.width, height=image.height)
        page.insert_image(page.rect, stream=stream.getvalue())
        return document.tobytes(), "application/pdf"
    return stream.getvalue(), "image/" + kind


def normalized(value):
    return "".join(c for c in unicodedata.normalize("NFD", value.lower()) if not unicodedata.combining(c))


@pytest.mark.skipif(not ocr.available(), reason="Install Tesseract with spa+eng for actual OCR integration")
@pytest.mark.parametrize("kind", ["png", "jpeg", "pdf"])
def test_actual_ocr_labels_are_assigned_to_the_correct_geometry_and_classification(kind):
    raw, content_type = plan_bytes(kind)
    scene = Pipeline().run_bytes(raw, content_type, MapperOptions(use_ml=False, pixels_per_meter=50))
    assert scene.diagnostics["ocr_status"] == "extracted"
    consulting = next(s for s in scene.spaces if "consultorio 101" in normalized(s.label or ""))
    elevator = next(s for s in scene.spaces if "ascensor" in normalized(s.label or ""))
    assert consulting.polygon.centroid.x < elevator.polygon.centroid.x
    assert consulting.kind == "room"
    assert elevator.kind == "vertical" and elevator.connector_kind == "elevator"
    assert scene.validation.metrics["edges_crossing_walls"] == 0
    payload = scene_to_payload(scene)
    assert payload["parser_version"] == "5"
    assert any("consultorio 101" in normalized(s["label"] or "") for s in payload["spaces"])


def test_embedded_text_is_kept_without_running_ocr(monkeypatch):
    def unexpected(*args, **kwargs):
        raise AssertionError("Vector text must not be sent to OCR")
    monkeypatch.setattr(subprocess, "run", unexpected)
    label = TextLabel("Recepción", (30, 40))
    source = RasterInput(np.zeros((80, 80, 3), dtype=np.uint8), labels=[label])
    result = ocr.extract_labels(source)
    assert result.status == "embedded-text" and not result.labels
    assert source.labels == [label]


@pytest.mark.parametrize("failure,status", [
    (subprocess.TimeoutExpired("tesseract", 6), "timeout"),
    (OSError("engine unavailable"), "unavailable"),
])
def test_engine_failures_leave_geometry_available_for_manual_names(monkeypatch, failure, status):
    monkeypatch.setattr(ocr.shutil, "which", lambda _: "tesseract")
    def fail(*args, **kwargs):
        raise failure
    monkeypatch.setattr(subprocess, "run", fail)
    raw, kind = plan_bytes("png")
    scene = Pipeline().run_bytes(raw, kind, MapperOptions(use_ml=False, pixels_per_meter=50))
    assert scene.spaces
    assert scene.diagnostics["ocr_status"] == status
    assert all(s.label is None for s in scene.spaces)
    assert any(i.code == "ocr-" + status and i.severity == "warning" for i in scene.validation.issues)


def test_unavailable_disabled_and_nonzero_exit_are_explicit(monkeypatch):
    source = RasterInput(np.zeros((80, 80, 3), dtype=np.uint8))
    monkeypatch.setenv("MAPPER_OCR_DISABLED", "true")
    assert ocr.extract_labels(source).status == "disabled"
    monkeypatch.delenv("MAPPER_OCR_DISABLED")
    monkeypatch.setattr(ocr.shutil, "which", lambda _: None)
    assert ocr.extract_labels(source).status == "unavailable"
    monkeypatch.setattr(ocr.shutil, "which", lambda _: "tesseract")
    monkeypatch.setattr(subprocess, "run", lambda *a, **k: subprocess.CompletedProcess([], 1, b"", b"failure"))
    assert ocr.extract_labels(source).status == "failed"


@pytest.mark.skipif(not ocr.available(), reason="Actual Tesseract integration")
def test_no_text_is_not_invented():
    blank = RasterInput(np.full((500, 600, 3), 255, dtype=np.uint8))
    result = ocr.extract_labels(blank)
    assert result.status == "no-text" and not result.labels


def test_tsv_rejects_uncertain_words_measurements_and_cross_room_joining():
    header = "level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\tleft\ttop\twidth\theight\tconf\ttext\n"
    def row(line, x, text, conf=95):
        return f"5\t1\t1\t1\t{line}\t1\t{x}\t50\t100\t20\t{conf}\t{text}\n"
    tsv = header + row(1, 20, "Recepción") + row(1, 500, "Farmacia") + row(2, 20, "Ruido", 40) + row(3, 20, "12 m2")
    labels = ocr.parse_tsv(tsv, 1000, 600)
    assert [l.text for l in labels] == ["Recepción", "Farmacia"]
    assert labels[0].position == (70, 60) and labels[1].position == (550, 60)
