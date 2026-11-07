"""Project semantic openings through their host wall without widening their jambs."""
from __future__ import annotations

import numpy as np
from shapely.geometry import Polygon
from shapely.ops import unary_union

from ..contracts import Opening, Scale, WallElement
from ..geometry import as_polygons, rasterize, segment


def cut_semantic_openings(walls: list[WallElement], openings: list[Opening], scale: Scale,
                          shape: tuple[int, int]) -> list[WallElement]:
    blocked = rasterize([wall.polygon for wall in walls], shape)
    cuts = []
    radius = max(4, int(round(scale.px(0.5))))
    for opening in openings:
        if "ml" not in opening.sources:
            continue
        normal = np.asarray(opening.normal, dtype=float)
        ends = np.asarray(opening.axis.coords)
        axis = ends[-1] - ends[0]
        axis /= np.linalg.norm(axis)
        center = np.asarray(opening.center)
        offsets = np.arange(-radius, radius + 1)
        lo, hi = [], []
        for along in (-0.2, 0, 0.2):
            points = center + axis * opening.width_px * along + offsets[:, None] * normal
            pixels = np.round(points).astype(int)
            valid = ((pixels[:, 0] >= 0) & (pixels[:, 0] < shape[1])
                     & (pixels[:, 1] >= 0) & (pixels[:, 1] < shape[0]))
            occupied = np.zeros(len(offsets), dtype=bool)
            occupied[valid] = blocked[pixels[valid, 1], pixels[valid, 0]]
            changes = np.diff(np.r_[False, occupied, False].astype(int))
            starts, stops = np.flatnonzero(changes == 1), np.flatnonzero(changes == -1) - 1
            if len(starts) == 0:
                continue
            nearest = min(zip(starts, stops), key=lambda run: max(offsets[run[0]], -offsets[run[1]], 0))
            if max(offsets[nearest[0]], -offsets[nearest[1]], 0) > scale.px(0.15):
                continue
            lo.append(offsets[nearest[0]] - 2)
            hi.append(offsets[nearest[1]] + 2)
        if not lo:
            continue
        lower, upper = float(min(lo)), float(max(hi))
        opening.polygon = Polygon([
            center - axis * opening.width_px / 2 + normal * lower,
            center + axis * opening.width_px / 2 + normal * lower,
            center + axis * opening.width_px / 2 + normal * upper,
            center - axis * opening.width_px / 2 + normal * upper,
        ])
        center += normal * (lower + upper) / 2
        opening.center = (float(center[0]), float(center[1]))
        opening.axis = segment(opening.center, axis, opening.width_px)
        cuts.append(opening.polygon)
    if not cuts:
        return walls
    cut = unary_union(cuts)
    return [WallElement(part, wall.kind, wall.confidence) for wall in walls
            for part in as_polygons(wall.polygon.difference(cut)) if part.area > 0]
