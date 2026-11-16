import numpy as np
from shapely.geometry import LineString, Polygon

from mapper.contracts import Opening, Scale, Scene
from mapper.inference.spaces import SpaceInference
from mapper.pipeline import scene_to_payload


def test_unassigned_door_remains_visible_but_never_enters_navigation_graph():
    opening = Opening(
        id="candidate-1", polygon=Polygon([(45, 48), (55, 48), (55, 52), (45, 52)]),
        center=(50, 50), axis=LineString([(45, 50), (55, 50)]), normal=(0, 1),
        width_px=10, confidence=0.85, sources=("ml",),
    )
    inference = SpaceInference(Scale(20, "calibrated"), (100, 100))
    labels = np.zeros((100, 100), dtype=np.int32)
    exterior = np.zeros((100, 100), dtype=bool)

    assert inference._assign_openings([opening], [], labels, exterior, []) == []
    assert inference.opening_candidates == [opening]
    payload = scene_to_payload(Scene(100, 100, Scale(20, "calibrated"),
                                     opening_candidates=inference.opening_candidates))
    assert payload["openings"][0]["id"] == "candidate-1"
    assert payload["openings"][0]["review_required"] is True
    assert payload["openings"][0]["review_reason"]
    assert payload["graph"]["edges"] == []
