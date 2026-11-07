"""Joint floor-plan parser through ONNX Runtime; no PyTorch dependency at inference."""
from __future__ import annotations

import os
from pathlib import Path

import cv2
import numpy as np

from ..contracts import RasterInput, Scale, StructuralMasks


def _softmax(logits: np.ndarray) -> np.ndarray:
    probabilities = np.exp(logits - logits.max(axis=0, keepdims=True))
    return probabilities / probabilities.sum(axis=0, keepdims=True)


def _supported_region(probability: np.ndarray, seeds: np.ndarray) -> np.ndarray:
    """Recover uncertain icon endpoints only when connected to an actual class prediction."""
    candidate = probability >= 0.2
    count, labels = cv2.connectedComponents(candidate.astype(np.uint8), 8)
    keep = np.zeros(count, dtype=bool)
    keep[np.unique(labels[seeds & candidate])] = True
    keep[0] = False
    return keep[labels]


class CubicasaOnnxPerceiver:
    name = "cubicasa5k-onnx"

    def __init__(self, path: str | Path):
        import onnxruntime as ort

        options = ort.SessionOptions()
        options.intra_op_num_threads = max(1, int(os.getenv("MAPPER_INFERENCE_THREADS", "4")))
        options.inter_op_num_threads = 1
        self.session = ort.InferenceSession(str(path), sess_options=options, providers=["CPUExecutionProvider"])
        self.input_name = self.session.get_inputs()[0].name

    def perceive(self, source: RasterInput, scale: Scale | None) -> StructuralMasks:
        h, w = source.image.shape[:2]
        factor = min(1.0, 1024 / max(h, w))
        height = max(64, int(np.ceil(h * factor / 64)) * 64)
        width = max(64, int(np.ceil(w * factor / 64)) * 64)
        image = cv2.resize(source.image, (width, height), interpolation=cv2.INTER_AREA)
        tensor = ((image.astype(np.float32) / 255) * 2 - 1).transpose(2, 0, 1)[None]
        logits = self.session.run(None, {self.input_name: tensor})[0][0]
        logits = np.stack([cv2.resize(channel, (w, h), interpolation=cv2.INTER_LINEAR) for channel in logits])
        rooms, icons = _softmax(logits[:12]), _softmax(logits[12:])
        room_classes, icon_classes = rooms.argmax(axis=0), icons.argmax(axis=0)
        return StructuralMasks(wall=room_classes == 2,
                               door=_supported_region(icons[2], icon_classes == 2),
                               window=_supported_region(icons[1], icon_classes == 1),
                               wall_probability=rooms[2], backend=self.name, semantic_geometry=True)
