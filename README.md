# RUMBO API · UI-Topic · 2026-20

Backend académico oficial **preparado localmente**, pendiente de publicarse en la organización
`arquitecturas-software-emergentes-9061`. **No utilizar el repositorio `report` como API**.

## Stack y arquitectura
- Java 21 / Spring Boot 3.5.6 / REST + OpenAPI.
- Domain-Driven Design; cada bounded context con capas `domain`, `application`,
  `interfaces` (plural: `interface` es una palabra reservada Java) e `infrastructure`.
- Persistencia según el capítulo V del informe: **PostgreSQL** (fuente de verdad, esquema con
  Flyway) y **Redis** (datos de vida corta y caché). Las entidades JPA son propias de cada
  contexto y se mapean a los aggregates, que no llevan anotaciones de JPA.
- Errores en formato **Problem Details (RFC 7807)** con una propiedad `code` estable
  (`shared/interfaces/rest/ProblemDetails`).
- El contexto `navigation` lee modelos **PUBLISHED** del contexto `modeling` a través
  de un puerto de solo lectura. El adapter inicial `demo` es una implementación temporal.

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
- Contratos REST y Spring MockMvc; dominio sin dependencias Spring.
- Datos de ejemplo disponibles **solo con perfil `demo`**. Sin persistencia productiva,
  subida de planos ni BLE reales; no declarar PR-214 terminado hasta integrar app cliente.

## Ejecutar tras instalar JDK 21, Maven y Docker
```sh
cp .env.example .env          # opcional: cambia credenciales locales
docker compose up -d          # PostgreSQL 17 y Redis 7
mvn verify                    # unitarias + integración (Testcontainers) + aceptación (Cucumber)
mvn spring-boot:run -Dspring-boot.run.profiles=demo
```
`mvn verify` levanta sus propios contenedores con Testcontainers: solo necesita Docker en ejecución.
Reportes: `target/cucumber-reports/cucumber.html` y `target/site/jacoco/index.html`.
Hasta que `planning` publique un modelo real, la app necesita el perfil `demo` para arrancar.

## Convenciones compartidas
- **Migraciones Flyway** en `src/main/resources/db/migration`, con versión por timestamp y prefijo
  del contexto para que no choquen entre ramas: `V<yyyyMMddHHmm>__<contexto>_<descripcion>.sql`
  (ejemplo: `V202611021200__iam_create_identity_tables.sql`). Nunca editar una migración ya fusionada
  a `develop`: se agrega una nueva.
- **Pruebas de aceptación**: un `.feature` por historia en `src/test/resources/features/<contexto>/`,
  con los criterios de aceptación de Jira. Los pasos HTTP genéricos están en `acceptance/steps/ApiSteps`.
- **Tests de integración**: `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`.
Endpoints de ejemplo:
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

## Decisiones aún no cerradas
1. Mensajería entre contextos: hoy Spring Application Events en proceso; RabbitMQ (C4) cuando
   Diseño, Navegación y Analíticas lo necesiten.
2. API contracts reales con el BC Diseño/Planificación y autorizaciones por sede.
3. Modelo de publicación de planos y políticas de seguridad.
4. Stack móvil (informe menciona Flutter y Unity/AR según sección).
5. Web Angular pendiente de cotejo con mockups del capítulo VI.

Fuentes: Informe TF 2026-20, capítulo V (BC Navegación), Jira `PR-214` y MVP
personal `jahazielgg/rumbo-platform`. No se ha migrado íntegramente todo el MVP.
