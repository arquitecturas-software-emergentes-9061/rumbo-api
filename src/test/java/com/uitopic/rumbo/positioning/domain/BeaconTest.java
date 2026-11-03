package com.uitopic.rumbo.positioning.domain;

import org.junit.jupiter.api.Test;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BeaconTest {
  @Test void normalizesCaseAndSpaces() {
    Beacon b = new Beacon(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "  AA:BB:CC:DD  ", 2, 5, true);
    assertEquals("aa:bb:cc:dd", b.identifier());
  }

  @Test void rejectsNaNInfinityAndNegativeCoordinates() {
    UUID id = UUID.randomUUID(), site = UUID.randomUUID(), floor = UUID.randomUUID();
    assertThrows(IllegalArgumentException.class, () -> new Beacon(id, site, floor, "abc_123", Double.NaN, 0, true));
    assertThrows(IllegalArgumentException.class, () -> new Beacon(id, site, floor, "abc_123", 0, Double.POSITIVE_INFINITY, true));
    assertThrows(IllegalArgumentException.class, () -> new Beacon(id, site, floor, "abc_123", -1, 0, true));
  }

  @Test void rejectsInvalidIdentifiers() {
    assertThrows(IllegalArgumentException.class, () -> Beacon.normalizeIdentifier("../secret"));
    assertThrows(IllegalArgumentException.class, () -> Beacon.normalizeIdentifier("a"));
  }
}
