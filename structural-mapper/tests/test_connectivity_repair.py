import networkx as nx
from shapely.geometry import LineString, Polygon, box
from shapely.prepared import prep

from mapper.contracts import GraphNode, Opening, Scale, WallElement
from mapper.graph.build import GraphBuilder
from mapper.pipeline import door_scale


def _door(width_px: float, kind: str = "door") -> Opening:
    return Opening(id=f"o{width_px}", polygon=box(0, 0, width_px, 4), center=(width_px / 2, 2),
                   axis=LineString([(0, 2), (width_px, 2)]), normal=(0.0, 1.0), width_px=width_px, kind=kind)


def test_doors_revise_a_wall_thickness_guess_that_makes_them_too_narrow():
    revised = door_scale([_door(25), _door(27), _door(30)], Scale(52.5, "estimated"))
    assert revised is not None
    assert revised.source == "estimated"
    assert revised.pixels_per_meter == 27 / 0.9


def test_door_scale_never_overrides_calibration_or_decides_from_too_few_doors():
    doors = [_door(25), _door(27), _door(30)]
    assert door_scale(doors, Scale(52.5, "calibrated")) is None
    assert door_scale(doors, Scale(52.5, "vector")) is None
    assert door_scale(doors[:2], Scale(52.5, "estimated")) is None
    assert door_scale([_door(25, "passage")] * 5, Scale(52.5, "estimated")) is None


def test_door_scale_keeps_an_estimate_that_already_agrees_with_the_doors():
    assert door_scale([_door(27), _door(28), _door(29)], Scale(30.0, "estimated")) is None


def _builder(walls: list[WallElement]) -> GraphBuilder:
    builder = GraphBuilder(Scale(10, "calibrated"), (60, 200), walls)
    builder.nodes = {
        "a": GraphNode("a", 20, 30, "room", space_id="left"),
        "b": GraphNode("b", 180, 30, "room", space_id="right"),
        "w": GraphNode("w", 100, 5, "waypoint"),
    }
    return builder


def _components(builder: GraphBuilder) -> list[set[str]]:
    graph = nx.Graph()
    graph.add_nodes_from(builder.nodes)
    graph.add_edges_from(tuple(key) for key in builder.edges)
    return [set(c) for c in nx.connected_components(graph)]


def test_fragments_joined_by_free_space_are_bridged_without_crossing_walls():
    free = [box(5, 10, 195, 50)]
    builder = _builder([])

    assert builder._bridge_components(free) == 1

    components = _components(builder)
    assert len(components) == 1
    assert {"a", "b"} <= components[0]
    assert "w" not in builder.nodes  # stray skeleton piece without wayfinding meaning


def test_a_wall_without_an_opening_keeps_fragments_apart_for_review():
    wall = WallElement(box(95, 0, 105, 60))
    builder = _builder([wall])
    builder.allowed = None

    assert builder._bridge_components([box(5, 10, 195, 50).difference(wall.polygon)]) == 0

    assert sorted(len(c) for c in _components(builder)) == [1, 1]


def test_visible_bend_is_removed_but_a_wall_blocking_the_shortcut_keeps_it():
    for walls, expected in ([], False), ([WallElement(box(40, 35, 60, 55))], True):
        builder = GraphBuilder(Scale(10, "calibrated"), (100, 100), walls)
        builder.allowed = prep(box(0, 0, 100, 100))
        builder.nodes = {
            "a": GraphNode("a", 20, 20, "room"),
            "bend": GraphNode("bend", 20, 70, "waypoint"),
            "b": GraphNode("b", 80, 70, "room"),
        }
        builder._add_edge("a", "bend", "corridor")
        builder._add_edge("bend", "b", "corridor")
        builder._simplify()
        assert ("bend" in builder.nodes) is expected
