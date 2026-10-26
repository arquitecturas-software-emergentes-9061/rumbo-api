# RUMBO API · UI-Topic · 2026-20

Backend académico oficial de RUMBO en la organización `arquitecturas-software-emergentes-9061`.
El informe académico se conserva separado en el repositorio `report`.

## Stack y arquitectura
- Java 21 / Spring Boot 3.5.6 / REST + OpenAPI.
- Domain-Driven Design; cada bounded context con capas `domain`, `application`,
  `interfaces` (plural: `interface` es una palabra reservada Java) e `infrastructure`.
- Este repositorio evita fijar PostgreSQL o MongoDB hasta resolver la diferencia en el informe.
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

## Ejecutar tras instalar JDK 21 y Maven
```sh
mvn clean verify
mvn spring-boot:run -Dspring-boot.run.profiles=demo
```
Endpoints de ejemplo:
```sh
curl -s 'http://localhost:8080/api/v1/navigations/destinations?siteId=00000000-0000-4000-8000-000000000001&query=cardiologia'
curl -s -X POST http://localhost:8080/api/v1/navigations/route \
  -H 'Content-Type: application/json' -d '{"siteId":"00000000-0000-4000-8000-000000000001","startFloorId":"00000000-0000-4000-8000-000000000002","startNodeId":"00000000-0000-4000-8000-000000000010","destinationFloorId":"00000000-0000-4000-8000-000000000002","destinationNodeId":"00000000-0000-4000-8000-000000000011","accessibleOnly":true}'
```
Swagger UI (demo): `http://localhost:8080/swagger-ui/index.html`.
Health check: `http://localhost:8080/actuator/health`.

## Ramas y publicación
Las ramas y los commits se incluyen en `rumbo-api-gitflow.bundle` para evitar reconstruir
su historial local. Crear repositorio vacío `rumbo-api` dentro de la organización; luego:
```sh
git clone --branch main rumbo-api-gitflow.bundle rumbo-api
cd rumbo-api
git remote rename origin bundle
git branch develop bundle/develop
git branch feat/navigation-destination-routing bundle/feat/navigation-destination-routing
git remote add origin https://github.com/arquitecturas-software-emergentes-9061/rumbo-api.git
git push -u origin main develop feat/navigation-destination-routing
```
**No** fusionar la feature a `develop` hasta ejecutar `mvn verify` en un entorno con Maven,
revisar CI y conseguir aprobación del equipo.

## Decisiones aún no cerradas
1. Persistencia (C4: PostgreSQL + Redis + RabbitMQ; 7.1.1: MongoDB/Atlas).
2. API contracts reales con el BC Diseño/Planificación y autorizaciones por sede.
3. Modelo de publicación de planos y políticas de seguridad.
4. Stack móvil (informe menciona Flutter y Unity/AR según sección).
5. Web Angular pendiente de cotejo con mockups del capítulo VI.

Fuentes: Informe TF 2026-20, capítulo V (BC Navegación), Jira `PR-214` y MVP
personal `jahazielgg/rumbo-platform`. No se ha migrado íntegramente todo el MVP.
