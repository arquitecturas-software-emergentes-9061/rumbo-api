# Structural geometry verification — 7 November 2026

## Scope

An administrator uploads a plan, calibrates it and requests a structural proposal. The mapper
builds geometry and routes, the API projects them to original-plan units, and Angular renders
the proposal and imports typed nodes/connections into a draft. This iteration implements the
geometry and navigation foundation; it does not implement universal floor-plan recognition.

Both worktrees start from the fetched `origin/develop`: API `421b8bc`, web `169381c`.
The source checkouts and the user's existing `angular.json` modification were preserved.

## Automated checks

| Check | Result |
|---|---|
| API, JDK 21, `mvn -o -q clean verify` | 300 tests, 0 failures, 0 errors, 0 skipped |
| Mapper, `STRUCTURAL_MAPPER_DISABLE_ML=1 pytest -q` | 24 passed |
| Angular, `NODE_OPTIONS=--no-experimental-webstorage npx ng test --watch=false` | 164 passed, 21 files |
| Angular production build, original API URL | Passed; size-budget warnings remain |
| Whitespace checks in both worktrees | Passed |

The Java run used Testcontainers plus an explicit fresh PostgreSQL database for the 40 integration
tests gated by `RUMBO_TEST_DB_URL`. These tests were not silently left skipped. Redis was also
isolated. The Node option fixes the local test runtime's Web Storage conflict without changing
the repository's test configuration.

Mapper regressions cover nonconvex halls, courtyard shortcuts, body clearance, opening strips,
late window barriers, order-independent rasterization of holes/obstacles, separate closed loops,
semantic cycles, SVG calibration, diagonal doors and a raster clinic with a freestanding column.
The column case checks complete connectivity, reachability and zero wall/out-of-walkable crossings.
It exposed an existing undirected-edge orientation error during repeated door attachments, now fixed.

## Real HTTP flow

An ephemeral PostgreSQL 17 database, Redis 7, packaged Java API and Python mapper were started on
separate loopback ports. Synthetic data only was created. Account/site/floor creation, PNG and SVG
upload, saved calibration and interpretation were exercised through actual HTTP requests.

| Boundary | Evidence |
|---|---|
| Original upload and storage | PNG and unannotated SVG accepted and stored as separate versions |
| Calibration | Both interpretation responses returned `CONFIRMED` |
| API → mapper | Actual multipart `/analyze` requests returned 200 |
| Mapper → API response | Room polygons, barrier classes, walkable polygons and opening endpoints retained |
| Final PNG proposal | 15 zones, 3 barriers, 42 nodes, 42 edges; no disconnection/reachability warnings |
| Final SVG proposal | 16 zones, 3 barriers, 42 nodes, 41 edges; no disconnection/reachability warnings |
| Response → Angular | DOM tests verify even-odd holes, label anchors, barriers and diagonal doors in both canvases |
| Real browser interaction | Not verified: browser host unavailable; native Chrome inspection subsequently timed out |

The HTTP exercise found a valid SVG rejected for a `100%` rectangle dimension before fallback.
The application now invokes structural interpretation in that case; a Java integration regression
covers the behavior. The original restricted parser's error remains when no fallback is available.

The different PNG/SVG zone counts are not evidence of recognition accuracy. No independently
annotated reference was used for this HTTP fixture. The real-browser boundary remains an explicit
gap; component DOM tests and HTTP checks are not presented as complete browser end-to-end coverage.

## Cost measurement

`structural-mapper/benchmark.py` measures wall-clock parsing plus payload construction, exports
diagnostics and handles invalid inputs per file. It does not compute recognition accuracy.

An illustrative same-machine CPU run over the existing clinic sample (1536 × 1024 pixels), three
repetitions per version, measured median total time of 3.319 s on the source checkout versus 3.154 s
on this worktree. Each returned 11 spaces. The newer graph differs because it preserves alternatives
and enforces walkable containment. This small sample and uncontrolled machine load do not establish
a statistically significant speedup or general performance guarantee. Peak RAM was not measured.

Implemented cost reductions include component-label lookup instead of a full-frame comparison per
component, cropped contour reconstruction, local distance transforms, reuse of the walkable union
and single computation of each interior label anchor. Correctness validation adds work of its own.

## Remaining work

- Establish independent annotations and a representative clinic corpus, including the user's raster.
- Improve wall, window and opening perception; assess thin/double-line and furnished plans.
- Evaluate OCR and compact learned perception with topology and correction-time metrics.
- Persist structural geometry and validate arbitrary manual edits against it before spatial publication.
- Implement and evaluate IFC/DXF adapters and AR localization after the mobile-stack decision.
- Complete interactive browser and physical-site verification.

All temporary servers and containers are disposable test resources. No production data, deployment,
remote branch, pull request or Jira status was changed.
