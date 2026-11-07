"""Masks -> vector wall elements (walls, windows, free-standing obstacles)."""
from __future__ import annotations

import numpy as np
import cv2
from shapely.affinity import translate

from ..contracts import Scale, StructuralMasks, WallElement
from ..geometry import components_cropped, mask_to_polygons


def reconstruct_walls(masks: StructuralMasks, scale: Scale) -> list[WallElement]:
    simplify = max(1.0, scale.px(0.04))
    close_px = max(1, int(round(scale.px(0.05))))
    kernel = np.ones((2 * close_px + 1, 2 * close_px + 1), np.uint8)
    wall = cv2.morphologyEx(masks.wall.astype(np.uint8), cv2.MORPH_CLOSE, kernel).astype(bool)
    if masks.semantic_geometry:
        wall &= ~masks.window
    speckle = scale.px(0.12) ** 2
    obstacle_area = scale.px(1.2) ** 2

    elements: list[WallElement] = []
    for component, origin, stats in components_cropped(wall, min_area=speckle):
        area = float(stats[cv2.CC_STAT_AREA])
        w, h = int(stats[cv2.CC_STAT_WIDTH]), int(stats[cv2.CC_STAT_HEIGHT])
        compact = max(w, h) / max(1, min(w, h)) <= 3.0
        kind = "obstacle" if (area <= obstacle_area and compact) else "wall"
        for polygon in mask_to_polygons(component, simplify, min_area=speckle):
            elements.append(WallElement(polygon=translate(polygon, xoff=origin[0], yoff=origin[1]), kind=kind))

    window = masks.window & ~wall
    for polygon in mask_to_polygons(window, simplify, min_area=speckle):
        elements.append(WallElement(polygon=polygon, kind="window", confidence=0.8))
    return elements
