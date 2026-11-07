import networkx as nx
import numpy as np
import pytest
from shapely.geometry import LineString, Point, Polygon, box
from shapely.ops import unary_union

from mapper.contracts import GraphEdge, GraphNode, NavigationGraph, Opening, Scale, Scene, Space, WallElement
from mapper.contracts import RasterInput
from mapper.ingest.decode import decode
from mapper.geometry import rasterize
from mapper.graph.build import GraphBuilder
from mapper.graph.skeleton import collapse_degree_two, skeleton_graph
from mapper.pipeline import MapperOptions, Pipeline, scene_to_payload
from mapper.validation.rules import validate
from mapper.walkable.free_space import walkable_by_space, walkable_union


def test_routes_in_an_l_shaped_hall_do_not_cut_across_the_unmapped_exterior():
    scale = Scale(20, "calibrated")
    hall = Space("hall", Polygon([(10, 10), (190, 10), (190, 60), (60, 60), (60, 190), (10, 190)]), kind="circulation")
    walk = walkable_by_space([hall], scale)
    area = walkable_union(walk, [], scale)
    builder = GraphBuilder(scale, (210, 210), [])
    graph = builder.build([hall], [], walk)
    assert graph.edges
    assert not builder._visible((150, 35), (35, 150))
    allowed = unary_union(area).buffer(0.75)
    nodes = {node.id: node for node in graph.nodes}
    assert all(allowed.covers(LineString([(nodes[e.from_id].x, nodes[e.from_id].y),
                                         (nodes[e.to_id].x, nodes[e.to_id].y)])) for e in graph.edges)


def test_independent_validator_rejects_a_route_over_a_courtyard_without_a_detected_wall():
    ring = Polygon([(0, 0), (200, 0), (200, 200), (0, 200)],
                   [[(60, 60), (140, 60), (140, 140), (60, 140)]])
    scene = Scene(200, 200, Scale(20, "calibrated"), walkable=[ring], graph=NavigationGraph(
        nodes=[GraphNode("a", 30, 100, "waypoint"), GraphNode("b", 170, 100, "waypoint")],
        edges=[GraphEdge("a", "b")]))
    report = validate(scene)
    assert report.status == "errors"
    assert report.metrics["edges_crossing_walls"] == 0
    assert report.metrics["edges_outside_walkable"] == 1


def test_clearance_does_not_silently_disappear_in_a_narrow_room():
    narrow = Space("narrow", box(0, 0, 8, 100))  # 0.4 m wide, below a 0.5 m diameter
    assert walkable_by_space([narrow], Scale(20, "calibrated")) == {}


def test_portals_reconnect_inset_spaces_but_do_not_widen_narrow_doorways():
    scale = Scale(20, "calibrated")
    spaces = [Space("a", box(0, 0, 100, 100)), Space("b", box(110, 0, 210, 100))]
    opening = Opening("door", box(100, 35, 110, 65), (105, 50), LineString([(105, 35), (105, 65)]),
                      (1, 0), 30, space_ids=("a", "b"))
    walk = walkable_by_space(spaces, scale)
    region = unary_union(walkable_union(walk, [opening], scale))
    assert region.covers(LineString([(90, 50), (120, 50)]))
    assert not region.covers(Point(105, 36))
    opening.width_px = 8
    assert not unary_union(walkable_union(walk, [opening], scale)).covers(Point(105, 50))


def test_late_detected_windows_are_excluded_from_the_walkable_area():
    space = Space("room", box(0, 0, 100, 100))
    glass = WallElement(box(40, 0, 60, 100), kind="window")
    walk = walkable_by_space([space], Scale(20, "calibrated"), [glass])
    assert not walk["room"].intersects(glass.polygon)


def test_raster_union_keeps_an_obstacle_inside_another_polygons_hole_in_either_order():
    enclosure = Polygon([(5, 5), (95, 5), (95, 95), (5, 95)],
                        [[(20, 20), (80, 20), (80, 80), (20, 80)]])
    obstacle = box(40, 40, 60, 60)
    first = rasterize([enclosure, obstacle], (100, 100))
    second = rasterize([obstacle, enclosure], (100, 100))
    assert np.array_equal(first, second)
    assert first[50, 50] and not first[30, 30]


@pytest.mark.parametrize("with_separate_branch", [False, True])
def test_closed_corridors_survive_skeleton_extraction_and_degree_two_collapse(with_separate_branch):
    mask = np.zeros((230, 300), dtype=bool)
    mask[20:180, 20:180] = True
    mask[55:145, 55:145] = False
    if with_separate_branch:
        mask[20:180, 240:260] = True
    graph = skeleton_graph(mask)
    collapse_degree_two(graph)
    assert len(nx.cycle_basis(graph)) == 1
    assert nx.number_connected_components(graph) == (2 if with_separate_branch else 1)


def test_a_ring_hall_remains_a_cycle_in_the_semantic_graph():
    hall = Space("hall", Polygon([(10, 10), (190, 10), (190, 190), (10, 190)],
                                 [[(65, 65), (135, 65), (135, 135), (65, 135)]]), kind="circulation")
    scale = Scale(20, "calibrated")
    graph = GraphBuilder(scale, (210, 210), []).build([hall], [], walkable_by_space([hall], scale))
    network = nx.Graph((edge.from_id, edge.to_id) for edge in graph.edges)
    assert len(nx.cycle_basis(network)) == 1


def test_svg_calibration_uses_the_decoded_size_instead_of_guessing_wall_thickness():
    svg = b'<svg xmlns="http://www.w3.org/2000/svg" width="200" height="100"><rect x="0" y="0" width="200" height="100" fill="white"/></svg>'
    scene = Pipeline().run_bytes(svg, "image/svg+xml", MapperOptions(
        use_ml=False, raster_scale=2, meters_per_unit=0.05, original_width_units=200))
    assert scene.scale.source == "calibrated"
    assert scene.scale.pixels_per_meter == pytest.approx(scene.width / 10)


def test_payload_preserves_diagonal_door_axes_and_polygon_holes():
    polygon = Polygon([(0, 0), (100, 0), (100, 100), (0, 100)],
                      [[(40, 40), (60, 40), (60, 60), (40, 60)]])
    opening = Opening("diagonal", box(10, 10, 20, 20), (15, 15), LineString([(10, 10), (20, 20)]),
                      (-0.707, 0.707), 14.142)
    payload = scene_to_payload(Scene(100, 100, Scale(20, "calibrated"), spaces=[Space("a", polygon)], openings=[opening]))
    assert payload["openings"][0]["axis"] == [[10, 10], [20, 20]]
    assert payload["spaces"][0]["polygon"]["holes"]
    assert polygon.covers(Point(payload["spaces"][0]["label_position"]))


def test_raster_clinic_remains_connected_when_door_attachments_split_paths_around_a_column(synthetic_case):
    _, svg, ppm = synthetic_case
    svg = svg.replace(b'</svg>', b'<rect x="490" y="297" width="12" height="22" fill="#111"/></svg>')
    image = decode(svg, "image/svg+xml", raster_scale=2).image
    scene = Pipeline().run(RasterInput(image), MapperOptions(pixels_per_meter=ppm * 2, use_ml=False))
    network = nx.Graph()
    network.add_nodes_from(node.id for node in scene.graph.nodes)
    network.add_edges_from((edge.from_id, edge.to_id) for edge in scene.graph.edges)
    assert nx.is_connected(network)
    assert any(wall.kind == "obstacle" for wall in scene.walls)
    assert scene.validation.metrics["edges_crossing_walls"] == 0
    assert scene.validation.metrics["edges_outside_walkable"] == 0
    assert scene.validation.metrics["rooms_unreachable_from_entrance"] == 0
