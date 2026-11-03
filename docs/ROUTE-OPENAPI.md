# PR-229 — Route contract (provisional until real Planning integration)

Swagger: `GET /v3/api-docs` and `/swagger-ui/index.html`.

## POST /api/v1/navigations/route

Request fields: `siteId`, `startFloorId`, `startNodeId`, `destinationFloorId`, `destinationNodeId` (all required UUID) and `accessibleOnly` (boolean, default false). Example request and a corresponding response are embedded **directly in generated OpenAPI** via annotations and checked by `NavigationOpenApiContractTest`.

Response 200: `totalDistanceMeters`, ordered `nodes[]` ({floorId,nodeId,label,x,y}) and `links[]` ({from:{floorId,nodeId},to:{floorId,nodeId},distanceMeters,kind,accessible}).

Error 400: malformed or required fields. Error 422: invalid model/site/nodes or unreachable/unsafe route.

**Current limits**: the deployed `develop` implementation uses Dijkstra over demo fixtures with **distanceMeters as cost**. It is not the target persisted weighted A* implementation. The latter lives in the team's unmerged `feature/planning-coordinate-system` branch. The target GET route API, persisted weight+verticalPenalty and accessibility checks cannot be described as shipped until that branch and the remaining permissions/obstacle contracts are integrated. No guarantee of response <200ms is made from a unit-test fixture.

PR-224 verification: `CrossFloorRouteContractTest` validates finite expected path distances, access filtering, directed connectors, and impossible/disconnected routes in the current engine. Separate A* tests in the other branch already compare weighted costs and penalties; these must be run after its merge.
