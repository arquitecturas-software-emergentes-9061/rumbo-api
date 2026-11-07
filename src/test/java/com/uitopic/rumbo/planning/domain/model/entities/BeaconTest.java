package com.uitopic.rumbo.planning.domain.model.entities;

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

  private static Beacon full(Double x, Double y, boolean active, Integer major, Integer tx) {
    UUID uuid = major == null ? null : UUID.fromString("f7826da6-4fa2-4e98-8024-bc5b71e0893e");
    return new Beacon(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "b05_ident",
        x, y, active, "  B-05  ", uuid, major, major == null ? null : 5, tx);
  }

  @Test void keepsNameAndIbeaconMetadata() {
    Beacon b = full(4.0, 2.0, true, 100, -59);
    assertEquals("B-05", b.name());
    assertEquals(100, b.major());
    assertEquals(-59, b.txPowerDbm());
    assertTrue(b.placed());
  }

  @Test void allowsUnplacedInactiveBeaconButRejectsActiveWithoutPosition() {
    assertFalse(full(null, null, false, 100, -59).placed());
    assertThrows(IllegalArgumentException.class, () -> full(null, null, true, 100, -59));
    assertThrows(IllegalArgumentException.class, () -> full(3.0, null, false, 100, -59));
  }

  @Test void rejectsIncompleteOrOutOfRangeIbeaconData() {
    UUID u = UUID.randomUUID();
    UUID id = UUID.randomUUID(), site = UUID.randomUUID(), floor = UUID.randomUUID();
    assertThrows(IllegalArgumentException.class,
        () -> new Beacon(id, site, floor, "abcd", 1.0, 1.0, true, null, u, 1, null, null));
    assertThrows(IllegalArgumentException.class, () -> full(1.0, 1.0, true, 70000, -59));
    assertThrows(IllegalArgumentException.class, () -> full(1.0, 1.0, true, 100, 5));
  }

  @Test void derivesIdentifierFromIbeaconTriplet() {
    UUID u = UUID.fromString("f7826da6-4fa2-4e98-8024-bc5b71e0893e");
    assertEquals("f7826da6-4fa2-4e98-8024-bc5b71e0893e:100:5", Beacon.deriveIdentifier(u, 100, 5));
    assertNull(Beacon.deriveIdentifier(null, 100, 5));
  }
}
