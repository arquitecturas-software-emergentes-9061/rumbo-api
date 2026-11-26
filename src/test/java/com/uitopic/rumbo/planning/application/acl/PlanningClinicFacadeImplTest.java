package com.uitopic.rumbo.planning.application.acl;

import com.uitopic.rumbo.planning.domain.services.ClinicStructureQueryService;
import com.uitopic.rumbo.planning.domain.model.aggregates.Clinic;
import com.uitopic.rumbo.planning.interfaces.acl.PlanningClinicFacade.ClinicSummary;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlanningClinicFacadeImplTest {
  ClinicStructureQueryService queries = mock(ClinicStructureQueryService.class);
  PlanningClinicFacadeImpl facade = new PlanningClinicFacadeImpl(queries);
  @Test void returnsOwnedClinicSummary() {
    UUID owner = UUID.randomUUID();
    Clinic clinic = new Clinic(UUID.randomUUID(), "Clinic", "CODE", "contact@example.test", null);
    when(queries.findClinic(owner)).thenReturn(Optional.of(clinic));
    assertThat(facade.findClinicOwnedBy(owner)).contains(new ClinicSummary(clinic.id(), clinic.name()));
    verify(queries).findClinic(owner);
  }
  @Test void returnsEmptyWhenOwnerHasNoClinic() {
    UUID owner = UUID.randomUUID();
    when(queries.findClinic(owner)).thenReturn(Optional.empty());
    assertThat(facade.findClinicOwnedBy(owner)).isEmpty();
    verify(queries).findClinic(owner);
  }
}
