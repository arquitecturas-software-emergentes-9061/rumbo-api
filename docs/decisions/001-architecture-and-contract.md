# ADR 001 — Persistencia, modelo publicado y rutas

Estado: aceptada para el diseño; persistencia y publicación controlada de `planning` implementadas.

## Contexto

El informe de Rumbo define siete bounded contexts en un Web Services API. Sus diagramas C4 y el diseño táctico del capítulo V describen una persistencia relacional en PostgreSQL, con Redis para caché y RabbitMQ para eventos. La sección 7.1.1, en cambio, menciona MongoDB Compass y Atlas. `planning` ya dispone de un adaptador PostgreSQL para borradores; `navigation` sigue funcionando con datos del perfil `demo`.

El Sprint Backlog 1 exige A* con pesos persistidos y penalizaciones verticales (TS-12), y una API de rutas `GET` con parámetros `from` y `to` (TS-13). El código actual usa Dijkstra y `POST /api/v1/navigations/route`.

## Decisiones

1. PostgreSQL será la persistencia principal de los datos duraderos. La mención de MongoDB/Atlas en 7.1.1 no rige la implementación del backend mientras contradiga el diseño táctico. Redis y RabbitMQ están previstos, pero se incorporarán únicamente cuando una tarea concreta los requiera. Esta decisión no autoriza instalar dependencias ni servicios: se conserva la aprobación previa indicada en el `AGENTS.md` local.
2. `planning` es dueño de clínicas, sedes, pisos, planos, escalas, grafos, destinos, restricciones y balizas. Publica una versión identificable del modelo indoor. `navigation` accede a una instantánea `PUBLISHED` mediante un puerto de solo lectura y no consulta borradores ni modifica la persistencia interna de `planning`. La instantánea identifica sede y versión y aporta pisos, coordenadas y escala, nodos, conexiones con pesos y restricciones, destinos y obstáculos. El contrato detallado está en [API-CONTRACT.md](../API-CONTRACT.md).
3. El objetivo de TS-12 es A* sobre los pesos persistidos más las penalizaciones verticales. La heurística debe ser una cota inferior del costo restante: distancia euclidiana convertida a la misma unidad que los pesos cuando esa propiedad esté garantizada; entre pisos, cota cero hasta disponer de una cota vertical válida. Así se conserva la ruta de menor costo.
4. El contrato objetivo de TS-13 es `GET /api/v1/sites/{siteId}/routes?from={nodeId}&to={nodeId}`, con `accessibleOnly` opcional. Conserva el método y los identificadores de nodo del backlog; la sede se expresa en la ruta para acotar el modelo consultado. El `POST` actual es provisional. TS-12 y TS-13 se completarán al implementar y probar sus criterios de aceptación.

## Consecuencias y trabajo pendiente

- La persistencia de borradores y la publicación transaccional de `planning` usan PostgreSQL y Flyway. El endpoint de publicación requiere habilitación explícita mientras no exista autorización por sede. El adaptador entre `planning` y `navigation`, A* y el endpoint de rutas siguen pendientes.
- La autorización por sede debe resolverse antes de exponer modelos reales a consumidores externos; incluir `siteId` en la URL no sustituye esa autorización.
- La publicación debe dejar la versión anterior disponible mientras se valida un borrador nuevo; `navigation` solo recibirá versiones publicadas.
- Los detalles de errores, respuesta y modelo publicado se mantienen en [API-CONTRACT.md](../API-CONTRACT.md), para evitar duplicarlos aquí.

## Fuentes

- Informe `AVANCE_TF_1ASI0728_202620_TF.pdf` del proyecto, capítulos IV–V, sección 7.1.1 y Sprint Backlog 1 (TS-12 y TS-13), ubicado fuera de este repositorio.
- `README.md` y `src/main/java/com/uitopic/rumbo/navigation/` para el estado implementado al tomar esta decisión.
