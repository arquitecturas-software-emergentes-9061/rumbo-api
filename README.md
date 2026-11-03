# RUMBO API · UI-Topic · 2026-20

Backend académico oficial **preparado localmente**, pendiente de publicarse en la organización
`arquitecturas-software-emergentes-9061`. **No utilizar el repositorio `report` como API**.

## Stack y arquitectura
- Java 21 / Spring Boot 3.5.6 / REST + OpenAPI.
- Domain-Driven Design; cada bounded context con capas `domain`, `application`,
  `interfaces` (plural: `interface` es una palabra reservada Java) e `infrastructure`.
- PostgreSQL es la persistencia principal elegida y ya guarda borradores y versiones
  publicadas de `planning`.
  Redis y RabbitMQ se incorporarán cuando una tarea concreta los requiera; véase
  [ADR 001](docs/decisions/001-architecture-and-contract.md) y
  [ADR 002](docs/decisions/002-site-map-and-floor-ownership.md).
- `planning` es dueño del modelo indoor y publica versiones identificables. `navigation`
  ya lee el núcleo espacial **PUBLISHED** mediante un puerto propio y un adaptador ACL;
  destinos, restricciones y obstáculos reales siguen pendientes. El adaptador `demo`
  actual es temporal.

## GitFlow obligatorio
`main` (core initial commit) → `develop` → `feat/<bounded-context>-<funcionalidad>`.
Solo fusionar feature → develop después de pasar CI/Maven/tests/revisión; releases desde
`develop` y luego merge de `release/*` a `main`. Usar Conventional Commits.

## Estado de implementación
- `planning` ya contiene coordenadas, escala, nodos, conexiones y un agregado de borrador
  `IndoorMap` con validación de referencias, duplicados y conectividad. Flyway y el adaptador
  JPA guardan y recuperan borradores completos en PostgreSQL. La publicación transaccional
  y su endpoint `PATCH` están implementados con habilitación explícita. `GET` y `PUT` permiten
  consultar y reemplazar nodos y conexiones de un borrador existente, también con habilitación
  explícita. El núcleo espacial publicado ya se consulta desde `navigation` sin exponer
  borradores ni activar aún rutas PostgreSQL.
- Rama `main` y `develop`: base de Spring Boot, CI y convenciones.
- Rama `feat/navigation-destination-routing`: primer incremento de `navigation`.
- `PR-214`: directorio y búsqueda acento-insensible de destinos por nombre/especialidad/tipo.
- Motor Dijkstra derivado de reglas existentes del prototipo FastAPI:
  rutas más cortas, filtro de accesibilidad y exclusión de aristas que cruzan paredes.
- `POST /api/v1/navigations/route` es el endpoint provisional implementado; TS-12 exige
  A* y el contrato objetivo de TS-13 es `GET /api/v1/sites/{siteId}/routes`.
- Contratos REST y Spring MockMvc; dominio sin dependencias Spring.
- Datos de ejemplo disponibles **solo con perfil `demo`**. La publicación de `planning`
  requiere PostgreSQL y permanece desactivada hasta habilitarla para pruebas controladas.
  Tampoco hay subida de planos ni BLE reales. No
  declarar PR-214 terminado hasta integrar app cliente.

## Ejecutar tras instalar JDK 21 y Maven
```sh
mvn clean verify
mvn spring-boot:run -Dspring-boot.run.profiles=demo
```
El perfil `demo` no requiere PostgreSQL. La prueba de persistencia se activa con
`RUMBO_TEST_DB_URL`, `RUMBO_TEST_DB_USER` y `RUMBO_TEST_DB_PASSWORD` apuntando a una base
PostgreSQL 16 de prueba; CI la ejecuta con un servicio temporal. Para una conexión de la
aplicación con el perfil `postgres`, se usan `RUMBO_DB_URL`, `RUMBO_DB_USER` y
`RUMBO_DB_PASSWORD`. Para probar localmente la publicación, activar también
`RUMBO_PLANNING_PUBLICATION_ENABLED=true`; para probar la edición, activar
`RUMBO_PLANNING_EDITING_ENABLED=true`. No exponer esos endpoints sin autorización por sede.
Véase [Contrato API](docs/API-CONTRACT.md) para métodos, cuerpos y errores.

Endpoints implementados de ejemplo (perfil `demo`):
```sh
curl -s 'http://localhost:8080/api/v1/navigations/destinations?siteId=00000000-0000-4000-8000-000000000001&query=cardiologia'
curl -s -X POST http://localhost:8080/api/v1/navigations/route \
  -H 'Content-Type: application/json' -d '{"siteId":"00000000-0000-4000-8000-000000000001","startFloorId":"00000000-0000-4000-8000-000000000002","startNodeId":"00000000-0000-4000-8000-000000000010","destinationFloorId":"00000000-0000-4000-8000-000000000002","destinationNodeId":"00000000-0000-4000-8000-000000000011","accessibleOnly":true}'
```
Swagger UI (demo): `http://localhost:8080/swagger-ui/index.html`.
Health check: `http://localhost:8080/actuator/health`.

## Flujo de contribución en el repositorio oficial
Las ramas `main` y `develop` contienen el núcleo del API. Las nuevas funcionalidades se
proponen desde `develop` mediante ramas `feat/<bounded-context>-<funcionalidad>`,
acompañadas de tests y pull request. Solo se fusionan tras aprobar Maven/CI y revisión.
El código de esta rama todavía no es una release ni está fusionado a `develop`.

## Documentación y decisiones pendientes
- [ADR 001](docs/decisions/001-architecture-and-contract.md): persistencia elegida,
  frontera `planning`/`navigation`, A* y ruta REST objetivo.
- [Contrato API](docs/API-CONTRACT.md): estado provisional y contrato objetivo de modelos,
  destinos y rutas. El contrato objetivo aún no está implementado.
- Pendientes: autorización por sede, detalle de publicación de planos y seguridad,
  stack móvil (Flutter frente a Unity/AR) y cotejo de la web Angular con los mockups.

Fuentes: Informe TF 2026-20, capítulo V (BC Navegación), Jira `PR-214` y MVP
personal `jahazielgg/rumbo-platform`. No se ha migrado íntegramente todo el MVP.
