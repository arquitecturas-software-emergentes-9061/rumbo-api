# ADR 001 — Persistencia, modelo publicado y rutas

Estado: aceptada; persistencia, publicación controlada, A* y ruta GET habilitable implementados.

## Contexto

El informe de Rumbo define siete bounded contexts en un Web Services API. Sus diagramas C4 y el diseño táctico del capítulo V describen una persistencia relacional en PostgreSQL, con Redis para caché y RabbitMQ para eventos. La sección 7.1.1, en cambio, menciona MongoDB Compass y Atlas. `planning` ya dispone de adaptadores PostgreSQL para borradores y versiones publicadas. `navigation` usa datos `demo` para la ruta provisional y modelos publicados para las lecturas reales habilitadas explícitamente.

El Sprint Backlog 1 exige A* con pesos persistidos y penalizaciones verticales (TS-12), y una API de rutas `GET` con parámetros `from` y `to` (TS-13). El endpoint provisional `POST /api/v1/navigations/route` sigue usando Dijkstra sobre datos `demo`; el cálculo A* y el GET del modelo publicado existen para pruebas controladas.

## Decisiones

1. PostgreSQL será la persistencia principal de los datos duraderos. La mención de MongoDB/Atlas en 7.1.1 no rige la implementación del backend mientras contradiga el diseño táctico. Redis y RabbitMQ están previstos, pero se incorporarán únicamente cuando una tarea concreta los requiera. Esta decisión no autoriza instalar dependencias ni servicios: se conserva la aprobación previa indicada en el `AGENTS.md` local.
2. `planning` es dueño de clínicas, sedes, pisos, planos, escalas, grafos, destinos, restricciones y balizas. Publica una versión identificable del modelo indoor. `navigation` accede a una instantánea `PUBLISHED` mediante un puerto de solo lectura y no consulta borradores ni modifica la persistencia interna de `planning`. La instantánea identifica sede y versión y aporta pisos, coordenadas y escala, nodos, conexiones con pesos y restricciones, destinos y obstáculos. El contrato detallado está en [API-CONTRACT.md](../API-CONTRACT.md).
3. TS-12 usa A* sobre los pesos persistidos más las penalizaciones verticales, separadas de la distancia física. En un único plano, la heurística es la distancia euclidiana multiplicada por el menor costo observado por metro geométrico entre sus conexiones; la desigualdad triangular garantiza que no sobrestima el costo restante. Con varios pisos o planos usa cota cero hasta disponer de una cota válida. Así se conserva la ruta de menor costo.
4. El contrato de TS-13 es `GET /api/v1/sites/{siteId}/routes?from={nodeId}&to={nodeId}`, con `accessibleOnly` opcional. Conserva el método y los identificadores de nodo del backlog; la sede se expresa en la ruta para acotar el modelo consultado, aunque el backlog describe una URL global. El `POST` actual es provisional. La ruta GET se habilita solo con `postgres` y una bandera explícita hasta disponer de autorización por sede.
5. Una conexión declara `UNKNOWN`, `ACCESSIBLE` o `INACCESSIBLE`, con `UNKNOWN` como valor predeterminado y para filas anteriores. Cuando se solicita accesibilidad, A* usa únicamente conexiones `ACCESSIBLE`. La declaración no verifica todavía el despeje de obstáculos.

## Consecuencias y trabajo pendiente

- La persistencia de borradores, la publicación transaccional y el directorio médico usan PostgreSQL y Flyway. Los endpoints reales requieren habilitación explícita mientras no exista autorización por sede. Los adaptadores entre `planning` y `navigation` exponen el núcleo espacial, destinos y accesibilidad declarada; A* y el GET calculan rutas de la versión publicada vigente. El modelado y la validación de obstáculos siguen pendientes.
- La autorización por sede y la validación de obstáculos deben resolverse antes de exponer rutas reales a consumidores externos; incluir `siteId` en la URL no sustituye esa autorización.
- La publicación debe dejar la versión anterior disponible mientras se valida un borrador nuevo; `navigation` solo recibirá versiones publicadas.
- Los detalles de errores, respuesta y modelo publicado se mantienen en [API-CONTRACT.md](../API-CONTRACT.md), para evitar duplicarlos aquí.

## Fuentes

- Informe `AVANCE_TF_1ASI0728_202620_TF.pdf` del proyecto, capítulos IV–V, sección 7.1.1 y Sprint Backlog 1 (TS-12 y TS-13), ubicado fuera de este repositorio.
- `README.md` y `src/main/java/com/uitopic/rumbo/navigation/` para el estado implementado al tomar esta decisión.
