# Build stage: compiles and packages the API with the same JDK used in CI.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B --no-transfer-progress dependency:go-offline
COPY src ./src
RUN mvn -B --no-transfer-progress package -DskipTests

# Runtime stage: the JRE, the executable jar and, next to it, the structural mapper with the CubiCasa5k model.
# It is Debian-based on purpose: the mapper's Python wheels (onnxruntime, OpenCV, SciPy) need glibc, which Alpine lacks.
# The mapper only starts when STRUCTURAL_MAPPER_EMBEDDED=true (see docker/start.sh), because with the model loaded it
# needs about 1 GB on its own: on a 512 MB instance leave it off and the API keeps working without it.
FROM eclipse-temurin:21-jre
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PIP_PREFER_BINARY=1 \
    CUBICASA_ONNX_MODEL=/opt/rumbo-models/cubicasa.onnx \
    MAPPER_INFERENCE_THREADS=2

RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 python3-venv libglib2.0-0 ca-certificates \
       tesseract-ocr tesseract-ocr-spa tesseract-ocr-eng \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home rumbo

WORKDIR /opt/structural-mapper
COPY structural-mapper/requirements-geometry.txt structural-mapper/requirements.txt ./
RUN python3 -m venv /opt/venv && /opt/venv/bin/pip install --no-cache-dir -r requirements.txt

# The 66 MB model comes from the public rumbo-models release and is verified by SHA-256 at build time.
COPY structural-mapper/mapper/perception/model_store.py ./mapper/perception/model_store.py
RUN /opt/venv/bin/python mapper/perception/model_store.py "$CUBICASA_ONNX_MODEL"

COPY structural-mapper/app.py ./app.py
COPY structural-mapper/mapper ./mapper

WORKDIR /app
COPY --from=build /workspace/target/rumbo-api-*.jar app.jar
COPY docker/start.sh /app/start.sh
RUN chmod 0755 /app/start.sh
USER rumbo
EXPOSE 8080
ENTRYPOINT ["/app/start.sh"]
