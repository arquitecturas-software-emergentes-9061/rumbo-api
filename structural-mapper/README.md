# Structural Mapper

Servicio de apoyo del contexto **Planning**. Lee un plano de piso (PDF, PNG, JPEG o SVG) y propone muros,
aperturas, espacios y una red de recorridos. `rumbo-api` lo consume al interpretar una versión de plano
(`POST /api/v1/sites/{siteId}/floors/{floorId}/plans/versions/{versionId}/interpretation`) y convierte su
respuesta a la propuesta que revisa el editor. **Nunca publica un mapa**: lo que devuelve son candidatos.

## Cómo funciona

Documento → ingesta (vectorial con PyMuPDF o raster) → percepción → reconstrucción de muros y aperturas →
partición en espacios → espacio transitable → grafo semántico → reparación de conectividad → validación.

La percepción usa por defecto el modelo **CubiCasa5k** sobre ONNX Runtime (CPU, sin PyTorch), que reconoce muros,
puertas, ventanas y ambientes en planos raster; se combina con los trazos vectoriales y la morfología clásica.
El modelo (66 MB) **se descarga solo** la primera vez desde el release público
[`rumbo-models`](https://github.com/arquitecturas-software-emergentes-9061/rumbo-models/releases/tag/cubicasa5k-v1)
a `structural-mapper/.models/` (ignorada por git) y se verifica su SHA-256. Si no hay red en ese primer arranque,
el servicio sigue funcionando con la percepción determinista y lo indica en `/health` (`ml_backend: null`).

## Ejecutar

```bash
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn app:app --port 8010
curl -s localhost:8010/health   # ml_backend: cubicasa5k-onnx
```

Con la API no hace falta nada: `mvn spring-boot:run` levanta este servicio con Docker Compose (escucha en
`127.0.0.1:8010`; la imagen descarga el modelo al construirse) y la API siempre lo usa. A mano:
`docker compose up -d structural-mapper` desde la raíz de `rumbo-api`. Si corre en otro puerto, indicarlo con
`STRUCTURAL_MAPPER_URL`. La API pide el modelo salvo que se ponga `STRUCTURAL_MAPPER_USE_ML=false`.

Variables del servicio: `STRUCTURAL_MAPPER_DISABLE_ML=1` desactiva cualquier modelo; `CUBICASA_ONNX_MODEL` usa otra
copia del archivo (si falta o está dañada, el arranque falla en vez de servir resultados clásicos en silencio);
`CUBICASA_MODEL_CACHE` cambia la carpeta de descarga; `MAPPER_INFERENCE_THREADS` (4) limita los hilos de inferencia.

La alternativa anterior con BuildingCV (`Dockerfile.ml`, PyTorch, varios GB) sigue disponible con
`docker compose --profile mapper-ml up -d structural-mapper-ml`, pero ya no es necesaria.

## Pruebas

```bash
pip install -r requirements.txt
STRUCTURAL_MAPPER_DISABLE_ML=1 pytest -q
```

Las pruebas generan un plano sintético con `tests/support/generate_synthetic.py`; no necesitan modelos ni red.

El parser v4 conserva ciclos de circulación, valida que cada arista esté contenida en el espacio transitable y
mantiene el margen corporal de 0,25 m: si un espacio no admite ese margen, no se fuerza un recorrido por él.
Las aperturas enlazan los espacios retraídos conservando margen en las jambas. Si una unión local falla en una
puerta, una búsqueda geodésica sobre el espacio libre une los fragmentos que de verdad están comunicados (sin
cruzar muros ni salir del área transitable); lo que el espacio libre no comunica queda aislado y con su advertencia.
Es una aproximación geométrica;
no acredita accesibilidad de una silla de ruedas ni condiciones físicas ausentes en el dibujo.

La respuesta conserva los polígonos con huecos y el eje real de cada apertura (`openings[].axis`). Las aperturas
detectadas que no pueden asociarse con certeza a dos espacios también llegan en `openings`, con
`review_required: true` y `review_reason`; no autorizan aristas del grafo hasta que se revisen. La API los
proyecta a las unidades del original, junto con las ventanas, obstáculos y área transitable. La calibración puede
enviarse como `meters_per_unit` y `original_width_units`; se convierte después de decodificar, también para SVG.
`pixels_per_meter` sigue disponible para clientes anteriores. Los tiempos `t_*` son por etapa; `t_total` de
`run_bytes` incluye decodificación y procesamiento, pero no la serialización HTTP.

Para comparar costo y cobertura en archivos propios, sin ML:

```bash
python benchmark.py --repeats 3 --output /tmp/mapper-benchmark.json plano.png plano.pdf
```

La medición externa incluye la construcción del payload. Compara en el mismo equipo, entorno, archivos y escala.
Los conteos de espacios y conexiones no son métricas de exactitud: requieren una referencia anotada independiente.

## Límites conocidos

- Sin la red neuronal, los planos escaneados o con mucho ruido pueden perder paredes finas o columnas.
- Sin una distancia real calibrada, la escala se estima con el grosor de los muros y, si el modelo detecta al menos
  tres puertas, se corrige con su ancho mediano (0,9 m). El API la sustituye por la calibración de la versión.
- La propuesta y su contexto visual conservan polígonos, pero el contrato persistido del borrador sigue guardando
  nodos, conexiones y destinos. La geometría estructural aún no se publica como un paquete espacial para el móvil.
- Es una propuesta: un administrador debe revisar muros, puertas, nombres y conexiones antes de publicar.

## Modelo CubiCasa5k

El archivo publicado en `rumbo-models` se exportó con:

```sh
python tools/export_cubicasa_onnx.py \
  --source /path/to/CubiCasa5k \
  --checkpoint /path/to/model_best_val_loss_var.pkl \
  --output /path/to/cubicasa.onnx
```

Solo hace falta repetirlo para publicar otra versión del modelo (requiere PyTorch y ONNX). Se usa el commit
`c34440266665a11f4484eb06cd2e4b7d72ad76c1` y el checkpoint oficial de
[CubiCasa5k](https://github.com/CubiCasa/CubiCasa5k); el exportador verifica su SHA-256. El grafo exportado incluye
los 23 canales de ambientes e íconos, excluye las salidas de esquinas y admite dimensiones múltiplos de 64; la
normalización es RGB `[-1, 1]`. Las probabilidades de puertas y ventanas usan histéresis conectada; las puertas se
proyectan a través de su muro antes de inferir adyacencias. Las ventanas siguen siendo barreras y un contacto con el
contorno no crea una entrada: la falta de entrada queda como advertencia.

El código y los datos de CubiCasa5k están bajo **CC BY-NC 4.0**; Rumbo los usa como proyecto académico, sin fines
comerciales. El modelo se entrenó con viviendas: en otros tipos de plano reconoce menos, por eso la salida es una
propuesta que se corrige en el editor. Resultados medidos en `../docs/structural-recognition-verification.md`.
