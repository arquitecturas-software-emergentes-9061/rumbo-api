# PR-120 — Physical calibration stored against a real draft floor plan

`PUT /api/v1/sites/{siteId}/plan-calibrations/{planVersionId}` with:
`{"floorId":"UUID","unit":"CENTIMETERS","measuredDistance":300,"startX":0,"startY":0,"endX":30,"endY":0}`.

The factor is `metersPerUnit = measuredDistanceInMeters / planDistanceInUnits`.
In the example it equals `0.1`. The API stores the raw measured distance,
unit and two reference points for audit, then updates
`plan_coordinate_spaces.meters_per_unit` for the exact site, floor,
planVersionId and DRAFT model version in one PostgreSQL transaction.
A later GET returns the persisted reference data.

Unknown or published/archived plans are not calibrated; points outside
the plan's width/height are rejected; moving a planVersionId to another
floor is a conflict. Authentication and site ownership are required.
The scale is consumed by Planning's `IndoorMapRepositoryImpl` and published
snapshot after a separate validated publication operation.

Validated by `ScaleReferenceIntegrationTest` against Testcontainers:
writes and recalibrates real draft data, rejects invalid plans and out-of-bounds
coordinates, and verifies the authoritative `meters_per_unit`.
