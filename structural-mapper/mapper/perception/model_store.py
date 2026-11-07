"""Locate the semantic floor-plan model, downloading it once when it is missing.

The ONNX export of CubiCasa5k (66 MB) is published as a release of the public
`rumbo-models` repository, so neither git nor PyTorch is involved: the first start of the
mapper fetches it into a local cache and verifies its SHA-256 before using it.

Usage at image build time: `python -m mapper.perception.model_store /path/cubicasa.onnx`.
"""
from __future__ import annotations

import hashlib
import logging
import os
import sys
import tempfile
import urllib.request
from pathlib import Path

MODEL_URL = ("https://github.com/arquitecturas-software-emergentes-9061/rumbo-models/"
             "releases/download/cubicasa5k-v1/cubicasa.onnx")
MODEL_SHA256 = "ab630b6dc8540f77b594f0b75cb74506c1a2cdde0fa9ce2476423b2b84a994e4"
DEFAULT_CACHE = Path(__file__).resolve().parents[2] / ".models"

log = logging.getLogger("structural-mapper")


class ModelUnavailable(RuntimeError):
    pass


def default_model_path() -> Path:
    return Path(os.getenv("CUBICASA_MODEL_CACHE", DEFAULT_CACHE)) / "cubicasa.onnx"


def ensure_model(path: Path | None = None, url: str = MODEL_URL, sha256: str = MODEL_SHA256,
                 timeout: float = 120.0) -> Path:
    """Return a verified model file, downloading it atomically if it is absent or corrupt."""
    target = Path(path) if path is not None else default_model_path()
    if target.is_file() and _digest(target) == sha256:
        return target
    target.parent.mkdir(parents=True, exist_ok=True)
    log.info("Downloading the floor-plan model (66 MB) to %s", target)
    handle, temporary = tempfile.mkstemp(dir=target.parent, suffix=".part")
    try:
        with os.fdopen(handle, "wb") as out, urllib.request.urlopen(url, timeout=timeout) as response:
            while chunk := response.read(1 << 20):
                out.write(chunk)
        if _digest(Path(temporary)) != sha256:
            raise ModelUnavailable(f"Checksum mismatch for the model downloaded from {url}")
        os.chmod(temporary, 0o644)  # mkstemp is owner-only; the service may run as another user
        os.replace(temporary, target)
    except ModelUnavailable:
        raise
    except Exception as exc:
        raise ModelUnavailable(f"Could not download the floor-plan model from {url}: {exc}") from exc
    finally:
        if os.path.exists(temporary):
            os.remove(temporary)
    return target


def _digest(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        while chunk := handle.read(1 << 20):
            digest.update(chunk)
    return digest.hexdigest()


if __name__ == "__main__":  # pragma: no cover - build-time helper
    logging.basicConfig(level=logging.INFO)
    print(ensure_model(Path(sys.argv[1]) if len(sys.argv) > 1 else None))
