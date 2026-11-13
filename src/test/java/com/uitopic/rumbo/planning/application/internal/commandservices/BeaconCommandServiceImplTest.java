package com.uitopic.rumbo.planning.application.internal.commandservices;

import com.uitopic.rumbo.planning.application.internal.exceptions.InvalidBeaconAnchorException;
import com.uitopic.rumbo.planning.domain.model.commands.SaveBeaconCommand;
import com.uitopic.rumbo.planning.domain.model.entities.Beacon;
import com.uitopic.rumbo.planning.domain.repositories.BeaconRepository;
import com.uitopic.rumbo.planning.domain.repositories.PlanSpatialReferenceRepository;
import com.uitopic.rumbo.planning.domain.repositories.SiteRepository;
import com.uitopic.rumbo.subscriptions.interfaces.acl.QuotaResource;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BeaconCommandServiceImplTest {
  private final UUID owner = UUID.randomUUID();
  private final UUID site = UUID.randomUUID();
  private final UUID floor = UUID.randomUUID();
  private final SiteRepository sites = mock(SiteRepository.class);
  private final PlanSpatialReferenceRepository plans = mock(PlanSpatialReferenceRepository.class);
  private final BeaconRepository beacons = mock(BeaconRepository.class);
  private final PlanningQuotaGuard quotas = mock(PlanningQuotaGuard.class);
  private final BeaconCommandServiceImpl service = new BeaconCommandServiceImpl(sites, plans, beacons, quotas);

  @Test void checksQuotaAfterValidationAndBeforeInsertingPlacedBeacon() {
    when(sites.isOwnedBy(site, owner)).thenReturn(true);
    when(plans.containsPoint(site, floor, 2.0, 3.0)).thenReturn(true);
    when(beacons.create(any(Beacon.class))).thenAnswer(call -> call.getArgument(0));

    Beacon saved = service.register(owner, site, command(2.0, 3.0));

    var order = inOrder(sites, plans, quotas, beacons);
    order.verify(sites).isOwnedBy(site, owner);
    order.verify(plans).containsPoint(site, floor, 2.0, 3.0);
    order.verify(quotas).check(owner, QuotaResource.BEACON);
    order.verify(beacons).create(saved);
    assertThat(saved.placed()).isTrue();
  }

  @Test void checksQuotaForNewUnplacedBeaconToo() {
    when(sites.isOwnedBy(site, owner)).thenReturn(true);
    when(beacons.create(any(Beacon.class))).thenAnswer(call -> call.getArgument(0));

    Beacon saved = service.register(owner, site, command(null, null));

    var order = inOrder(quotas, beacons);
    order.verify(quotas).check(owner, QuotaResource.BEACON);
    order.verify(beacons).create(saved);
    assertThat(saved.placed()).isFalse();
    assertThat(saved.active()).isFalse();
    verifyNoInteractions(plans);
  }

  @Test void rejectsInvalidAnchorBeforeCheckingQuotaOrInserting() {
    when(sites.isOwnedBy(site, owner)).thenReturn(true);

    assertThatThrownBy(() -> service.register(owner, site, command(2.0, 3.0)))
        .isInstanceOf(InvalidBeaconAnchorException.class);

    verifyNoInteractions(quotas, beacons);
  }

  @Test void editingOrPlacingExistingBeaconDoesNotCheckQuota() {
    UUID id = UUID.randomUUID();
    Beacon current = new Beacon(id, site, floor, "beacon-test", null, null, false,
        null, null, null, null, null);
    when(sites.isOwnedBy(site, owner)).thenReturn(true);
    when(beacons.findBySiteAndId(site, id)).thenReturn(Optional.of(current));
    when(beacons.update(any(Beacon.class))).thenReturn(true);
    when(plans.containsPoint(site, floor, 2.0, 3.0)).thenReturn(true);

    Beacon edited = service.update(owner, site, id, command(null, null));
    Beacon placed = service.update(owner, site, id, command(2.0, 3.0));

    verify(beacons).update(edited);
    verify(beacons).update(placed);
    assertThat(placed.placed()).isTrue();
    verifyNoInteractions(quotas);
  }

  @Test void changingExistingBeaconStatusDoesNotCheckQuota() {
    UUID id = UUID.randomUUID();
    Beacon current = new Beacon(id, site, floor, "beacon-test", 2.0, 3.0, true);
    when(sites.isOwnedBy(site, owner)).thenReturn(true);
    when(beacons.findBySiteAndId(site, id)).thenReturn(Optional.of(current));
    when(beacons.updateActive(site, id, false)).thenReturn(true);

    service.setActive(owner, site, id, false);

    verify(beacons).updateActive(site, id, false);
    verifyNoInteractions(quotas);
  }

  private SaveBeaconCommand command(Double x, Double y) {
    return new SaveBeaconCommand(floor, "beacon-test", "Test beacon", null, null, null, -59, x, y);
  }
}
