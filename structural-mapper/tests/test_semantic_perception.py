import numpy as np
from shapely.geometry import LineString, Point, box
from shapely.ops import unary_union

from mapper.contracts import Opening, Scale, StructuralMasks, WallElement
from mapper.perception.cubicasa import _supported_region
from mapper.perception.fusion import fuse
from mapper.reconstruction.openings import detect_openings
from mapper.reconstruction.polygons import reconstruct_walls
from mapper.reconstruction.semantic import cut_semantic_openings
from mapper.graph.build import GraphBuilder


def test_joint_parser_keeps_door_evidence_inside_wall_and_ignores_furniture():
    wall = np.zeros((100, 160), bool)
    wall[45:55, :] = True
    door = np.zeros_like(wall)
    door[48:52, 60:100] = True
    furniture = np.zeros_like(wall)
    furniture[10:30, 20:40] = True
    primary = StructuralMasks(wall, door, np.zeros_like(wall), semantic_geometry=True)
    classical = StructuralMasks(furniture, np.zeros_like(wall), np.zeros_like(wall))
    masks = fuse(primary, classical, None, Scale(40, 'calibrated'))
    assert np.array_equal(masks.wall, wall)
    assert np.array_equal(masks.door, door)


def test_thin_semantic_door_cuts_host_depth_without_widening_jambs():
    shape = (120, 200)
    wall = np.zeros(shape, bool)
    wall[50:66, 10:190] = True
    door = np.zeros(shape, bool)
    door[56:59, 75:116] = True  # ink area is far smaller than a doorway footprint
    scale = Scale(50, 'calibrated')
    masks = StructuralMasks(wall, door, np.zeros(shape, bool), semantic_geometry=True)
    walls = reconstruct_walls(masks, scale)
    openings = detect_openings(masks, walls, scale, shape)
    assert len(openings) == 1
    opening = openings[0]
    width = opening.width_px
    cut = cut_semantic_openings(walls, openings, scale, shape)
    material = unary_union([w.polygon for w in cut])
    assert not material.intersects(LineString([(95, 40), (95, 75)]))
    assert material.contains(Point(70, 58))
    assert material.contains(Point(120, 58))
    assert opening.width_px == width
    assert opening.polygon.bounds[1] < 50 and opening.polygon.bounds[3] > 65


def test_hysteresis_extends_only_icons_connected_to_a_predicted_seed():
    probability = np.zeros((20, 30), np.float32)
    probability[5:9, 2:12] = 0.3
    probability[5:9, 20:26] = 0.3
    seed = np.zeros_like(probability, bool)
    seed[6, 6] = True
    result = _supported_region(probability, seed)
    assert result[5:9, 2:12].all()
    assert not result[:, 20:].any()


def test_semantic_window_remains_a_blocked_barrier():
    wall = np.zeros((100, 160), bool)
    wall[45:55, :] = True
    window = np.zeros_like(wall)
    window[45:55, 60:100] = True
    masks = StructuralMasks(wall, np.zeros_like(wall), window, semantic_geometry=True)
    walls = reconstruct_walls(masks, Scale(40, 'calibrated'))
    assert any(w.kind == 'window' for w in walls)
    assert unary_union([w.polygon for w in walls]).contains(Point(80, 50))


def test_visible_skeleton_attachment_is_not_shifted_to_a_different_endpoint():
    import networkx as nx
    builder = GraphBuilder(Scale(50, 'calibrated'), (100, 100), [])
    graph = nx.Graph()
    graph.add_node(1, x=10., y=50.)
    graph.add_node(2, x=90., y=50.)
    graph.add_edge(1, 2, path=[(x, 50.) for x in range(10, 91)], length=80.)
    builder.skeleton = graph
    builder.skeleton_node_ids = {n: builder._add_node(d['x'], d['y'], 'waypoint') for n, d in graph.nodes(data=True)}
    result = builder._split_skeleton_edge((1, 2, 10), (20., 50.), 35.)
    assert builder.nodes[result].x == 20.
