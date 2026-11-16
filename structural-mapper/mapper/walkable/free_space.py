"""Walkable area: interior spaces shrunk by a body clearance, joined through openings."""
from __future__ import annotations

import numpy as np
from collections.abc import Sequence
from shapely.geometry import Point, Polygon
from shapely.ops import nearest_points, unary_union

from ..contracts import Opening, Scale, Space, WallElement
from ..geometry import as_polygons

CLEARANCE_M = 0.25


def walkable_by_space(spaces: list[Space], scale: Scale, walls: Sequence[WallElement] = ()) -> dict[str, Polygon]:
    result: dict[str, Polygon] = {}
    clearance = scale.px(CLEARANCE_M)
    barrier = unary_union([wall.polygon for wall in walls])
    for space in spaces:
        if space.kind == "void":
            continue
        # Never relax the body clearance to keep a narrow or blocked space routable.
        shrunk = space.polygon.difference(barrier).buffer(-clearance)
        parts = [p for p in as_polygons(shrunk) if p.area > 0]
        if not parts:
            continue
        result[space.id] = unary_union(parts)  # type: ignore[assignment]
    return result


def walkable_union(walkable: dict[str, Polygon], openings: list[Opening], scale: Scale,
                   walls: Sequence[WallElement] = ()) -> list[Polygon]:
    clearance = scale.px(CLEARANCE_M)
    geometries = list(walkable.values())
    for opening in openings:
        if not any(side in walkable for side in opening.space_ids):
            continue
        width = opening.width_px - 2 * clearance
        if width <= 0:
            continue
        ends = np.asarray(opening.axis.coords, dtype=float)
        axis = ends[-1] - ends[0]
        length = np.linalg.norm(axis)
        if length <= 0:
            continue
        axis /= length
        normal = np.array([-axis[1], axis[0]])
        center = np.asarray(opening.center)
        # Extend through the inset on both sides, while retaining clearance at jambs.
        depth = opening.polygon.area / max(opening.width_px, 1.0) / 2 + clearance + 1.0
        # Space partitioning temporarily seals and pads openings. Reach the actual
        # inset boundaries, including that separation, rather than leaving a thin gap.
        for side in opening.space_ids:
            if side in walkable:
                _, nearest = nearest_points(Point(center), walkable[side])
                depth = max(depth, abs(float((np.array(nearest.coords[0]) - center) @ normal)) + 1.0)
        geometries.append(Polygon([
            center + axis * width / 2 + normal * depth,
            center - axis * width / 2 + normal * depth,
            center - axis * width / 2 - normal * depth,
            center + axis * width / 2 - normal * depth,
        ]))
    barrier = unary_union([wall.polygon for wall in walls])
    return as_polygons(unary_union(geometries).difference(barrier))
