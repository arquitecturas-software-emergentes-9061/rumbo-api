# PR-120 — Persisted plan scale reference measurements

`PUT /api/v1/sites/{siteId}/plan-calibrations/{planVersionId}` uses:
`{"floorId":"UUID", "unit":"CENTIMETERS", "measuredDistance":300, "startX":0, "startY":0, "endX":30, "endY":0}`

The server stores the **original reference** (physical unit, measured length, two plan-unit points) and the calculated `metersPerUnit` factor. The example yields 0.1 m/unit. A same-version update is allowed only for the same floor; changing floor causes a conflict. `GET` returns the stored calibration.

Both endpoints require an authenticated clinic administrator who owns the site in the existing `indoor_sites` registry; another account gets 404. PostgreSQL constrains uniqueness of one reference per site+planVersion and positivity/finiteness. Flyway adds a timestamped migration, preserving the existing MapScale data path.

**Important integration blocker**: the other team's unmerged `feature/planning-coordinate-system` branch defines `PlanCoordinateSpaceJpaEntity` with `metersPerUnit` and its own version ownership. This PR stores the physical measurement provenance but does not yet verify that the referenced floor and planVersion actually exist in Planning, and does not change that aggregate's scale during publication. Once Planning is integrated, the command must verify the existing (siteId,floorId,planVersionId) and transactionally apply the computed factor to the authoritative Planning draft. Do not treat this as a finished calibration workflow until that integration and regression tests pass.
