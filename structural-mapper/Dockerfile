# syntax=docker/dockerfile:1.7
# Default image: deterministic perception plus the CubiCasa5k semantic parser on ONNX Runtime (CPU, no PyTorch).
# The 66 MB model is downloaded from the public rumbo-models release at build time and verified by SHA-256,
# so the container starts offline. The optional BuildingCV perceiver is not installed here; see README.md.
FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PIP_PREFER_BINARY=1 \
    CUBICASA_ONNX_MODEL=/opt/rumbo-models/cubicasa.onnx \
    MAPPER_INFERENCE_THREADS=4

RUN apt-get update && apt-get install -y --no-install-recommends libglib2.0-0 curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home mapper

WORKDIR /workspace/structural-mapper
COPY requirements-geometry.txt requirements.txt ./
RUN --mount=type=cache,target=/root/.cache/pip pip install -r requirements.txt

COPY mapper/perception/model_store.py ./mapper/perception/model_store.py
RUN python mapper/perception/model_store.py "$CUBICASA_ONNX_MODEL"

COPY app.py ./app.py
COPY mapper ./mapper

USER mapper
EXPOSE 8010
HEALTHCHECK --interval=10s --timeout=3s --retries=5 CMD curl -fsS http://localhost:8010/health || exit 1
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8010"]
