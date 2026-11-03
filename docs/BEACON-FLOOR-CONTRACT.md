# PR-192 / PR-194 — Beacon inventory contract and negative cases

The `GET /api/v1/sites/{siteId}/beacons?floorId=<uuid>` API returns only beacon references for the authenticated owner's site and floor. Missing floor filter lists all the owner's site beacons. A nonexistent floor filter returns an empty list; it does **not** assert that this floor is registered in Planning.

Regression tests verify per-floor isolation, expected empty responses, duplicate identifiers, invalid coordinates, unknown beacon IDs, malformed floor UUIDs, and cross-account access.

`positioning_beacons.floor_id` is a raw UUID in the current develop schema and `positioning_beacons` does not yet store `nodeId`. Therefore the Jira requirement **“rechazo de punto espacial inexistente” is still unmet**. It requires a validated site+floor+node reference from the Planning spatial graph, currently only in the unmerged `feature/planning-coordinate-system` branch. After its integration, registration must require a known node or calibrated floor anchor and the database must reject unknown references. Do not mark PR-194 complete before that negative integration test passes.
