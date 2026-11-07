# ADR 004 — Preserve structural geometry and contain generated routes

Status: implemented for interpretation proposals; persistent spatial publication remains future work.

## Context

The structural mapper already emitted polygonal rooms, obstacles and windows. Its Java adapter
replaced rooms with bounding rectangles, discarded structural classes and flattened diagonal
doors into horizontal or vertical lines. Graph visibility only rejected intersections with
detected barriers, allowing shortcuts over exterior regions or courtyards. Simple-graph
simplification could erase corridor cycles and alternative routes.

## Decision

- Keep room polygons with interior rings and an interior label position in interpretation proposals.
  Retain bounding-box fields and legacy constructors for annotated SVG and older clients.
- Add typed barrier polygons and walkable polygons, in original-plan units. Preserve opening axes.
- Derive a calibrated raster scale after decoding from the original width and meters per unit;
  this covers SVG documents whose rendered dimensions differ from stored original units.
- Keep the configured body clearance; do not silently relax it for narrow spaces. Join inset
  spaces through confirmed opening strips with jamb clearance, excluding detected barriers.
- Validate generated edges against both barriers and the complete walkable region. Account for
  sub-pixel raster rounding with a documented 0.75-pixel containment tolerance.
- Preserve closed components and parallel corridor paths using distinct intermediate nodes in
  a simple graph. Do not simplify a cycle into an already-existing edge.
- Measure stages independently and provide a repeatable, deterministic benchmark command.
- Fall back to structural interpretation when a valid stored SVG exceeds the annotation parser's
  supported subset; preserve its existing error if no structural proposal is available.

## Consequences

The Angular proposal and applied backdrop render polygons with even-odd holes and typed barriers,
with legacy rectangular fallback. This does not change the persisted graph aggregate, publication
rules or mobile stack. Geometry validation applies to generated proposals; arbitrary subsequent
manual graph edits are not yet checked against persisted structural geometry.

Applying a structural proposal retains entrance roles, corridor/doorway kinds and directionality.
Proximity alone must not snap an explicitly typed door or waypoint onto a saved destination.

These checks establish consistency with detected geometry, not completeness against the physical
building. Raster wall/door recognition, OCR, IFC/DXF adapters, trained compact perception and AR
relocalization require separate evaluation and implementation. A connected graph or zero geometry
violations must not be presented as evidence that all rooms or walls have been recognized.
