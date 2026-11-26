# PR-157 / US-10 — Nombres en planos raster

Completa la extracción de nombres pendiente sin reemplazar el mapper/refactor del equipo ni la
reconciliación de destinos ya integrada en Web. La rama parte de `origin/develop` y es independiente
de la recuperación de contraseña.

## Comportamiento

- PNG, JPEG y PDF escaneados sin texto incorporado: Tesseract spa+eng lee rótulos y coordenadas.
- PDF/SVG con texto incorporado: mantiene el lector existente, sin duplicar rótulos mediante OCR.
- El clasificador existente recibe esos nombres: por ejemplo, «Ascensor» se reconoce como conector
  vertical. No añade otro modelo de geometría ni un contexto DDD nuevo.
- Descarta rótulos de baja confianza y medidas sueltas. Un fallo, ausencia de motor o tiempo agotado
  conserva la geometría y añade una advertencia visible de revisión manual.
- No guarda/publica la propuesta ni altera destinos. El editor conserva su conciliación por nombre
  único, IDs, categoría y asociación del destino, con revisión cuando hay ambigüedad.

El formato de la respuesta mantiene sus campos; `parser_version` sube a `5` y agrega diagnósticos OCR.
Las imágenes Docker instalan Tesseract y los idiomas español/inglés. La CI existente se conserva:
las pruebas de OCR real se omiten explícitamente cuando el runner no dispone del motor; se ejecutan
completas en el entorno Docker documentado, además de las regresiones habituales de CI.
También lo incluye la nueva imagen unificada API + mapper integrada por el equipo el 26 de noviembre;
se conserva su activación mediante `STRUCTURAL_MAPPER_EMBEDDED`, sin cambiar su despliegue.
Configuración, límites y referencias técnicas: `structural-mapper/README.md`.

## Evidencia ejecutada — 25 de noviembre de 2026

- Suite completa del mapper con Tesseract 5.5.0 real: **52 pruebas, 0 fallos, 0 omisiones**.
  Incluye PNG, JPEG, PDF escaneado sin capa de texto, asignación a ambientes, clasificación vertical,
  texto vectorial conservado, ausencia de texto y fallos/timeout del motor.
- HTTP real `POST /analyze` + API autenticada: carga del archivo, persistencia PostgreSQL, calibración
  y `POST /api/v1/sites/{siteId}/floors/{floorId}/plans/versions/{versionId}/interpretation`.
  En cada uno de los tres formatos devuelve dos zonas: «Consultorio 101» y «Ascensor», con escala
  `CONFIRMED`. Archivos generados con texto rasterizado, sin respuestas simuladas del mapper.
- Regresión del editor Web integrado, sin modificarlo: **31 pruebas** de `graph-editing.spec.ts`,
  incluidas reconciliación/conservación y rechazo de nombres ambiguos, todas correctas.
- Este incremento no cambia Java, contratos REST, persistencia ni migraciones Flyway. La suite Maven
  de CI permanece obligatoria para el PR; los resultados anteriores no se presentan como Maven ejecutado
  sobre esta rama ni como una validación de exactitud en planos arbitrarios.

Las muestras comprueban el recorrido técnico; un plano real pequeño, borroso, manuscrito, rotado o con
etiquetado mixto puede requerir corrección manual. La propuesta requiere revisión antes de guardarse.
