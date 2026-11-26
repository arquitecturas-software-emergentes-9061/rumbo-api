# Viabilidad de generar el modelo navegable desde planos

Conclusión del spike sobre interpretación automática de planos en `planning`. Consolida lo medido hasta el 15 de noviembre de 2026 y enlaza la evidencia ya registrada en el repositorio. No introduce mediciones nuevas.

## Conclusión

**Es viable como propuesta asistida, no como generación automática confiable.** El servicio `structural-mapper` produce candidatos de muros, aperturas, espacios y red de recorridos que el administrador revisa y corrige en el editor antes de publicar. Ninguna técnica evaluada permite publicar sin revisión.

| Entrada | Camino recomendado | Confianza |
|---|---|---|
| SVG anotado (`data-rumbo-role`) | Lectura directa en Java, sin mapper | Alta: la estructura la declara el archivo |
| SVG sin anotar | `structural-mapper`, extracción vectorial | Media-baja: recuperación de espacios muy variable (ver tabla 2) |
| PNG, JPEG y PDF | `structural-mapper` con percepción CubiCasa5k (ONNX) | Media: mejor que la morfología clásica, sin validación sobre un corpus propio |

## 1. Técnicas comparadas

| Técnica | Resultado observado | Costo |
|---|---|---|
| SVG anotado | Zonas, etiquetas y puertas explícitas se leen directo; cubierto por pruebas (`PLANNING-SVG-INTERPRETATION.md`) | Mínimo |
| Vectorial sin anotar (mapper) | Recupera geometría candidata; el conteo de espacios varía mucho entre planos (tabla 2) | Segundos a decenas de segundos |
| Raster con morfología clásica | Sobre un raster amueblado fusionó casi todo en un solo espacio transitable | Bajo |
| Raster con BuildingCV (PyTorch) | Omitió particiones críticas en el mismo raster | Imagen de varios GB (`Dockerfile.ml`) |
| Raster con CubiCasa5k vía ONNX | Reconoció los cinco consultorios principales del raster de referencia (tabla 1) | Modelo de ~66 MiB, CPU, sin PyTorch |

Tabla 1. Raster real de referencia (JPEG 600 × 500, calibrado a 0,01826 m/px; detalle en `docs/structural-recognition-verification.md`):

- 5 consultorios separados con polígono y nodo destino propios, verificados manualmente en 5 puntos interiores.
- Grafo final: 43 nodos, 42 aristas, 1 componente conexa, 0 cruces de muro, 0 aristas fuera del área transitable.
- Seis transiciones: cinco puertas de consultorio y el paso entre tramos del vestíbulo.
- Diez regiones segmentadas sin acceso detectado (baños pequeños y zonas periféricas inciertas). Ninguna entrada exterior identificada con confianza.
- Análisis total de 2 a 3 s en una máquina local, incluido ~1 s de inferencia en CPU.

Tabla 2. Planos sintéticos con verdad de terreno (generador con semilla fija, backend determinista sin ML; fuente: [`PR-134-DEVELOP-2026-11-10.md`](PR-134-DEVELOP-2026-11-10.md)):

| Semilla | Formato | Espacios / reales | Puertas / reales | Componentes |
|---|---|---:|---:|---:|
| 321 | SVG | 9 / 16 | 8 / 16 | 1 |
| 322 | SVG | 23 / 12 | 12 / 12 | 4 |
| 323 | SVG | 2 / 14 | 1 / 14 | 1 |
| 321 | PNG | 17 / 16 | 16 / 16 | 1 |
| 322 | PNG | 13 / 12 | 12 / 12 | 1 |
| 323 | PNG | 15 / 14 | 14 / 14 | 1 |

Rasterizar los mismos SVG mejoró conteos y conectividad en esta muestra pequeña. No demuestra que PNG sea mejor en general: cambia el camino de ingesta y no hay corpus independiente.

## 2. Prototipo de propuesta editable

- **Servicio:** `structural-mapper` (FastAPI, puerto 8010), levantado con el Docker Compose de la API.
- **Flujo:** documento → ingesta (PyMuPDF para vectorial, raster en otro caso) → percepción → reconstrucción de muros y aperturas → espacios → área transitable → grafo semántico → reparación de conectividad → validación.
- **Integración:** `POST /api/v1/sites/{siteId}/floors/{floorId}/plans/versions/{versionId}/interpretation` llama al mapper y proyecta el resultado a unidades del plano original.
- **Revisión:** el editor web importa nodos y conexiones tipados al borrador, marca ambientes sin etiqueta y conserva destinos al regenerar. El mapper nunca publica un mapa; las aperturas que no se asocian con certeza llegan con `review_required: true` y no crean aristas hasta revisarse.
- **Modelo:** descargado del release público `rumbo-models` y verificado por SHA-256 al construir la imagen.

## 3. Fallos y calidad de detección

Fallos observados y documentados:

1. La percepción clásica por oscuridad y grosor puede perder tabiques finos y aceptar símbolos gruesos como muros; en el raster amueblado fusionó los consultorios.
2. En SVG sin anotar, la recuperación fue desde 2 de 14 hasta 23 sobre 12 espacios reales (tabla 2). El estado `ok` del validador no garantiza que todos los espacios reconocidos estén conectados: la semilla 322 produjo 4 componentes con todas las aristas contenidas.
3. Sin evidencia de puerta, el contacto con el cerramiento exterior podía convertirse en entrada; ahora se exige evidencia de entrada.
4. Sin calibración la escala se estima suponiendo un muro de 0,20 m. La API envía la escala calibrada al mapper para raster y PDF, pero no para SVG, que se dimensiona a sí mismo.
5. Solo se procesa la primera página de un PDF vectorial, y las imágenes raster no incluyen OCR, por lo que los nombres de ambientes no se extraen de ellas.
6. El grafo simple conserva una sola arista entre dos nodos, de modo que pueden perderse recorridos alternativos entre el mismo par.

Límites de la evidencia: las mediciones son un conjunto sintético rectilíneo y un único raster real. No hay anotación independiente de muros, aperturas y regiones transitables, ni medición de falsas conexiones, accesos omitidos, éxito de rutas ni tiempo de corrección. El tiempo de análisis viene de una sola máquina y no es un compromiso de latencia. La verificación en navegador real no se completó.

## 4. Viabilidad y técnica recomendada

- **Usar** lectura directa para SVG anotado y `structural-mapper` con CubiCasa5k para el resto, siempre como propuesta sujeta a revisión en el editor.
- **No prometer** generación sin revisión, cumplimiento de accesibilidad ni alineación AR a partir de esta salida; el área transitable es una aproximación geométrica del dibujo.
- **Licencia:** los pesos de CubiCasa5k son CC BY-NC 4.0. Su uso es válido en este proyecto académico no comercial y debe reevaluarse antes de cualquier uso comercial.
- **Mantener** BuildingCV solo como alternativa opcional (`--profile mapper-ml`); el modelo ONNX es más liviano y no requiere PyTorch.

## 5. Siguientes pasos

1. Reunir un corpus de planos de clínicas autorizados, con referencia revisada de muros, aperturas, espacios y rutas, y partición por edificio.
2. Medir precisión y cobertura de espacios y puertas, conexiones falsas, destinos alcanzables y minutos de corrección por plano.
3. Corregir los límites 4 a 6 de la sección 3 y medir de nuevo.
4. Comparar un modelo compacto exportado a ONNX frente al actual con las mismas métricas.
