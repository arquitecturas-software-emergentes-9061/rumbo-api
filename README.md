# RUMBO API · UI-Topic · 2026-20

Backend académico oficial **preparado localmente**, pendiente de publicarse en la organización
`arquitecturas-software-emergentes-9061`. **No utilizar el repositorio `report` como API**.

## Stack y arquitectura
- Java 21 / Spring Boot 3.5.6 / REST + OpenAPI.
- Domain-Driven Design; cada bounded context con capas `domain`, `application`,
  `interfaces` (plural: `interface` es una palabra reservada Java) e `infrastructure`.
- PostgreSQL es la persistencia principal elegida, todavía no implementada. Redis y
  RabbitMQ se incorporarán cuando una tarea concreta los requiera; véase [ADR 001](docs/decisions/001-architecture-and-contract.md).
- `planning` es dueño del modelo indoor y publicará versiones identificables. `navigation`
  leerá solo modelos **PUBLISHED** mediante un puerto de solo lectura. El adaptador `demo`
  actual es temporal.

## GitFlow obligatorio
`main` (core initial commit) → `develop` → `feat/<bounded-context>-<funcionalidad>`.
Solo fusionar feature → develop después de pasar CI/Maven/tests/revisión; releases desde
`develop` y luego merge de `release/*` a `main`. Usar Conventional Commits.

## Estado de implementación
- Rama `main` y `develop`: base de Spring Boot, CI y convenciones.
- Rama `feat/navigation-destination-routing`: primer incremento de `navigation`.
- `PR-214`: directorio y búsqueda acento-insensible de destinos por nombre/especialidad/tipo.
- Motor Dijkstra derivado de reglas existentes del prototipo FastAPI:
  rutas más cortas, filtro de accesibilidad y exclusión de aristas que cruzan paredes.
- `POST /api/v1/navigations/route` es el endpoint provisional implementado; TS-12 exige
  A* y el contrato objetivo de TS-13 es `GET /api/v1/sites/{siteId}/routes`.
- Contratos REST y Spring MockMvc; dominio sin dependencias Spring.
- Datos de ejemplo disponibles **solo con perfil `demo`**. Sin persistencia productiva,
  subida de planos ni BLE reales; no declarar PR-214 terminado hasta integrar app cliente.

## Ejecutar tras instalar JDK 21 y Maven
```sh
mvn clean verify
mvn spring-boot:run -Dspring-boot.run.profiles=demo
```
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
