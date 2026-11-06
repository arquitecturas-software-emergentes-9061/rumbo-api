# RUMBO API · UI-Topic · 2026-20

Backend académico oficial **preparado localmente**, pendiente de publicarse en la organización
`arquitecturas-software-emergentes-9061`. **No utilizar el repositorio `report` como API**.

## Stack y arquitectura
- Java 21 / Spring Boot 3.5.6 / REST + OpenAPI.
- Domain-Driven Design; cada bounded context con capas `domain`, `application`,
  `interfaces` (plural: `interface` es una palabra reservada Java) e `infrastructure`.
- `navigation` organiza sus modelos y consultas en `domain/model`, puertos en
  `domain/repositories` y `domain/services`, casos de uso en
  `application/internal/queryservices`, HTTP en `interfaces/rest` y adaptadores
  de lectura en `infrastructure/acl`. El perfil `demo` tiene un adaptador propio
  en `infrastructure/demo`.
- PostgreSQL es la persistencia principal elegida y ya guarda borradores y versiones
  publicadas de `planning`.
  Redis ya se usa para sesiones y caché de métricas; RabbitMQ sigue pendiente. Véase
  [ADR 001](docs/decisions/001-architecture-and-contract.md) y
  [ADR 002](docs/decisions/002-site-map-and-floor-ownership.md).
- `planning` gestiona sedes, inventario BLE, referencias de escala y vistas previas
  SVG. Sus casos de uso acceden a PostgreSQL por puertos de repositorio; IAM
  consulta la propiedad de sede mediante una fachada pública de Planning.
- `planning` es dueño del modelo indoor y publica versiones identificables. `navigation`
  ya lee el núcleo espacial **PUBLISHED** y busca destinos médicos persistidos mediante
  puertos propios y adaptadores ACL. Las conexiones publicadas incluyen un estado de
  accesibilidad declarado; la validación de obstáculos sigue pendiente. El adaptador
  `demo` actual es temporal.

## GitFlow obligatorio
`main` (core initial commit) → `develop` → `feat/<bounded-context>-<funcionalidad>`.
Solo fusionar feature → develop después de pasar CI/Maven/tests/revisión; releases desde
`develop` y luego merge de `release/*` a `main`. Usar Conventional Commits.

## Estado de implementación
- `planning` ya contiene coordenadas, escala, nodos, conexiones y un agregado de borrador
  `IndoorMap` con validación de referencias, duplicados y conectividad. Flyway y el adaptador
  JPA guardan y recuperan borradores completos en PostgreSQL. La publicación transaccional
  y su endpoint `PATCH` están implementados. `POST /versions` crea un nuevo borrador
  copiando la versión publicada vigente sin cambiarla; `GET` y `PUT` permiten consultar y reemplazar
  nodos, conexiones y destinos de un borrador existente. El `PUT` exige las tres colecciones y el `If-Match` devuelto como
  `ETag` por el `GET`, para evitar borrados por omisión y sobrescrituras entre editores.
  El núcleo espacial publicado ya se consulta desde `navigation` sin exponer
  borradores. Los endpoints se muestran en el arranque local predeterminado.
- El directorio médico de `planning` guarda entradas de médicos, especialidades y servicios
  vinculadas a nodos destino de una versión. La búsqueda indexada de `navigation` solo devuelve
  entradas de la versión publicada vigente de una sede. IAM protege los endpoints reales
  con JWT, rol de administrador y propiedad temporal de la sede.
- Las conexiones verticales guardan una penalización de costo separada del peso y la distancia
  física. Cada conexión guarda `UNKNOWN`, `ACCESSIBLE` o `INACCESSIBLE` (por defecto `UNKNOWN`).
  `navigation` calcula rutas A* sobre el grafo publicado y respeta pesos, penalizaciones,
  dirección y el filtro `accessibleOnly`. El `GET` de rutas PostgreSQL requiere JWT y
  propiedad de sede; falta validar obstáculos.
- Rama `main` y `develop`: base de Spring Boot, CI y convenciones.
- Rama `feat/navigation-destination-routing`: primer incremento de `navigation`.
- `PR-214`: directorio y búsqueda acento-insensible de destinos por nombre/especialidad/tipo.
- El motor Dijkstra del perfil `demo`, derivado del prototipo FastAPI, ofrece rutas de ejemplo
  con filtro de accesibilidad y exclusión de aristas que cruzan paredes.
- `POST /api/v1/navigations/route` sigue como endpoint provisional sobre datos `demo`.
  `GET /api/v1/sites/{siteId}/routes?from={nodeId}&to={nodeId}` consulta la versión
  publicada con A* en el arranque predeterminado. La respuesta
  ordena nodos y enlaces e identifica los tramos entre pisos en `verticalSections[]`.
- Contratos REST y Spring MockMvc; dominio sin dependencias Spring.
- El arranque predeterminado usa `postgres,local`: muestra los endpoints de `planning`,
  `navigation`, IAM, beacons y analytics en Swagger. También crea ejemplos persistidos de
  un borrador y una versión publicada. La aplicación requiere PostgreSQL y Redis.
  Planning puede registrar y recuperar versiones de archivos originales por piso.
  El escaneo BLE desde dispositivos queda fuera de la API. No declarar PR-214 terminado
  hasta integrar app cliente.

## Ejecutar tras instalar JDK 21 y Maven
La configuración Spring compartida está en un único
[`application.properties`](src/main/resources/application.properties), sin secciones por
perfil. Los perfiles predeterminados son `postgres,local`; no hace falta llenar **Active
profiles** en IntelliJ. Si una configuración anterior indica `demo`, quítalo para usar
los perfiles predeterminados. Las credenciales se proporcionan por variables de entorno.

```sh
mvn clean verify
```
Las pruebas de persistencia de la rama original se activan con
`RUMBO_TEST_DB_URL`, `RUMBO_TEST_DB_USER` y `RUMBO_TEST_DB_PASSWORD` apuntando a una base
PostgreSQL de prueba; las nuevas pruebas de IAM usan Testcontainers. Para arrancar la aplicación
se usan `RUMBO_DB_URL`, `RUMBO_DB_USER`, `RUMBO_DB_PASSWORD` y `JWT_SECRET`.
Redis se configura con `REDIS_HOST` y `REDIS_PORT`. Los cuatro endpoints
controlados por banderas están activos por defecto y el servidor escucha solo en
`127.0.0.1`. `accessibleOnly=true` excluye conexiones sin accesibilidad confirmada.
La autorización actual verifica el dueño de la sede; la membresía de varios administradores
y las reglas de obstáculos siguen pendientes.
Véase [Contrato API](docs/API-CONTRACT.md) para métodos, cuerpos y errores.

Endpoints provisionales de ejemplo, visibles junto con los de PostgreSQL:
```sh
curl -s 'http://localhost:8080/api/v1/navigations/destinations?siteId=00000000-0000-4000-8000-000000000001&query=cardiologia'
curl -s -X POST http://localhost:8080/api/v1/navigations/route \
  -H 'Content-Type: application/json' -d '{"siteId":"00000000-0000-4000-8000-000000000001","startFloorId":"00000000-0000-4000-8000-000000000002","startNodeId":"00000000-0000-4000-8000-000000000010","destinationFloorId":"00000000-0000-4000-8000-000000000002","destinationNodeId":"00000000-0000-4000-8000-000000000011","accessibleOnly":true}'
```
Swagger UI: `http://localhost:8080/swagger-ui/index.html`.
Health check: `http://localhost:8080/actuator/health`.

### Arrancar Swagger con todas las operaciones (PowerShell)

El arranque predeterminado registra las operaciones de los contextos integrados y conserva
las dos provisionales etiquetadas **Demo navigation**. Solo escucha en `127.0.0.1`.
Se necesitan PostgreSQL 17, Redis 7 y una clave JWT privada:

```powershell
$env:RUMBO_DB_USER = "rumbo"
$env:RUMBO_DB_PASSWORD = Read-Host -MaskInput "Contraseña local de PostgreSQL"
$env:DB_PASSWORD = $env:RUMBO_DB_PASSWORD
$env:RUMBO_DB_URL = "jdbc:postgresql://localhost:5432/rumbo"
$env:JWT_SECRET = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
docker compose up -d --wait
mvn spring-boot:run
```

Abrir `http://localhost:8080/swagger-ui/index.html`. Flyway crea el esquema al arrancar.
Usa una base **nueva**: la base PostgreSQL 16 del trabajo anterior conserva el historial
Flyway `V1`–`V7`, incompatible con las versiones fechadas que llegaron de `develop`.
No ejecutes `repair` ni borres ese volumen sin migrar o respaldar sus datos.
El perfil local añade una vez un mapa publicado y un borrador, sin reemplazar las ediciones
posteriores. IAM exige iniciar sesión y ser dueño de la sede para probar las operaciones
reales. Los mapas fijos del perfil local aún no tienen dueño IAM, así que sirven como datos
de referencia pero sus rutas protegidas responden `403` hasta asociarlos a una cuenta.
Los identificadores son:

| Prueba | Identificadores y parámetros |
|---|---|
| Ruta publicada | `siteId=10000000-0000-4000-8000-000000000001`, `from=10000000-0000-4000-8000-000000000006`, `to=10000000-0000-4000-8000-000000000007` |
| Directorio publicado | El mismo `siteId`, `searchTerm=cardio` |
| Crear sucesor del mapa publicado | `siteId=10000000-0000-4000-8000-000000000001`, `mapId=10000000-0000-4000-8000-000000000002`, cuerpo `{"sourceVersionId":"10000000-0000-4000-8000-000000000003"}` |
| Leer o editar borrador | `siteId=20000000-0000-4000-8000-000000000001`, `mapId=20000000-0000-4000-8000-000000000002`, `versionId=20000000-0000-4000-8000-000000000003` |
| Publicar borrador | Los mismos tres IDs, cuerpo `{"status":"PUBLISHED"}` y `Content-Type: application/merge-patch+json` |
| Endpoints Demo | Usar los IDs del ejemplo `demo` anterior; no consultan PostgreSQL |

El `POST` de sucesor responde `201` con un nuevo `modelVersionId`, un `Location` para
leer su grafo y un `ETag` inicial. Para el `PUT`, leer primero el borrador y enviar su
`ETag` en `If-Match`; el cuerpo debe contener `nodes`, `connections` y `destinations`
completos. Publicar el sucesor archiva la versión publicada anterior en la misma
transacción. Publicar el borrador de ejemplo lo convierte en `PUBLISHED`, por lo que
su `GET`/`PUT` de borrador dejará de funcionar; después se puede crear otro sucesor.
Detener los servicios cuando termines con `docker compose down`; el volumen
conserva los datos y la contraseña elegida en el primer inicio. Usa esa misma contraseña
al reiniciar. No subir contraseñas ni archivos `.env` al repositorio.

## Flujo de contribución en el repositorio oficial
Las ramas `main` y `develop` contienen el núcleo del API. Las nuevas funcionalidades se
proponen desde `develop` mediante ramas `feat/<bounded-context>-<funcionalidad>`,
acompañadas de tests y pull request. Solo se fusionan tras aprobar Maven/CI y revisión.
El código de esta rama todavía no es una release ni está fusionado a `develop`.

## Documentación y decisiones pendientes
- [ADR 001](docs/decisions/001-architecture-and-contract.md): persistencia elegida,
  frontera `planning`/`navigation`, A* y ruta REST objetivo.
- [ADR 003](docs/decisions/003-draft-graph-editing-and-regeneration.md): guardado
  completo del borrador, `If-Match` y reconciliación pendiente con `US-09`.
- [ADR 004](docs/decisions/004-local-default-api-visibility.md): los endpoints
  visibles en el arranque local predeterminado, limitado a `127.0.0.1`.
- [ADR 005](docs/decisions/005-successor-draft-versions.md): creación de un borrador
  sucesor desde la versión publicada y archivo atómico al publicar el reemplazo.
- [Contrato API](docs/API-CONTRACT.md): contratos implementados, habilitación y requisitos
  pendientes para modelos, destinos y rutas.
- Pendientes: membresía de sede para varios administradores, validación de obstáculos,
  detalle de publicación de planos y seguridad,
  stack móvil (Flutter frente a Unity/AR) y cotejo de la web Angular con los mockups.

Fuentes: Informe TF 2026-20, capítulo V (BC Navegación), Jira `PR-214` y MVP
personal `jahazielgg/rumbo-platform`. No se ha migrado íntegramente todo el MVP.
