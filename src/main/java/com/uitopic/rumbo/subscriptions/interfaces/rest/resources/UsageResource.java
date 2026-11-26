package com.uitopic.rumbo.subscriptions.interfaces.rest.resources;

public record UsageResource(ResourceUsageResource sites, ResourceUsageResource floors, ResourceUsageResource beacons) {
  public record ResourceUsageResource(long used, Integer limit) {}
}
