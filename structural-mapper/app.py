"""Rumbo Structural Mapper service (v4).

Thin HTTP layer over `mapper.pipeline`. Perception uses the CubiCasa5k ONNX parser by
default (downloaded once and verified, see `mapper.perception.model_store`), an explicitly
configured ONNX file, or the optional legacy BuildingCV backend. If no model can be
loaded the service keeps deterministic vector/classical perception.
"""
from __future__ import annotations

import logging
import os
from contextlib import asynccontextmanager

from fastapi import FastAPI, File, Form, HTTPException, UploadFile

from mapper.ingest.decode import DecodeError
from mapper.pipeline import PARSER_VERSION, MapperOptions, Pipeline, scene_to_payload

MAX_BYTES = 32 * 1024 * 1024
log = logging.getLogger("structural-mapper")


def _load_ml():
    if os.getenv("STRUCTURAL_MAPPER_DISABLE_ML", "").lower() in ("1", "true", "yes"):
        return None
    onnx_path = os.getenv("CUBICASA_ONNX_MODEL")
    if onnx_path:
        from mapper.perception.cubicasa import CubicasaOnnxPerceiver

        # Explicit configuration must fail visibly if the model cannot load.
        return CubicasaOnnxPerceiver(onnx_path)
    try:
        # Default: the semantic parser, fetched once into a local cache.
        from mapper.perception.cubicasa import CubicasaOnnxPerceiver
        from mapper.perception.model_store import ensure_model

        return CubicasaOnnxPerceiver(ensure_model())
    except Exception as exc:  # offline first start or onnxruntime missing
        log.warning("Semantic floor-plan model unavailable: %s", exc)
    try:
        from mapper.perception.buildingcv import BuildingCvPerceiver

        return BuildingCvPerceiver(os.getenv("BUILDINGCV_RUN_DIR"), os.getenv("BUILDINGCV_DEVICE", "cpu"))
    except Exception as exc:  # pragma: no cover - depends on the runtime image
        log.warning("ML perception unavailable, running deterministic backends only: %s", exc)
        return None


@asynccontextmanager
async def lifespan(app: FastAPI):
    ml = _load_ml()
    app.state.pipeline = Pipeline(ml=ml)
    app.state.ml_backend = ml.name if ml is not None else None
    yield


app = FastAPI(title="Rumbo Structural Mapper", version="4.0", lifespan=lifespan)


@app.get("/health")
def health() -> dict:
    return {
        "status": "ok",
        "parser": "rumbo-structural-mapper",
        "parser_version": PARSER_VERSION,
        "ml_backend": app.state.ml_backend,
        "backends": [b for b in ("vector-strokes", "classical-morphology", app.state.ml_backend) if b],
    }


@app.post("/analyze")
async def analyze(
    file: UploadFile = File(...),
    pixels_per_meter: float | None = Form(default=None),
    raster_scale: float = Form(default=2.0),
    use_ml: bool = Form(default=True),
    meters_per_unit: float | None = Form(default=None),
    original_width_units: float | None = Form(default=None),
) -> dict:
    raw = await file.read()
    if not raw:
        raise HTTPException(status_code=400, detail="Plano vacío.")
    if len(raw) > MAX_BYTES:
        raise HTTPException(status_code=413, detail="Plano demasiado grande para análisis.")
    options = MapperOptions(pixels_per_meter=pixels_per_meter, raster_scale=raster_scale, use_ml=use_ml,
                            meters_per_unit=meters_per_unit, original_width_units=original_width_units)
    try:
        scene = app.state.pipeline.run_bytes(raw, file.content_type, options)
    except DecodeError as exc:
        raise HTTPException(status_code=422, detail=f"No se pudo decodificar el plano: {exc}") from exc
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    return scene_to_payload(scene)
