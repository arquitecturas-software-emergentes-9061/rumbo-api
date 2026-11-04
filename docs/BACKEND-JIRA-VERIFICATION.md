# Backend deliverables owned by Jahaziel — Jira PR project

This file records evidence for the eight subtasks assigned to Jahaziel on
`develop`. The local integration with Gabriela's `feature/planning-coordinate-system`
keeps the Planning and Navigation implementations from Gabriela where her work items
overlap, while retaining the separate IAM, Positioning, Analytics, calibration, and
SVG functionality from `develop`. Historical PR numbers below identify the source
work; the merged code must be verified again as a whole.

| Jira | Evidence in code | Test |
|---|---|---|
| PR-120 | `ScaleReferenceService` updates draft plan scale transactionally and stores original measurement | `ScaleReferenceIntegrationTest` |
| PR-142 | `SvgGeometryInterpreter` extracts walls; `SvgRoomTopologyProposalService` recognizes explicit doors | `SvgGeometryInterpreterTest`, `SvgRoomTopologyProposalServiceTest` |
| PR-143 | Semantic zone nodes/links only through verified annotated door geometry | `SvgRoomTopologyProposalServiceTest` |
| PR-159 | Room/corridor/service labels and editable categories | `SvgRoomTopologyProposalServiceTest` |
| PR-192 | `BeaconInventoryService` query by floor and spatial coordinates | `BeaconControllerIntegrationTest` |
| PR-194 | Duplicate checks, invalid-floor/outside-plan rejection and authorization | `BeaconControllerIntegrationTest` |
| PR-224 | Weighted multi-floor A* on published Planning graph; unreachable and directed paths | Gabriela's `AStarRouteCalculationDomainServiceTest`, `PublishedRoutesControllerIntegrationTest` |
| PR-229 | Actual GET published route with generated OpenAPI parameters and sample response | Gabriela's `PublishedRoutesControllerIntegrationTest`; `NavigationOpenApiContractTest` covers the demo POST |

## Known limitations outside these backend tickets

- The SVG topology parser requires vector sources with meaningful annotations
  for conservative door links. Unannotated raster extraction is not implemented.
- The Planning graph has declared accessibility states but no verified obstacle
  clearance. `accessibleOnly` uses only explicitly accessible links.
- Real site/clinic authorization currently follows IAM account ownership and is not
  a multi-admin organization membership model.
- The map-generation workflow requires a frontend editor review before data is
  transformed from SVG proposal to Planning draft.

Never mark unexercised acceptance criteria as validated simply because a PR was merged.
The original Planning branch's PostgreSQL tests require `RUMBO_TEST_DB_URL`; they
must run against an isolated database before integration is considered verified.
Testcontainers tests separately exercise IAM, scale, beacon, and analytics flows.
