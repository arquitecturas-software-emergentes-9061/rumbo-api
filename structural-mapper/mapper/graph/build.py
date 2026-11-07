"""Semantic navigation graph.

Nodes exist only where they are useful for wayfinding:

* `decision`  – corridor junctions (skeleton degree >= 3)
* `waypoint`  – bends where a straight segment would cross a wall or turn sharply
* `door`      – every kept opening between spaces
* `entrance`  – openings to the outside
* `room`      – one anchor per reachable room (destination for POIs)
* `connector` – elevator / stairs / ramp spaces (cross-floor links are configured later)

Every edge is checked against the wall polygons; an edge that would cross a wall is
refined along the skeleton path or dropped. Topological correctness beats looks.
"""
from __future__ import annotations

from itertools import count

import networkx as nx
import numpy as np
from shapely.geometry import LineString, Point, Polygon
from shapely.ops import nearest_points, unary_union
from shapely.prepared import prep

from ..contracts import GraphEdge, GraphNode, NavigationGraph, Opening, Scale, Space, WallElement
from ..geometry import rasterize
from ..walkable.free_space import walkable_union
from .skeleton import collapse_degree_two, merge_close_nodes, prune_spurs, rdp, skeleton_graph

TURN_TOLERANCE_M = 0.35
MERGE_M = 0.7
SPUR_M = 1.2
COLLINEAR_COS = -0.94  # ~160 degrees
SNAP_M = 0.5  # how far a node may sit from the rasterized free space and still be bridged
BRIDGE_MAX_M = 40.0  # longest geodesic repair; beyond it a fragment stays flagged for review
SEMANTIC_KINDS = ("door", "entrance", "room", "connector")


class GraphBuilder:
    def __init__(self, scale: Scale, shape: tuple[int, int], walls: list[WallElement]):
        self.scale = scale
        self.shape = shape
        barrier = unary_union([w.polygon for w in walls]) if walls else Polygon()
        # shrink slightly so touching a wall face is not a crossing
        self.barrier = barrier.buffer(-0.25) if not barrier.is_empty else barrier
        self.prepared = prep(self.barrier)
        self.nodes: dict[str, GraphNode] = {}
        self.edges: dict[frozenset, GraphEdge] = {}
        self.ids = count(1)
        self.skeleton: nx.Graph | None = None
        self.skeleton_node_ids: dict[int, str] = {}
        self.dropped_crossing = 0
        self.room_ids: set[str] = set()
        self.walls = walls
        self.allowed = None
        self.bridged_components = 0

    # ---- public --------------------------------------------------------------------
    def build(self, spaces: list[Space], openings: list[Opening], walkable: dict[str, Polygon],
              walkable_area: list[Polygon] | None = None) -> NavigationGraph:
        # Sub-pixel tolerance accounts for raster centre rounding, not body clearance.
        allowed = walkable_area if walkable_area is not None else walkable_union(walkable, openings, self.scale, self.walls)
        self.allowed = prep(unary_union(allowed).buffer(0.75))
        by_space = {s.id: s for s in spaces}
        self.room_ids = {s.id for s in spaces if s.kind in ("room", "vertical")}
        circulation = [walkable[s.id] for s in spaces if s.kind == "circulation" and s.id in walkable]
        if circulation:
            mask = rasterize(circulation, self.shape)
            self._build_skeleton(mask, openings)
        for opening in openings:
            self._attach_opening(opening, by_space, walkable)
        self._connect_room_nodes(walkable)
        self._simplify()
        self.dropped_crossing = self._drop_crossing_edges()
        self.bridged_components = self._bridge_components(allowed)
        return NavigationGraph(nodes=list(self.nodes.values()), edges=list(self.edges.values()))

    # ---- connectivity repair -------------------------------------------------------------
    def _components(self) -> list[set[str]]:
        graph = nx.Graph()
        graph.add_nodes_from(self.nodes)
        graph.add_edges_from(tuple(key) for key in self.edges)
        return [set(c) for c in nx.connected_components(graph)]

    def _semantic(self, component: set[str]) -> int:
        return sum(1 for n in component if self.nodes[n].kind in SEMANTIC_KINDS)

    def _bridge_components(self, allowed: list[Polygon]) -> int:
        """Join graph fragments that the free space itself connects.

        Local attachment can fail at a doorway (a probe lands on a jamb, an edge grazes the
        inset boundary) and leave a room or door isolated although a walkable path exists.
        A geodesic search over the rasterized free space, which already excludes walls,
        finds that path; it is added through the same wall-safe polyline as other edges.
        Fragments that the free space does not connect stay isolated and keep their
        validation warning: no route is invented through an undetected opening.
        """
        components = self._components()
        if len(components) <= 1:
            return 0
        from skimage.graph import MCP_Geometric

        free = rasterize(allowed, self.shape)
        if not self.barrier.is_empty:
            free &= ~rasterize([self.barrier], self.shape)
        costs = np.where(free, 1.0, np.inf)
        snap = max(1, int(round(self.scale.px(SNAP_M))))
        limit = self.scale.px(BRIDGE_MAX_M)

        main = max(components, key=lambda c: (self._semantic(c), len(c)))
        others = [c for c in components if c is not main]
        starts: dict[tuple[int, int], str] = {}
        for node_id in main:
            pixel = self._free_pixel(free, self.nodes[node_id], snap)
            if pixel is not None:
                starts.setdefault(pixel, node_id)
        if not starts:
            return 0
        mcp = MCP_Geometric(costs, fully_connected=True)
        cumulative, _ = mcp.find_costs(list(starts))

        bridged = 0
        for component in others:
            if self._semantic(component) == 0:
                continue
            best = None
            for node_id in component:
                pixel = self._free_pixel(free, self.nodes[node_id], snap)
                if pixel is None or not np.isfinite(cumulative[pixel]) or cumulative[pixel] > limit:
                    continue
                if best is None or cumulative[pixel] < best[0]:
                    best = (cumulative[pixel], node_id, pixel)
            if best is None:
                continue
            _, node_id, pixel = best
            route = mcp.traceback(pixel)  # from the reached start pixel to this fragment
            target = starts.get(tuple(route[0]))
            if target is None:
                continue
            node, end = self.nodes[node_id], self.nodes[target]
            points = [(node.x, node.y)] + [(float(c), float(r)) for r, c in reversed(route)] + [(end.x, end.y)]
            chain = self._wall_safe(points)
            # Only commit a bridge whose every segment satisfies the validator's invariants.
            if not all(self._visible(u, v) for u, v in zip(chain, chain[1:])):
                continue
            previous = node_id
            for bend in chain[1:-1]:
                bend_id = self._add_node(bend[0], bend[1], "waypoint")
                self._add_edge(previous, bend_id, "corridor")
                previous = bend_id
            self._add_edge(previous, target, "corridor")
            bridged += 1
        # Fragments without any wayfinding meaning (stray skeleton pieces) are noise.
        for component in self._components():
            if self._semantic(component) == 0:
                for node_id in component:
                    for key in [k for k in self.edges if node_id in k]:
                        del self.edges[key]
                    del self.nodes[node_id]
        return bridged

    @staticmethod
    def _free_pixel(free: np.ndarray, node: GraphNode, radius: int) -> tuple[int, int] | None:
        x, y = int(round(node.x)), int(round(node.y))
        height, width = free.shape
        if 0 <= y < height and 0 <= x < width and free[y, x]:
            return (y, x)
        y0, y1 = max(0, y - radius), min(height, y + radius + 1)
        x0, x1 = max(0, x - radius), min(width, x + radius + 1)
        window = np.argwhere(free[y0:y1, x0:x1])
        if len(window) == 0:
            return None
        distances = np.hypot(window[:, 0] + y0 - y, window[:, 1] + x0 - x)
        r, c = window[int(np.argmin(distances))]
        return (int(r + y0), int(c + x0))

    def _drop_crossing_edges(self) -> int:
        """Safety net: the invariant is enforced at construction, but never trust it blindly."""
        dropped = 0
        for key in list(self.edges):
            a, b = tuple(key)
            na, nb = self.nodes[a], self.nodes[b]
            if not self._visible((na.x, na.y), (nb.x, nb.y)):
                del self.edges[key]
                dropped += 1
        if dropped:
            connected = {n for key in self.edges for n in key}
            for node_id in [n.id for n in self.nodes.values() if n.id not in connected and n.kind in ("waypoint", "decision")]:
                del self.nodes[node_id]
        return dropped

    # ---- skeleton of circulation --------------------------------------------------------
    def _build_skeleton(self, mask: np.ndarray, openings: list[Opening]) -> None:
        graph = skeleton_graph(mask)
        if graph.number_of_nodes() == 0:
            return
        protected: set[int] = set()
        for opening in openings:
            nearest = self._nearest_skeleton_node(graph, opening.center, self.scale.px(1.0))
            if nearest is not None:
                protected.add(nearest)
        import cv2

        distance = cv2.distanceTransform(mask.astype(np.uint8), cv2.DIST_L2, 5)

        def local_width(x: float, y: float) -> float:
            xi, yi = int(round(x)), int(round(y))
            if 0 <= yi < distance.shape[0] and 0 <= xi < distance.shape[1]:
                return float(distance[yi, xi])
            return 0.0

        prune_spurs(graph, self.scale.px(SPUR_M), protected, local_width)
        merge_close_nodes(graph, self.scale.px(MERGE_M), protected)
        collapse_degree_two(graph, protected)
        self.skeleton = graph
        for node, data in graph.nodes(data=True):
            kind = "decision" if graph.degree(node) >= 3 else "waypoint"
            self.skeleton_node_ids[node] = self._add_node(data["x"], data["y"], kind)
        for a, b, data in graph.edges(data=True):
            path = self._orient(data["path"], graph.nodes[a])
            self._add_polyline(self.skeleton_node_ids[a], self.skeleton_node_ids[b], [(graph.nodes[a]["x"], graph.nodes[a]["y"])] + list(path) + [(graph.nodes[b]["x"], graph.nodes[b]["y"])], "corridor")

    @staticmethod
    def _orient(path, start):
        def distance(point):
            return (point[0] - start["x"]) ** 2 + (point[1] - start["y"]) ** 2
        return path if distance(path[0]) <= distance(path[-1]) else path[::-1]

    def _nearest_skeleton_node(self, graph: nx.Graph, point, radius: float) -> int | None:
        best, best_d = None, radius
        for node, data in graph.nodes(data=True):
            d = np.hypot(data["x"] - point[0], data["y"] - point[1])
            if d < best_d:
                best, best_d = node, d
        return best

    # ---- openings --------------------------------------------------------------------------
    def _attach_opening(self, opening: Opening, spaces: dict[str, Space], walkable: dict[str, Polygon]) -> None:
        kind = "entrance" if opening.kind == "entrance" else "door"
        door_id = self._add_node(opening.center[0], opening.center[1], kind, opening_id=opening.id)
        depth = max(2.0, opening.polygon.area / max(opening.width_px, 1.0))
        for sign, space_id in zip((1, -1), opening.space_ids):
            if space_id is None or space_id not in spaces or space_id not in walkable:
                continue
            space = spaces[space_id]
            inside = (
                opening.center[0] + sign * opening.normal[0] * (depth / 2 + self.scale.px(0.4)),
                opening.center[1] + sign * opening.normal[1] * (depth / 2 + self.scale.px(0.4)),
            )
            if not space.polygon.buffer(1.0).contains(Point(inside)):
                other = (
                    opening.center[0] - sign * opening.normal[0] * (depth / 2 + self.scale.px(0.4)),
                    opening.center[1] - sign * opening.normal[1] * (depth / 2 + self.scale.px(0.4)),
                )
                if space.polygon.buffer(1.0).contains(Point(other)):
                    inside = other
            # The temporary opening seal and metric body inset can put this probe
            # outside the usable polygon. Follow the doorway normal to the actual
            # inset boundary before turning towards the room anchor or skeleton.
            direction = np.asarray(inside) - np.asarray(opening.center)
            direction /= max(np.linalg.norm(direction), 1.0)
            ray = LineString([opening.center, np.asarray(opening.center) + direction * self.scale.px(3.0)])
            entry = walkable[space_id].intersection(ray)
            if not entry.is_empty:
                _, closest = nearest_points(Point(opening.center), entry)
                candidate = np.asarray(closest.coords[0]) + direction
                if walkable[space_id].covers(Point(candidate)):
                    inside = tuple(candidate)
                else:
                    inside = tuple(closest.coords[0])
            if space.kind == "circulation":
                self._connect_to_skeleton(door_id, inside, space)
            elif space.kind in ("room", "vertical"):
                self._connect_to_room(door_id, inside, space, walkable[space.id])

    def _connect_to_skeleton(self, door_id: str, inside, space: Space) -> None:
        graph = self.skeleton
        door = self.nodes[door_id]
        if graph is None or graph.number_of_nodes() == 0:
            # circulation without skeleton (tiny space): treat like a room
            self._connect_to_room(door_id, inside, space, space.polygon)
            return
        # candidate attachment points: existing nodes first, then points along edges
        candidates: list[tuple[float, str | None, tuple[float, float], tuple | None]] = []
        for node, data in graph.nodes(data=True):
            p = (data["x"], data["y"])
            candidates.append((np.hypot(p[0] - inside[0], p[1] - inside[1]), self.skeleton_node_ids[node], p, None))
        for a, b, data in graph.edges(data=True):
            # Undirected edge iteration may reverse endpoint order after a split.
            # Sample and split indices must use the same path orientation.
            path = self._orient(data["path"], graph.nodes[a])
            step = max(1, len(path) // 40)
            pixels = np.asarray(path)
            nearest = int(np.argmin(np.hypot(pixels[:, 0] - inside[0], pixels[:, 1] - inside[1])))
            for index in sorted(set(range(0, len(path), step)) | {nearest}):
                p = path[index]
                candidates.append((np.hypot(p[0] - inside[0], p[1] - inside[1]), None, (float(p[0]), float(p[1])), (a, b, index)))
        candidates.sort(key=lambda item: item[0])
        merge = self.scale.px(MERGE_M)
        for distance, node_id, point, edge_ref in candidates[:400]:
            if not self._visible((door.x, door.y), point):
                continue
            if node_id is None and edge_ref is not None:
                node_id = self._split_skeleton_edge(edge_ref, point, merge)
            if node_id is None:
                continue
            self._add_edge(door_id, node_id, "doorway")
            return
        # nothing visible: fall back to a waypoint just inside the door, connected to nearest visible skeleton point via straight line attempts
        way_id = self._add_node(inside[0], inside[1], "waypoint", space_id=space.id)
        self._add_edge(door_id, way_id, "doorway")
        for distance, node_id, point, edge_ref in candidates[:400]:
            if not self._visible(inside, point):
                continue
            if node_id is None and edge_ref is not None:
                node_id = self._split_skeleton_edge(edge_ref, point, merge)
            if node_id is None:
                continue
            self._add_edge(way_id, node_id, "corridor")
            return

    def _split_skeleton_edge(self, edge_ref, point, merge: float) -> str | None:
        a, b, index = edge_ref
        graph = self.skeleton
        if graph is None or not graph.has_edge(a, b):
            return None
        data = graph[a][b]
        path = self._orient(data["path"], graph.nodes[a])
        for end in (a, b):
            attrs = graph.nodes[end]
            # Visibility was checked to `point`, not to a nearby endpoint. Moving
            # that attachment by MERGE_M can turn a normal doorway crossing into
            # a diagonal through its jamb or outside its narrow usable strip.
            if np.hypot(attrs["x"] - point[0], attrs["y"] - point[1]) <= 0.75:
                return self.skeleton_node_ids[end]
        new = max(graph.nodes) + 1
        graph.add_node(new, x=float(point[0]), y=float(point[1]))
        first, second = path[: index + 1], path[index:]
        graph.remove_edge(a, b)
        graph.add_edge(a, new, path=first, length=_length(first))
        graph.add_edge(new, b, path=second, length=_length(second))
        node_id = self._add_node(point[0], point[1], "waypoint")
        self.skeleton_node_ids[new] = node_id
        # replace the straight polyline edges in the semantic graph
        self._remove_edge(self.skeleton_node_ids[a], self.skeleton_node_ids[b])
        self._add_polyline(self.skeleton_node_ids[a], node_id, [(graph.nodes[a]["x"], graph.nodes[a]["y"])] + list(first) + [point], "corridor")
        self._add_polyline(node_id, self.skeleton_node_ids[b], [point] + list(second) + [(graph.nodes[b]["x"], graph.nodes[b]["y"])], "corridor")
        return node_id

    def _connect_to_room(self, door_id: str, inside, space: Space, walk: Polygon) -> None:
        door = self.nodes[door_id]
        anchor = self._room_anchor(space, walk)
        existing = [n for n in self.nodes.values() if n.kind in ("room", "connector") and n.space_id == space.id]
        target = existing[0] if existing else None
        if target is None:
            kind = "connector" if space.kind == "vertical" else "room"
            target = self.nodes[self._add_node(anchor.x, anchor.y, kind, space_id=space.id, label=space.label, connector_kind=space.connector_kind)]
        if self._visible((door.x, door.y), (target.x, target.y)):
            self._add_edge(door_id, target.id, "doorway")
            return
        way_id = self._add_node(inside[0], inside[1], "waypoint", space_id=space.id)
        self._add_edge(door_id, way_id, "doorway")
        if self._visible(inside, (target.x, target.y)):
            self._add_edge(way_id, target.id, "corridor")
        else:
            self._route_inside(way_id, target.id, walk)

    def _room_anchor(self, space: Space, walk: Polygon) -> Point:
        try:
            from shapely.ops import polylabel

            part = walk if walk.geom_type == "Polygon" else max(walk.geoms, key=lambda p: p.area)
            return polylabel(part, tolerance=1.0)
        except Exception:
            return walk.representative_point()

    def _route_inside(self, from_id: str, to_id: str, walk: Polygon) -> None:
        """Connect two nodes inside one space through its own skeleton (non-convex rooms)."""
        mask = rasterize([walk], self.shape)
        graph = skeleton_graph(mask)
        if graph.number_of_nodes() == 0:
            return
        a, b = self.nodes[from_id], self.nodes[to_id]
        start = self._nearest_skeleton_node(graph, (a.x, a.y), self.scale.px(3.0))
        end = self._nearest_skeleton_node(graph, (b.x, b.y), self.scale.px(3.0))
        if start is None or end is None:
            return
        try:
            route = nx.shortest_path(graph, start, end, weight="length")
        except nx.NetworkXNoPath:
            return
        points = [(a.x, a.y)]
        for u, v in zip(route, route[1:]):
            path = self._orient(graph[u][v]["path"], graph.nodes[u])
            points.extend(path)
        points.append((b.x, b.y))
        self._add_polyline(from_id, to_id, points, "corridor")

    def _connect_room_nodes(self, walkable: dict[str, Polygon]) -> None:
        """Rooms with several doors may have several waypoints; link them when visible.

        Circulation spaces are covered by the skeleton and are deliberately excluded: a
        visibility clique across a large hall is the opposite of a minimal graph.
        """
        by_space: dict[str, list[GraphNode]] = {}
        for node in self.nodes.values():
            if node.space_id and node.kind in ("room", "waypoint", "connector") and node.space_id in self.room_ids:
                by_space.setdefault(node.space_id, []).append(node)
        for nodes in by_space.values():
            for i, a in enumerate(nodes):
                for b in nodes[i + 1 :]:
                    if frozenset((a.id, b.id)) in self.edges:
                        continue
                    if self._visible((a.x, a.y), (b.x, b.y)):
                        self._add_edge(a.id, b.id, "corridor")

    # ---- primitives ------------------------------------------------------------------------
    def _add_node(self, x: float, y: float, kind: str, **attrs) -> str:
        node_id = f"n{next(self.ids)}"
        self.nodes[node_id] = GraphNode(id=node_id, x=float(x), y=float(y), kind=kind, **attrs)  # type: ignore[arg-type]
        return node_id

    def _add_edge(self, a: str, b: str, kind: str) -> None:
        if a == b:
            return
        key = frozenset((a, b))
        if key in self.edges:
            return
        na, nb = self.nodes[a], self.nodes[b]
        self.edges[key] = GraphEdge(from_id=a, to_id=b, kind=kind, length_px=float(np.hypot(na.x - nb.x, na.y - nb.y)))  # type: ignore[arg-type]

    def _remove_edge(self, a: str, b: str) -> None:
        # also drop any bend waypoints created for this polyline
        key = frozenset((a, b))
        if key in self.edges:
            del self.edges[key]
            return
        for node in list(self.nodes.values()):
            if node.kind == "waypoint" and node.label == f"bend:{a}:{b}":
                for k in [k for k in self.edges if node.id in k]:
                    del self.edges[k]
                del self.nodes[node.id]

    def _add_polyline(self, a: str, b: str, points, kind: str) -> None:
        """Add a wall-safe chain between two nodes: RDP simplified, refined where needed."""
        simplified = self._wall_safe(points)
        previous = a
        for bend in simplified[1:-1]:
            bend_id = self._add_node(bend[0], bend[1], "waypoint", label=f"bend:{a}:{b}")
            self._add_edge(previous, bend_id, kind)
            previous = bend_id
        self._add_edge(previous, b, kind)

    def _wall_safe(self, points, depth: int = 0) -> list[tuple[float, float]]:
        pts = [tuple(map(float, p)) for p in points]
        if len(pts) < 2:
            return pts
        epsilon = self.scale.px(TURN_TOLERANCE_M) / (2**depth)
        simplified = rdp(pts, epsilon)
        if depth >= 4:
            return simplified
        out = [simplified[0]]
        for u, v in zip(simplified, simplified[1:]):
            if self._visible(u, v):
                out.append(v)
                continue
            i, j = _index_of(pts, u), _index_of(pts, v)
            if j - i <= 1:
                out.append(v)
                continue
            refined = self._wall_safe(pts[i : j + 1], depth + 1)
            out.extend(refined[1:])
        return out

    def _visible(self, a, b) -> bool:
        line = LineString([a, b])
        return (self.allowed is None or self.allowed.covers(line)) and (self.barrier.is_empty or not self.prepared.intersects(line))

    # ---- final simplification --------------------------------------------------------------
    def _simplify(self) -> None:
        """Remove collinear degree-2 waypoints when the merged edge stays wall-free."""
        changed = True
        while changed:
            changed = False
            adjacency: dict[str, list[str]] = {}
            for key in self.edges:
                a, b = tuple(key)
                adjacency.setdefault(a, []).append(b)
                adjacency.setdefault(b, []).append(a)
            for node in list(self.nodes.values()):
                if node.kind != "waypoint" or len(adjacency.get(node.id, [])) != 2:
                    continue
                a, b = adjacency[node.id]
                if frozenset((a, b)) in self.edges:
                    continue  # contracting this triangle would erase an alternative route
                na, nb = self.nodes[a], self.nodes[b]
                va = np.array([na.x - node.x, na.y - node.y])
                vb = np.array([nb.x - node.x, nb.y - node.y])
                denom = np.linalg.norm(va) * np.linalg.norm(vb)
                cosine = float(va @ vb / denom) if denom else 1.0
                if cosine > COLLINEAR_COS:
                    continue
                if not self._visible((na.x, na.y), (nb.x, nb.y)):
                    continue
                kind = self.edges[frozenset((a, node.id))].kind
                other = self.edges[frozenset((b, node.id))].kind
                del self.edges[frozenset((a, node.id))]
                del self.edges[frozenset((b, node.id))]
                del self.nodes[node.id]
                self._add_edge(a, b, "doorway" if "doorway" in (kind, other) else "corridor")
                changed = True
                break
        for node in self.nodes.values():
            if node.label and node.label.startswith("bend:"):
                node.label = None
        # drop isolated non-semantic nodes
        connected = {n for key in self.edges for n in key}
        for node_id in [n.id for n in self.nodes.values() if n.id not in connected and n.kind in ("waypoint", "decision")]:
            del self.nodes[node_id]


def _length(path) -> float:
    pts = np.asarray(path, dtype=float)
    return float(np.sum(np.hypot(np.diff(pts[:, 0]), np.diff(pts[:, 1])))) if len(pts) > 1 else 0.0


def _index_of(points, target) -> int:
    for index, p in enumerate(points):
        if p == target:
            return index
    return 0
