# syntax=docker/dockerfile:1.7
# Lean image: deterministic perception only (vector strokes + classical morphology), no PyTorch and no model weights.
# The optional BuildingCV perceiver is not installed here; see README.md to enable it.
FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PIP_PREFER_BINARY=1 \
    STRUCTURAL_MAPPER_DISABLE_ML=1

RUN apt-get update && apt-get install -y --no-install-recommends libglib2.0-0 curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home mapper

WORKDIR /workspace/structural-mapper
COPY requirements-geometry.txt ./requirements-geometry.txt
RUN --mount=type=cache,target=/root/.cache/pip pip install -r requirements-geometry.txt

COPY app.py ./app.py
COPY mapper ./mapper

USER mapper
EXPOSE 8010
HEALTHCHECK --interval=10s --timeout=3s --retries=5 CMD curl -fsS http://localhost:8010/health || exit 1
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8010"]
