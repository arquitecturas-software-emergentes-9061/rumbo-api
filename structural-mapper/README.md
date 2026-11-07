# Structural Mapper

Servicio de apoyo del contexto **Planning**. Lee un plano de piso (PDF, PNG, JPEG o SVG) y propone muros,
aperturas, espacios y una red de recorridos. `rumbo-api` lo consume al interpretar una versión de plano
(`POST /api/v1/sites/{siteId}/floors/{floorId}/plans/versions/{versionId}/interpretation`) y convierte su
respuesta a la propuesta que revisa el editor. **Nunca publica un mapa**: lo que devuelve son candidatos.

## Cómo funciona

Documento → ingesta (vectorial con PyMuPDF o raster) → percepción (trazos vectoriales y morfología clásica)
→ reconstrucción de muros y aperturas → partición en espacios → espacio transitable → grafo semántico →
validación. La percepción por red neuronal (BuildingCV) es opcional y **no se instala en la imagen por defecto**.

## Ejecutar

```bash
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements-geometry.txt
STRUCTURAL_MAPPER_DISABLE_ML=1 uvicorn app:app --port 8010
curl -s -F "file=@plano.png" localhost:8010/analyze | head -c 300
```

Con Docker: `docker compose --profile mapper up -d structural-mapper` desde la raíz de `rumbo-api` (escucha en `127.0.0.1:8010`).
Para que el API lo use: `STRUCTURAL_MAPPER_ENABLED=true` y, si no es el puerto por defecto, `STRUCTURAL_MAPPER_URL`.

## Pruebas

```bash
pip install -r requirements-geometry.txt
STRUCTURAL_MAPPER_DISABLE_ML=1 pytest -q
```

Las pruebas generan un plano sintético con `tests/support/generate_synthetic.py`; no necesitan modelos ni red.

## Límites conocidos

- Sin la red neuronal, los planos escaneados o con mucho ruido pueden perder paredes finas o columnas.
- Sin una distancia real calibrada, la escala se estima; el API la sustituye por la calibración de la versión.
- Los espacios son polígonos; el API los entrega al editor como cajas envolventes.
- Es una propuesta: un administrador debe revisar muros, puertas, nombres y conexiones antes de publicar.
