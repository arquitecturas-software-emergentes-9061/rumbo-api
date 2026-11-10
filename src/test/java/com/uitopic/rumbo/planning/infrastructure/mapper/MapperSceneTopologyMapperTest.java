package com.uitopic.rumbo.planning.infrastructure.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uitopic.rumbo.planning.application.internal.models.SvgPlanPreview.CandidateNode;
import com.uitopic.rumbo.planning.application.internal.models.SvgPlanPreview.CandidateLink;
import com.uitopic.rumbo.planning.application.internal.models.SvgPlanPreview.Door;
import com.uitopic.rumbo.planning.application.internal.models.SvgPlanPreview.Topology;
import com.uitopic.rumbo.planning.application.internal.models.SvgPlanPreview.Zone;
import com.uitopic.rumbo.planning.application.internal.models.SvgPlanPreview.PlanPoint;
import com.uitopic.rumbo.planning.domain.model.valueobjects.OriginalPlanBounds;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** The scene is a real output of the structural mapper over a clinic plan photographed as a JPEG (1284 x 1032 px). */
class MapperSceneTopologyMapperTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  /** The original is stored at half the pixels the mapper analyzed, so every coordinate must be halved. */
  private static final OriginalPlanBounds HALF = new OriginalPlanBounds(642, 516);

  private static JsonNode scene() throws IOException {
    try (InputStream in = MapperSceneTopologyMapperTest.class.getResourceAsStream("/mapper/clinic-scene.json")) {
      return JSON.readTree(in);
    }
  }

  private static JsonNode synthetic(String json) throws IOException {
    return JSON.readTree(json);
  }

  @Test
  void spacesBecomeBoundingBoxesInTheUnitsOfTheOriginal() throws IOException {
    JsonNode scene = scene();
    Topology topology = MapperSceneTopologyMapper.toTopology(scene, HALF, false);

    assertThat(topology.zones()).extracting(Zone::id, Zone::category, Zone::name)
      .containsExactly(org.assertj.core.groups.Tuple.tuple("s1", "CORRIDOR", "CORRIDOR 1"),
        org.assertj.core.groups.Tuple.tuple("s2", "ROOM", "ROOM 1"));
    double minX = Double.MAX_VALUE;
    for (JsonNode point : scene.path("spaces").get(1).path("polygon").path("outer")) {
      minX = Math.min(minX, point.get(0).asDouble());
    }
    Zone room = topology.zones().get(1);
    assertThat(room.x()).isCloseTo(minX * 0.5, within(1e-9));
    assertThat(room.width()).isPositive();
    assertThat(room.x() + room.width()).isLessThanOrEqualTo(HALF.width() + 1e-9);
  }

  @Test
  void theNodeOfARoomSharesTheIdOfItsZoneSoTheEditorCanTellDestinationsApart() throws IOException {
    Topology topology = MapperSceneTopologyMapper.toTopology(scene(), HALF, false);

    CandidateNode room = topology.nodes().stream().filter(node -> node.role().equals("ROOM")).findFirst().orElseThrow();
    assertThat(room.id()).isEqualTo("s2");
    assertThat(room.label()).isEqualTo("ROOM 1");
    assertThat(topology.nodes().stream().map(CandidateNode::id).collect(Collectors.toSet()))
      .hasSize(topology.nodes().size());
  }

  @Test
  void connectionsOnlyJoinKnownNodesAndTheirLengthIsScaled() throws IOException {
    JsonNode scene = scene();
    Topology topology = MapperSceneTopologyMapper.toTopology(scene, HALF, false);

    Set<String> ids = topology.nodes().stream().map(CandidateNode::id).collect(Collectors.toSet());
    assertThat(topology.connections()).isNotEmpty().allSatisfy(link -> {
      assertThat(ids).contains(link.fromNodeId(), link.toNodeId());
      assertThat(link.fromNodeId()).isNotEqualTo(link.toNodeId());
      assertThat(link.bidirectional()).isTrue();
    });
    assertThat(topology.connections().get(0).distanceUnits())
      .isCloseTo(scene.path("graph").path("edges").get(0).path("length_px").asDouble() * 0.5, within(1e-9));
  }

  @Test
  void openingsBecomeSegmentsAsWideAsTheOpening() throws IOException {
    JsonNode scene = scene();
    Topology topology = MapperSceneTopologyMapper.toTopology(scene, HALF, false);

    assertThat(topology.doors()).hasSize(scene.path("openings").size());
    Door first = topology.doors().get(0);
    double length = Math.hypot(first.x2() - first.x1(), first.y2() - first.y1());
    assertThat(length).isCloseTo(scene.path("openings").get(0).path("width_px").asDouble() * 0.5, within(1e-9));
  }

  @Test
  void wallsAreSegmentsInsideThePlan() throws IOException {
    Topology topology = MapperSceneTopologyMapper.toTopology(scene(), HALF, false);

    assertThat(topology.wallCandidates()).isNotEmpty().allSatisfy(segment -> {
      assertThat(segment.source()).isEqualTo("mapper");
      assertThat(segment.x1()).isBetween(-1.0, HALF.width() + 1);
      assertThat(segment.y2()).isBetween(-1.0, HALF.height() + 1);
    });
  }

  @Test
  void theProposalAlwaysSaysItIsNotPublishedAndWarnsAboutAnEstimatedScale() throws IOException {
    Topology estimated = MapperSceneTopologyMapper.toTopology(scene(), HALF, false);
    Topology calibrated = MapperSceneTopologyMapper.toTopology(scene(), HALF, true);

    assertThat(estimated.warnings()).anyMatch(text -> text.contains("escala se estimó"));
    assertThat(calibrated.warnings()).noneMatch(text -> text.contains("escala se estimó"));
    assertThat(calibrated.warnings()).last().asString().contains("no publicada");
  }

  @Test
  void verticalSpacesKeepTheirKindVoidsAreDroppedAndLabelsWin() throws IOException {
    JsonNode scene = synthetic("""
      {"image_size":{"width":100,"height":100},
       "spaces":[
        {"id":"a","kind":"vertical","connector_kind":"elevator","label":"Ascensor A","polygon":{"outer":[[0,0],[10,0],[10,10],[0,10]]}},
        {"id":"b","kind":"vertical","connector_kind":"stairs","label":null,"polygon":{"outer":[[20,0],[30,0],[30,10],[20,10]]}},
        {"id":"c","kind":"void","label":null,"polygon":{"outer":[[0,50],[10,50],[10,60],[0,60]]}},
        {"id":"d","kind":"room","label":"Cardiologia","polygon":{"outer":[[40,0],[60,0],[60,20],[40,20]]}}],
       "openings":[],"graph":{"nodes":[],"edges":[]},"walls":[],"validation":{"status":"ok","issues":[]}}""");

    Topology topology = MapperSceneTopologyMapper.toTopology(scene, new OriginalPlanBounds(100, 100), true);

    assertThat(topology.zones()).extracting(Zone::id, Zone::category, Zone::name).containsExactly(
      org.assertj.core.groups.Tuple.tuple("a", "ELEVATOR", "Ascensor A"),
      org.assertj.core.groups.Tuple.tuple("b", "STAIRS", "STAIRS 1"),
      org.assertj.core.groups.Tuple.tuple("d", "ROOM", "Cardiologia"));
  }

  @Test
  void anEmptySceneYieldsAWarningInsteadOfFailing() throws IOException {
    Topology topology = MapperSceneTopologyMapper.toTopology(synthetic("""
      {"image_size":{"width":50,"height":50},"spaces":[],"openings":[],"graph":{"nodes":[],"edges":[]},"walls":[],
       "validation":{"status":"warnings","issues":[{"code":"no-entrance","severity":"warning","message":"No entrance","subjects":[]},
       {"code":"disconnected-graph","severity":"warning","message":"Graph has 2 components","subjects":["n1","n2","n3"]},
       {"code":"something-new","severity":"warning","message":"Texto propio","subjects":[]},
       {"code":"x","severity":"info","message":"noise","subjects":[]}]}}"""), new OriginalPlanBounds(50, 50), true);

    assertThat(topology.zones()).isEmpty();
    assertThat(topology.warnings()).anyMatch(text -> text.contains("No se reconocieron ambientes"))
      .anyMatch(text -> text.startsWith("No se detectó ninguna entrada"))
      .anyMatch(text -> text.startsWith("3 nodos no se alcanzan"))
      .contains("Texto propio").doesNotContain("noise");
  }

  @Test
  void recognizedOpeningTurnsACrossingIntoDoorwayAndLowConfidenceLeavesItForReview() throws IOException {
    String scene = """
      {"image_size":{"width":100,"height":100},
       "spaces":[],"walls":[{"outer":[[50,0],[50,100]]}],
       "walkable":[{"polygon":{"outer":[[0,0],[100,0],[100,100],[0,100]],"holes":[]}}],
       "openings":[{"id":"door","axis":[[50,45],[50,55]],"confidence":%s}],
       "graph":{"nodes":[{"id":"a","x":20,"y":50},{"id":"b","x":80,"y":50}],
         "edges":[{"from":"a","to":"b","kind":"corridor","length_px":60}]},
       "validation":{"status":"ok","issues":[]}}
      """;
    Topology certain = MapperSceneTopologyMapper.toTopology(synthetic(scene.formatted("0.9")),
      new OriginalPlanBounds(100, 100), true);
    assertThat(certain.doors()).hasSize(1);
    assertThat(certain.wallCandidates()).hasSize(2);
    assertThat(certain.connections()).extracting(CandidateLink::kind).containsExactly("DOORWAY");

    Topology uncertain = MapperSceneTopologyMapper.toTopology(synthetic(scene.formatted("0.3")),
      new OriginalPlanBounds(100, 100), true);
    assertThat(uncertain.doors()).hasSize(1);
    assertThat(uncertain.wallCandidates()).hasSize(2);
    assertThat(uncertain.connections()).isEmpty();
    assertThat(uncertain.warnings()).anyMatch(text -> text.contains("detección incierta"))
      .anyMatch(text -> text.contains("fuera de una puerta"));
  }

  @Test
  void uncertainTraversableSpaceKeepsNodesButDoesNotInventAnEdge() throws IOException {
    Topology topology = MapperSceneTopologyMapper.toTopology(synthetic("""
      {"image_size":{"width":100,"height":100},"spaces":[],"walls":[],"openings":[],"walkable":[],
       "graph":{"nodes":[{"id":"a","x":20,"y":50},{"id":"b","x":80,"y":50}],
         "edges":[{"from":"a","to":"b","kind":"corridor","length_px":60}]},
       "validation":{"status":"warnings","issues":[]}}
      """), new OriginalPlanBounds(100, 100), true);
    assertThat(topology.nodes()).hasSize(2);
    assertThat(topology.connections()).isEmpty();
    assertThat(topology.warnings()).anyMatch(text -> text.contains("espacio transitable"));
  }

  @Test
  void anUnusableDoorCandidateIsReportedInsteadOfSilentlyDiscarded() throws IOException {
    Topology topology = MapperSceneTopologyMapper.toTopology(synthetic("""
      {"image_size":{"width":100,"height":100},"spaces":[],"walls":[],
       "openings":[{"id":"unclear","axis":[[25,25],[25,25]],"confidence":0.9}],
       "graph":{"nodes":[],"edges":[]},"validation":{"status":"warnings","issues":[]}}
      """), new OriginalPlanBounds(100, 100), true);
    assertThat(topology.doors()).isEmpty();
    assertThat(topology.warnings()).anyMatch(text -> text.contains("puerta detectada no tiene una geometría interpretable"));
  }

  @Test
  void aDetectedButUnassignedDoorStaysVisibleWithoutAuthorizingADoorway() throws IOException {
    Topology topology = MapperSceneTopologyMapper.toTopology(synthetic("""
      {"image_size":{"width":100,"height":100},"spaces":[],
       "walls":[{"outer":[[50,0],[50,100]]}],
       "walkable":[{"polygon":{"outer":[[0,0],[100,0],[100,100],[0,100]],"holes":[]}}],
       "openings":[{"id":"uncertain","axis":[[50,45],[50,55]],"confidence":0.95,
         "review_required":true,"review_reason":"no se identificaron ambos lados de la abertura"}],
       "graph":{"nodes":[{"id":"a","x":20,"y":50},{"id":"b","x":80,"y":50}],
         "edges":[{"from":"a","to":"b","kind":"doorway","length_px":60}]},
       "validation":{"status":"warnings","issues":[]}}
      """), new OriginalPlanBounds(100, 100), true);
    assertThat(topology.doors()).hasSize(1);
    assertThat(topology.connections()).isEmpty();
    assertThat(topology.warnings()).anyMatch(text -> text.contains("no se identificaron ambos lados"));
  }

  @Test
  void aConfirmedDoorInAWallGapSupportsDoorwayConnections() throws IOException {
    Topology topology = MapperSceneTopologyMapper.toTopology(synthetic("""
      {"image_size":{"width":100,"height":100},
       "spaces":[
         {"id":"left","kind":"room","polygon":{"outer":[[0,0],[50,0],[50,100],[0,100]]}},
         {"id":"right","kind":"room","polygon":{"outer":[[50,0],[100,0],[100,100],[50,100]]}}],
       "walls":[{"outer":[[50,0],[50,45]]},{"outer":[[50,55],[50,100]]}],
       "walkable":[{"polygon":{"outer":[[0,0],[100,0],[100,100],[0,100]],"holes":[]}}],
       "openings":[{"id":"door","kind":"door","axis":[[50,45],[50,55]],"confidence":0.9,
         "spaces":["left","right"]}],
       "graph":{"nodes":[{"id":"a","x":20,"y":50},{"id":"b","x":80,"y":50}],
         "edges":[{"from":"a","to":"b","kind":"corridor","length_px":60}]},
       "validation":{"status":"ok","issues":[]}}
      """), new OriginalPlanBounds(100, 100), true);
    assertThat(topology.doors()).hasSize(1);
    assertThat(topology.connections()).extracting(CandidateLink::kind).containsExactly("DOORWAY");
  }

  @Test
  void aSceneWithoutImageSizeIsRejected() {
    assertThatThrownBy(() -> MapperSceneTopologyMapper.toTopology(synthetic("{\"spaces\":[]}"),
      new OriginalPlanBounds(10, 10), true)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void preservesNonConvexSpacesHolesBarrierClassesAndDiagonalDoorsThroughJson() throws IOException {
    Topology topology = MapperSceneTopologyMapper.toTopology(synthetic("""
      {"image_size":{"width":100,"height":100},
       "spaces":[{"id":"a","kind":"room","label":"Triaje","label_position":[20,60],
         "polygon":{"outer":[[10,10],[90,10],[90,40],[40,40],[40,90],[10,90]],
                    "holes":[[[20,20],[30,20],[30,30],[20,30]]]}}],
       "openings":[{"id":"diagonal","axis":[[40,40],[50,50]],"width_px":14.142}],
       "walls":[{"outer":[[0,0],[10,0],[10,100],[0,100]],"holes":[]}],
       "obstacles":[{"outer":[[20,20],[30,20],[30,30],[20,30]],"holes":[]}],
       "windows":[{"outer":[[90,10],[95,10],[95,30],[90,30]],"holes":[]}],
       "walkable":[{"polygon":{"outer":[[10,10],[35,10],[35,80],[10,80]],"holes":[]}}],
       "graph":{"nodes":[],"edges":[]},"validation":{"issues":[]}}
      """), new OriginalPlanBounds(50, 50), true);

    Zone zone = topology.zones().getFirst();
    assertThat(zone.polygon().outer()).hasSize(6).contains(new PlanPoint(20, 20));
    assertThat(zone.polygon().holes()).hasSize(1);
    assertThat(zone.polygon().holes().getFirst()).contains(new PlanPoint(15, 15));
    assertThat(zone.labelPosition()).isEqualTo(new PlanPoint(10, 30));
    assertThat(topology.doors()).containsExactly(new Door("diagonal", 20, 20, 25, 25));
    assertThat(topology.barriers()).extracting(barrier -> barrier.kind()).containsExactly("WALL", "OBSTACLE", "WINDOW");
    assertThat(topology.walkable()).hasSize(1);
    JsonNode response = JSON.valueToTree(topology);
    assertThat(response.at("/zones/0/polygon/holes/0/0/x").asDouble()).isEqualTo(10);
    assertThat(response.at("/barriers/1/polygon/outer/0/x").asDouble()).isEqualTo(10);
    assertThat(response.at("/walkable/0/outer/0/y").asDouble()).isEqualTo(5);
  }

  @Test
  void hugeWallSetsAreCappedAndTheEditorIsTold() throws IOException {
    StringBuilder ring = new StringBuilder("[");
    for (int i = 0; i < MapperSceneTopologyMapper.MAX_WALL_SEGMENTS + 50; i++) {
      ring.append(i == 0 ? "" : ",").append("[").append(i).append(",").append(i % 2).append("]");
    }
    ring.append("]");
    Topology topology = MapperSceneTopologyMapper.toTopology(synthetic("""
      {"image_size":{"width":10000,"height":10},"spaces":[],"openings":[],"graph":{"nodes":[],"edges":[]},
       "walls":[{"outer":%s}],"validation":{"status":"ok","issues":[]}}""".formatted(ring)),
      new OriginalPlanBounds(10000, 10), true);

    assertThat(topology.wallCandidates()).hasSize(MapperSceneTopologyMapper.MAX_WALL_SEGMENTS);
    assertThat(topology.warnings()).anyMatch(text -> text.contains("más de " + MapperSceneTopologyMapper.MAX_WALL_SEGMENTS));
  }
}
