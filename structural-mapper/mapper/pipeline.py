"""Orchestrates the stages and serializes the result for rumbo-api."""
from __future__ import annotations

import time
import math
from dataclasses import dataclass

import numpy as np

from .contracts import RasterInput, Scale, Scene
from .graph.build import GraphBuilder
from .inference.spaces import SpaceInference
from .ingest.decode import decode
from .perception.base import Perceiver
from .perception.classical import ClassicalPerceiver, dark_mask, estimate_stroke_thickness
from .perception.fusion import fuse
from .perception.vector import VectorPerceiver
from .reconstruction.openings import detect_openings
from .reconstruction.polygons import reconstruct_walls
from .reconstruction.semantic import cut_semantic_openings
from .validation.rules import validate
from .walkable.free_space import walkable_by_space, walkable_union

ASSUMED_WALL_M = 0.2
TYPICAL_DOOR_M = 0.9
MIN_DOORS_FOR_SCALE = 3
PARSER_VERSION = "4"


@dataclass(slots=True)
class MapperOptions:
    pixels_per_meter: float | None = None
    raster_scale: float = 2.0
    use_ml: bool = True
    use_classical: bool = True
    meters_per_unit: float | None = None
    original_width_units: float | None = None


class Pipeline:
    def __init__(self, ml: Perceiver | None = None):
        self.ml = ml
        self.classical = ClassicalPerceiver()
        self.vector = VectorPerceiver()

    def run_bytes(self, raw: bytes, content_type: str | None, options: MapperOptions) -> Scene:
        t0 = time.perf_counter()
        source = decode(raw, content_type, raster_scale=options.raster_scale)
        decoded = time.perf_counter() - t0
        scene = self.run(source, options)
        scene.diagnostics["t_decode"] = round(decoded, 4)
        scene.diagnostics["t_total"] = round(time.perf_counter() - t0, 4)
        return scene

    def run(self, source: RasterInput, options: MapperOptions) -> Scene:
        timings: dict[str, float] = {}
        t0 = time.perf_counter()
        scale = self._scale(source, options)
        shape = (source.height, source.width)

        ml = None
        if options.use_ml and self.ml is not None:
            # Learned perception is scale-independent; run it once even if the scale is revised.
            ml = self.ml.perceive(source, scale)
        timings["perception"] = time.perf_counter() - t0
        stage = time.perf_counter()
        masks, walls, openings = self._reconstruct(source, options, scale, ml, shape)
        # Gaps between classical wall strokes are not reliable door widths; only learned
        # door detections are used as a ruler.
        revised = door_scale(openings, scale) if masks.semantic_geometry else None
        if revised is not None:
            scale = revised
            masks, walls, openings = self._reconstruct(source, options, scale, ml, shape)
        timings["reconstruction"] = time.perf_counter() - stage
        stage = time.perf_counter()

        inference = SpaceInference(scale, shape, masks.thin, semantic_geometry=masks.semantic_geometry)
        spaces, openings, _ = inference.run(walls, openings, source.labels)
        walls = walls + inference.glass
        timings["inference"] = time.perf_counter() - stage
        stage = time.perf_counter()

        walkable = walkable_by_space(spaces, scale, walls)
        free = walkable_union(walkable, openings, scale, walls)
        builder = GraphBuilder(scale, shape, walls)
        graph = builder.build(spaces, openings, walkable, walkable_area=free)
        timings["graph"] = time.perf_counter() - stage

        scene = Scene(
            width=source.width,
            height=source.height,
            scale=scale,
            walls=walls,
            openings=openings,
            spaces=spaces,
            walkable=free,
            graph=graph,
            labels=source.labels,
        )
        stage = time.perf_counter()
        scene.validation = validate(scene)
        timings["validation"] = time.perf_counter() - stage
        scene.diagnostics = {
            "backend": masks.backend,
            "source_kind": source.source_kind,
            "scale_source": scale.source,
            "pixels_per_meter": round(scale.pixels_per_meter, 3),
            "wall_polygons": sum(1 for w in walls if w.kind == "wall"),
            "obstacles": sum(1 for w in walls if w.kind == "obstacle"),
            "windows": sum(1 for w in walls if w.kind == "window"),
            "doors": sum(1 for o in openings if o.kind in ("door", "passage")),
            "entrances": sum(1 for o in openings if o.kind == "entrance"),
            "spaces": len(spaces),
            "circulation_spaces": sum(1 for s in spaces if s.kind == "circulation"),
            "nodes": len(graph.nodes),
            "edges": len(graph.edges),
            "edges_dropped_crossing": builder.dropped_crossing,
            "components_bridged": builder.bridged_components,
            "labels": len(source.labels),
            **{f"t_{k}": round(v, 4) for k, v in timings.items()},
            "t_total": round(time.perf_counter() - t0, 4),
        }
        return scene

    def _reconstruct(self, source: RasterInput, options: MapperOptions, scale: Scale, ml, shape):
        classical = self.classical.perceive(source, scale) if options.use_classical else None
        vector = self.vector.perceive(source, scale) if source.vector_walls is not None else None
        masks = fuse(ml, classical, vector, scale)
        walls = reconstruct_walls(masks, scale)
        openings = detect_openings(masks, walls, scale, shape)
        if masks.semantic_geometry:
            walls = cut_semantic_openings(walls, openings, scale, shape)
        return masks, walls, openings

    def _scale(self, source: RasterInput, options: MapperOptions) -> Scale:
        # Derive the raster scale after decoding: SVG render dimensions need not equal
        # the dimensions stored by the API, and vector pages can have their own zoom.
        if options.meters_per_unit is not None or options.original_width_units is not None:
            values = (options.meters_per_unit, options.original_width_units)
            if any(value is None or not math.isfinite(value) or value <= 0 for value in values):
                raise ValueError("La calibración requiere una escala y un ancho original positivos y finitos.")
            return Scale(source.width / (options.original_width_units * options.meters_per_unit), "calibrated")
        if options.pixels_per_meter and options.pixels_per_meter > 0:
            return Scale(float(options.pixels_per_meter), "calibrated")
        if source.vector_scale:
            return Scale(float(source.vector_scale), "vector")
        mask = source.vector_walls if source.vector_walls is not None else dark_mask(source.image)
        thickness = estimate_stroke_thickness(mask)
        ppm = float(np.clip(thickness / ASSUMED_WALL_M, 8.0, 400.0))
        return Scale(ppm, "estimated")


def door_scale(openings, scale: Scale) -> Scale | None:
    """Revise a wall-thickness guess with the doors the plan actually draws.

    Drawn wall thickness varies a lot between offices and drawing styles, while interior
    doors stay close to 0.9 m. When the plan carries no calibration and enough doors were
    detected, their median width is the better ruler. A calibrated or vector scale is never
    replaced, and a revision under 20 % is ignored to keep stable plans stable.
    """
    if scale.source != "estimated":
        return None
    widths = sorted(o.width_px for o in openings if o.kind == "door" and o.width_px > 0)
    if len(widths) < MIN_DOORS_FOR_SCALE:
        return None
    ppm = float(np.clip(widths[len(widths) // 2] / TYPICAL_DOOR_M, 8.0, 400.0))
    if abs(ppm - scale.pixels_per_meter) / scale.pixels_per_meter < 0.2:
        return None
    return Scale(ppm, "estimated")


def scene_to_payload(scene: Scene) -> dict:
    def ring(coords):
        return [[round(float(x), 2), round(float(y), 2)] for x, y in coords]

    def polygon(p):
        return {"outer": ring(p.exterior.coords[:-1]), "holes": [ring(h.coords[:-1]) for h in p.interiors]}

    def label_position(space):
        anchor = space.anchor
        return [round(anchor.x, 2), round(anchor.y, 2)]

    return {
        "parser": "rumbo-structural-mapper",
        "parser_version": PARSER_VERSION,
        "image_size": {"width": scene.width, "height": scene.height},
        "scale": {"pixels_per_meter": scene.scale.pixels_per_meter, "source": scene.scale.source},
        "walls": [polygon(w.polygon) for w in scene.walls if w.kind == "wall"],
        "obstacles": [polygon(w.polygon) for w in scene.walls if w.kind == "obstacle"],
        "windows": [polygon(w.polygon) for w in scene.walls if w.kind == "window"],
        "openings": [
            {
                "id": o.id,
                "kind": o.kind,
                "polygon": polygon(o.polygon),
                "center": [round(o.center[0], 2), round(o.center[1], 2)],
                "axis": ring(o.axis.coords),
                "width_px": round(o.width_px, 2),
                "confidence": round(o.confidence, 3),
                "spaces": [s for s in o.space_ids],
                "sources": list(o.sources),
            }
            for o in scene.openings
        ],
        "doors": [polygon(o.polygon) for o in scene.openings if o.kind != "entrance"],
        "spaces": [
            {
                "id": s.id,
                "kind": s.kind,
                "label": s.label,
                "connector_kind": s.connector_kind,
                "polygon": polygon(s.polygon),
                "label_position": label_position(s),
                "openings": list(s.opening_ids),
                "area_px": round(s.area_px, 1),
                "confidence": round(s.confidence, 3),
            }
            for s in scene.spaces
        ],
        "walkable": [{"polygon": polygon(p), "kind": "walkable"} for p in scene.walkable],
        "graph": {
            "nodes": [
                {
                    "id": n.id,
                    "x": round(n.x, 2),
                    "y": round(n.y, 2),
                    "kind": n.kind,
                    "space_id": n.space_id,
                    "opening_id": n.opening_id,
                    "label": n.label,
                    "connector_kind": n.connector_kind,
                }
                for n in scene.graph.nodes
            ],
            "edges": [{"from": e.from_id, "to": e.to_id, "kind": e.kind, "length_px": round(e.length_px, 2)} for e in scene.graph.edges],
        },
        "validation": {
            "status": scene.validation.status,
            "issues": [
                {"code": i.code, "severity": i.severity, "message": i.message, "subjects": i.subject_ids}
                for i in scene.validation.issues
            ],
            "metrics": scene.validation.metrics,
        },
        "diagnostics": scene.diagnostics,
    }
