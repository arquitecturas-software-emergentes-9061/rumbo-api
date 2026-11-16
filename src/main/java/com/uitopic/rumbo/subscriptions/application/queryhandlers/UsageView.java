package com.uitopic.rumbo.subscriptions.application.queryhandlers;

public record UsageView(ResourceUsageView sites, ResourceUsageView floors, ResourceUsageView beacons) {
  public record ResourceUsageView(long used, Integer limit) {}
}
