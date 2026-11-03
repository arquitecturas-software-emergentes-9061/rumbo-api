# Backend deliverables owned by Jahaziel — Jira PR project

This is the integration checkpoint for the eight assigned subtasks.
The implementation is split across PR #6–#11 and the integration PR #12.
Source branch: `feature/integration-planning-spatial-core`, started at
`develop`, preserving the other contributor's
`feature/planning-coordinate-system` as an unchanged source of code.

| Jira | Evidence in code | Test |
|---|---|---|
| PR-120 | `ScaleReferenceService` updates draft plan scale transactionally and stores original measurement | `ScaleReferenceIntegrationTest` |
| PR-142 | `SvgGeometryInterpreter` extracts walls; `SvgRoomTopologyProposalService` recognizes explicit doors | `SvgGeometryInterpreterTest`, `SvgRoomTopologyProposalServiceTest` |
| PR-143 | Semantic zone nodes/links only through verified annotated door geometry | `SvgRoomTopologyProposalServiceTest` |
| PR-159 | Room/corridor/service labels and editable categories | `SvgRoomTopologyProposalServiceTest` |
| PR-192 | `BeaconInventoryService` query by floor and spatial coordinates | `BeaconControllerIntegrationTest` |
| PR-194 | Duplicate checks, invalid-floor/outside-plan rejection and authorization | `BeaconControllerIntegrationTest` |
| PR-224 | Weighted multi-floor A* on published Planning graph; unreachable and directed paths | `AStarRouteCalculationDomainServiceTest`, `PublishedRouteControllerIntegrationTest` |
| PR-229 | Actual GET published route with generated OpenAPI parameters and sample response | `PublishedRouteControllerIntegrationTest` |

## Known limitations outside these backend tickets

- The SVG topology parser requires vector sources with meaningful annotations
  for conservative door links. Unannotated raster extraction is not implemented.
- The Planning published graph has no verified `accessible` or obstacle metadata.
  Accessible-only queries are explicitly rejected (422); no false assurance.
- Real site/clinic authorization currently follows IAM account ownership and is not
  a multi-admin organization membership model.
- The map-generation workflow requires a frontend editor review before data is
  transformed from SVG proposal to Planning draft.

Never mark unexercised acceptance criteria as validated simply because a PR was merged.
Tests from the original Planning branch guarded by external RUMBO_TEST_DB_URL remain
skipped in CI; separate Testcontainers tests exercise the published route,
scale and beacon integration.
