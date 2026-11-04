# Preview de geometría SVG (SPIKE SP-02 / US-09 / US-10)

Endpoint: `POST /api/v1/plan-interpretations/svg` with JSON `{"svg":"<svg ...>..." }`, accessible only to a valid `CLINIC_ADMIN` bearer token. Returns the source `viewBox`, `wallCandidates[]`, `roomLabels[]` and `warnings[]`. Does **not** write data or publish a map.

The parser:
- Extracts straight segments from `line`, `rect`, `polyline`, `polygon` and paths using `M/m L/l H/h V/v Z/z`.
- Recognizes text labels and offers tentative categories for rooms, corridors, elevators, stairs and services.
- Uses secure XML parsing, rejects external entities, oversized SVGs and coordinate transforms that would make geometry wrong.
- Skips curved path commands with warnings instead of inventing walls.

Limitations:
- Segments are candidates, not confirmed walls. Labels are heuristic, not medical directory truth. No doorway detection, OCR of raster PNG/JPEG/PDF, layout inference, accessibility or graph topology.
- Coordinates are SVG plan units, **not** meters; Planning's scale reference must confirm them.
- Clients must display candidates in the editor and explicitly review/correct nodes, walls, room categories and connectivity before any publication.
- The REST adapter calls a Planning query service; the SVG parser stays in Planning infrastructure. Reviewed candidates can be converted into draft graph edits by the client.
