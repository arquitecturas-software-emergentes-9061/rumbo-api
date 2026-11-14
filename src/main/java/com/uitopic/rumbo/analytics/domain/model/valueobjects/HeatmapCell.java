package com.uitopic.rumbo.analytics.domain.model.valueobjects;

import java.util.UUID;

public record HeatmapCell(UUID floorId, String nodeReference, long deviations, long recalculations) {}
