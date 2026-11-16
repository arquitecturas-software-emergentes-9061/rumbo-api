package com.uitopic.rumbo.analytics.domain.model.valueobjects;

import java.util.List;

public record NavigationHeatmap(List<HeatmapCell> cells, long discardedEvents) {
  public static final NavigationHeatmap EMPTY = new NavigationHeatmap(List.of(), 0);

  public NavigationHeatmap {
    cells = List.copyOf(cells);
  }
}
