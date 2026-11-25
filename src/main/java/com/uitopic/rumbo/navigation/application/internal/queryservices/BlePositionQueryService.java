package com.uitopic.rumbo.navigation.application.internal.queryservices;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uitopic.rumbo.navigation.domain.model.valueobjects.PublishedSpatialGraph;
import com.uitopic.rumbo.navigation.domain.services.PublishedSpatialGraphQueryService;
import java.io.IOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

/** Matches measured fingerprints tied to a specific published map and surveyed nodes. */
@Service
@Profile("postgres")
public class BlePositionQueryService {
  private final PublishedSpatialGraphQueryService graphs;
  private final List<Survey> surveys;

  public BlePositionQueryService(PublishedSpatialGraphQueryService graphs, ObjectMapper mapper,
      ResourceLoader resources, @Value("${rumbo.navigation.ble-survey:}") String surveyLocation) throws IOException {
    this.graphs = graphs;
    if (surveyLocation.isBlank()) {
      this.surveys = List.of();
    } else {
      try (var input = resources.getResource(surveyLocation).getInputStream()) {
        this.surveys = List.copyOf(mapper.readValue(input, new TypeReference<List<Survey>>() {}));
      }
      for (var survey : surveys) validate(survey);
    }
  }

  public Set<Identity> beacons(UUID siteId) {
    return graphs.findCurrentBySite(siteId)
        .map(graph -> points(graph).stream().flatMap(point -> point.signals().stream())
            .map(Baseline::identity).collect(Collectors.toUnmodifiableSet()))
        .orElse(Set.of());
  }

  public Fix resolve(UUID siteId, List<Observation> observations, Instant now) {
    if (observations == null || observations.size() > 200) return null;
    var graph = graphs.findCurrentBySite(siteId).orElse(null);
    if (graph == null) return null;
    Map<Identity, Observation> live = new HashMap<>();
    var knownPoints = points(graph);
    var identities = knownPoints.stream().flatMap(point -> point.signals().stream())
        .map(Baseline::identity).collect(Collectors.toSet());
    for (var observation : observations) {
      if (observation == null || !identities.contains(observation.identity())
          || observation.timestampUtc() == null || !validRssi(observation.rssiDbm())
          || observation.timestampUtc().isBefore(now.minusSeconds(12))
          || observation.timestampUtc().isAfter(now.plusSeconds(2))) continue;
      live.merge(observation.identity(), observation,
          (a, b) -> a.timestampUtc().isAfter(b.timestampUtc()) ? a : b);
    }
    if (live.size() < 2) return null;
    var newest = live.values().stream().map(Observation::timestampUtc).max(Comparator.naturalOrder()).orElseThrow();
    live.values().removeIf(observation -> observation.timestampUtc().isBefore(newest.minusSeconds(2)));
    var ranked = knownPoints.stream().map(point -> score(point, live)).filter(s -> s != null)
        .sorted(Comparator.comparingDouble(Match::error)).toList();
    if (ranked.size() < 2 || ranked.getFirst().error() > 6
        || ranked.get(1).error() - ranked.getFirst().error() < 3) return null;
    var best = ranked.getFirst();
    var node = graph.nodes().stream().filter(n -> n.id().equals(best.point().nodeId())).findFirst().orElseThrow();
    var plan = graph.plans().stream().filter(p -> p.floorId().equals(node.floorId()) &&
        p.planVersionId().equals(node.planVersionId())).findFirst().orElse(null);
    if (plan == null || !Double.isFinite(plan.metersPerUnit()) || plan.metersPerUnit() <= 0) return null;
    return new Fix(graph.siteId(), graph.modelVersionId(), node.floorId(), node.planVersionId(), node.id(),
        node.x(), node.y(), plan.metersPerUnit(), best.observedAt(), true);
  }

  private List<Point> points(PublishedSpatialGraph graph) {
    var nodes = graph.nodes().stream().map(PublishedSpatialGraph.Node::id).collect(Collectors.toSet());
    return surveys.stream().filter(s -> s.siteId().equals(graph.siteId()) &&
        s.modelVersionId().equals(graph.modelVersionId())).findFirst().stream()
        .flatMap(s -> s.points().stream()).filter(p -> nodes.contains(p.nodeId())).toList();
  }

  private static Match score(Point point, Map<Identity, Observation> live) {
    double error = 0;
    int shared = 0;
    Instant time = Instant.MIN;
    for (var baseline : point.signals()) {
      var observed = live.get(baseline.identity());
      if (observed == null) {
        error += 15;
      } else {
        shared++;
        error += Math.abs(baseline.rssiDbm() - observed.rssiDbm());
        if (observed.timestampUtc().isAfter(time)) time = observed.timestampUtc();
      }
    }
    if (shared < 2 || shared * 4 < point.signals().size() * 3) return null;
    // Penalize unexpected beacons as well as missing baseline readings.
    int unexpected = live.size() - shared;
    return new Match(point, (error + 15 * unexpected) / (point.signals().size() + unexpected), time);
  }

  private static void validate(Survey survey) {
    if (survey.siteId() == null || survey.modelVersionId() == null || survey.points() == null ||
        survey.points().size() < 2 || survey.points().stream().map(Point::nodeId).distinct().count() != survey.points().size()) {
      throw new IllegalArgumentException("BLE survey requires distinct published nodes");
    }
    for (var point : survey.points()) {
      if (point.nodeId() == null || point.signals() == null || point.signals().size() < 2 ||
          point.signals().stream().anyMatch(s -> s.identity().uuid() == null || s.major() < 0 ||
              s.major() > 65535 || s.minor() < 0 || s.minor() > 65535 || !validRssi(s.rssiDbm())) ||
          point.signals().stream().map(Baseline::identity).distinct().count() != point.signals().size()) {
        throw new IllegalArgumentException("BLE survey contains invalid or duplicate baselines");
      }
    }
  }

  private static boolean validRssi(double value) { return Double.isFinite(value) && value >= -127 && value < 0; }
  public record Identity(UUID uuid, int major, int minor) {}
  public record Baseline(UUID uuid, int major, int minor, double rssiDbm) {
    Identity identity() { return new Identity(uuid, major, minor); }
  }
  public record Observation(UUID uuid, int major, int minor, double rssiDbm, Instant timestampUtc) {
    Identity identity() { return new Identity(uuid, major, minor); }
  }
  public record Point(UUID nodeId, List<Baseline> signals) {}
  public record Survey(UUID siteId, UUID modelVersionId, List<Point> points) {}
  private record Match(Point point, double error, Instant observedAt) {}
  public record Fix(UUID siteId, UUID modelVersionId, UUID floorId, UUID planVersionId, UUID nodeId,
                    double x, double y, double metersPerUnit, Instant observedAt, boolean reliable) {}
}
