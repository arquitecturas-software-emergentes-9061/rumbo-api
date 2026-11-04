# ADR 001 — Persistencia, modelo publicado y rutas

Estado: aceptada; persistencia, publicación, A* y ruta GET implementados.
La habilitación local se precisó en [ADR 004](004-local-default-api-visibility.md).

## Contexto

El informe de Rumbo define siete bounded contexts en un Web Services API. Sus diagramas C4 y el diseño táctico del capítulo V describen una persistencia relacional en PostgreSQL, con Redis para caché y RabbitMQ para eventos. La sección 7.1.1, en cambio, menciona MongoDB Compass y Atlas. `planning` ya dispone de adaptadores PostgreSQL para borradores y versiones publicadas. `navigation` usa datos `demo` para la ruta provisional y modelos publicados para las lecturas reales habilitadas explícitamente.

El Sprint Backlog 1 exige A* con pesos persistidos y penalizaciones verticales (TS-12), y una API de rutas `GET` con parámetros `from` y `to` (TS-13). El endpoint provisional `POST /api/v1/navigations/route` sigue usando Dijkstra sobre datos `demo`; el cálculo A* y el GET del modelo publicado existen para pruebas controladas.

## Decisiones

1. PostgreSQL será la persistencia principal de los datos duraderos. La mención de MongoDB/Atlas en 7.1.1 no rige la implementación del backend mientras contradiga el diseño táctico. Redis se incorporó para sesiones y caché de métricas; RabbitMQ sigue previsto. Esta decisión no autoriza instalar dependencias ni servicios: se conserva la aprobación previa indicada en el `AGENTS.md` local.
2. `planning` es dueño del modelo indoor, sus sedes, pisos, planos, escalas, grafos, destinos, inventario BLE y restricciones. Tras integrar `develop`, el inventario BLE, la calibración y la interpretación SVG se organizaron dentro de las capas de `planning`; el escaneo BLE en el dispositivo queda fuera de este backend. IAM comprueba la propiedad de una sede mediante una fachada pública de `planning`. `planning` publica una versión identificable del modelo indoor. `navigation` accede a una instantánea `PUBLISHED` mediante un puerto de solo lectura y no consulta borradores ni modifica la persistencia interna de `planning`. La instantánea identifica sede y versión y aporta pisos, coordenadas y escala, nodos, conexiones con pesos y restricciones, y destinos. Los obstáculos siguen pendientes. El contrato detallado está en [API-CONTRACT.md](../API-CONTRACT.md).
3. TS-12 usa A* sobre los pesos persistidos más las penalizaciones verticales, separadas de la distancia física. En un único plano, la heurística es la distancia euclidiana multiplicada por el menor costo observado por metro geométrico entre sus conexiones; la desigualdad triangular garantiza que no sobrestima el costo restante. Con varios pisos o planos usa cota cero hasta disponer de una cota válida. Así se conserva la ruta de menor costo.
4. El contrato de TS-13 es `GET /api/v1/sites/{siteId}/routes?from={nodeId}&to={nodeId}`, con `accessibleOnly` opcional. Conserva el método y los identificadores de nodo del backlog; el equipo aceptó expresar la sede en la ruta para acotar el modelo consultado, aunque el backlog inicial describe una URL global. No se añade un alias global. La respuesta ordena nodos y enlaces e identifica los tramos entre pisos como proyección de los enlaces calculados. El `POST` actual es provisional. La ruta GET se habilita con `postgres`, exige JWT de administrador y verifica que la cuenta sea dueña de la sede.
5. Una conexión declara `UNKNOWN`, `ACCESSIBLE` o `INACCESSIBLE`, con `UNKNOWN` como valor predeterminado y para filas anteriores. Cuando se solicita accesibilidad, A* usa únicamente conexiones `ACCESSIBLE`. La declaración no verifica todavía el despeje de obstáculos.

## Consecuencias y trabajo pendiente

- La persistencia de borradores, la publicación transaccional y el directorio médico usan PostgreSQL y Flyway. Los adaptadores entre `planning` y `navigation` exponen el núcleo espacial, destinos y accesibilidad declarada; A* y el GET calculan rutas de la versión publicada vigente. El modelado y la validación de obstáculos siguen pendientes.
- La autorización actual verifica el dueño de la sede mediante IAM. Falta la membresía de varios administradores y la validación de obstáculos antes de abrir las rutas a consumidores externos.
- El merge conservó las migraciones fechadas de `develop`. Las bases locales con historial Flyway `V1`–`V7` requieren una migración de datos planificada; no se debe ejecutar `repair` ni borrar su volumen como atajo.
- La publicación debe dejar la versión anterior disponible mientras se valida un borrador nuevo; `navigation` solo recibirá versiones publicadas.
- Los detalles de errores, respuesta y modelo publicado se mantienen en [API-CONTRACT.md](../API-CONTRACT.md), para evitar duplicarlos aquí.

## Fuentes

- Informe `AVANCE_TF_1ASI0728_202620_TF.pdf` del proyecto, capítulos IV–V, sección 7.1.1 y Sprint Backlog 1 (TS-12 y TS-13), ubicado fuera de este repositorio.
- `README.md` y `src/main/java/com/uitopic/rumbo/navigation/` para el estado implementado al tomar esta decisión.
