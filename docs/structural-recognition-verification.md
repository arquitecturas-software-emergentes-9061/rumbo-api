# Structural recognition verification — 7 November 2026

## Actual uploaded raster

The comparison uses the exact locally stored `76050ee1ea873c6b2d26483ef2f54711.jpg`,
600 × 500 pixels, SHA-256 `a867e249981adaa2ad7b0e19e77c99a94cf99e3825b7c6c2ab361d2f9e1334ad`.
Calibration: 0.018259790923226722 meters per pixel. The source upload and saved graph
were read only; proposals were not applied to the user's draft.

The previous geometry work did not solve perception: classical morphology merged the
furnished raster into essentially one circulation area. The evaluated BuildingCV model
also missed critical partitions. The optional CubiCasa5k multitask model now supplies
wall, door and window evidence through ONNX Runtime, without PyTorch at inference.
The official checkpoint is exported separately; weights are not committed.

The reconstructed scene identifies the five principal offices around the central hall.
A manual spot check at interior points (398,95), (200,250), (400,250), (200,390), (400,390)
found five distinct room polygons, each with a destination node in the same connected
network. This is a focused check on one image, not a whole-plan accuracy benchmark.
The final graph has 43 nodes, 42 edges and one connected component, with zero wall
crossings and zero edges outside the inferred walkable polygons. It has six transitions:
five office doors and the passage between hall segments. No exterior entrance is
confidently identified. Ten segmented regions have no detected access, including small
bathrooms and uncertain peripheral regions. Review is still necessary.

On this machine, total analysis was approximately 2–3 seconds, including about one
second of CPU neural inference, after model initialization. This does not establish a
latency SLA. Exported model size is 69,474,315 bytes (~66 MiB), versus the ~198 MB
upstream training checkpoint. These are different artifacts, not a measured application
RAM reduction. Native model training/inference is not shipped in the ONNX service.

## Fixes required to use semantic evidence correctly

- Preserve overlapping door/window labels within the wall class.
- Keep narrow icon strokes; ink area differs from physical door area.
- Cut openings through measured host-wall depth without widening jambs.
- Preserve windows as blocked geometry.
- Follow a doorway normal to the actual inset usable region before turning.
- Preserve the tested skeleton attachment point instead of shifting it to a nearby node.
- Require entrance evidence rather than converting arbitrary envelope contacts to doors.

Five focused regression tests cover these boundaries; the complete mapper suite has
29 passing tests. Existing Java/Angular validation is recorded separately in
`structural-geometry-verification.md`. Browser interaction has not been verified.

## Limits and reproducibility

The image is not bundled into the repository. Source, output payload and comparative
visuals are local artifacts. The model is the default backend: the mapper downloads it
once from the public `rumbo-models` release and verifies its SHA-256
(`mapper/perception/model_store.py`); `CUBICASA_ONNX_MODEL` points to another copy, and
the API asks for it unless `STRUCTURAL_MAPPER_USE_ML=false`. Health and proposal
diagnostics expose the active backend. The CubiCasa upstream license is CC BY-NC 4.0;
Rumbo uses it only as an academic, non-commercial project.

Broader validation still requires independent annotations for walls, openings and usable
regions across diverse clinics, and measurements of false connections, missed access,
route success and correction time. The reference checks above are not sufficient to
claim generalization, AR alignment or deployable accessibility compliance.

## Activation and HTTP evidence

The exact JPEG was uploaded and calibrated through the packaged Java API into a fresh
PostgreSQL verification database. The real interpretation endpoint returned 17 zones,
43 nodes, 42 connections, structural barriers and walkable polygons with scale status
`CONFIRMED`. The temporary account/data and database were then removed.

Local services were restarted from the two worktrees on the usual ports: frontend
4200 (HTTP 200), API 8080 (`UP`), mapper 8010 (`parser_version=4`,
`ml_backend=cubicasa5k-onnx`). A subsequent direct HTTP analysis on 8010 reproduced the
connected result. The API is explicitly configured with ML enabled. A newly generated
local JWT signing key means existing browser sessions need a new login. The user's
saved proposal/draft was not applied or overwritten. Browser interaction remains unverified.

The primary `develop` checkouts are preserved; these changes remain in separate
`feature/planning-navigable-geometry` worktrees and have not been pushed or merged.
Local comparison artifacts are in the workspace's `artifacts/plan-recognition/` directory.
